package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.mapper.AutomaticClassificationMapper.*;
import com.zjcxph.imgapi.ocr.*;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AutomaticClassificationService {
    public record RollbackRequest(long afterAuditId,int limit) {}
    public record RollbackResult(int reverted,int skipped,long nextAfterAuditId) {}
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AutomaticClassificationService.class);
    private final AutomaticClassificationMapper mapper;private final ClassificationMapper classifications;private final OcrBatchMapper batches;
    private final OcrMapper ocr;private final OcrInput input;private final OcrAccess access;private final ImageTypeService types;
    private final OcrQualityGate quality;private final OcrLoadGuard guard;private final OcrJobService jobs;private final OcrBatchService batchService;
    private final TransactionTemplate transactions;private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ocr-auto-apply");t.setDaemon(true);return t;});
    public AutomaticClassificationService(AutomaticClassificationMapper mapper,ClassificationMapper classifications,OcrBatchMapper batches,
            OcrMapper ocr,OcrInput input,OcrAccess access,ImageTypeService types,OcrQualityGate quality,OcrLoadGuard guard,
            OcrJobService jobs,OcrBatchService batchService,TransactionTemplate transactions) {
        this.mapper=mapper;this.classifications=classifications;this.batches=batches;this.ocr=ocr;this.input=input;this.access=access;
        this.types=types;this.quality=quality;this.guard=guard;this.jobs=jobs;this.batchService=batchService;this.transactions=transactions;
    }
    @Scheduled(fixedDelay=3000) public void dispatch() {
        if(!jobs.enabled()||!quality.enabled()||!guard.allowed()||!running.compareAndSet(false,true)) return;
        try {executor.execute(()->{try{runOne();}catch(RuntimeException failure){LOG.warn("自动分类持久化暂不可用，等待重试");}finally{running.set(false);}});}
        catch(RejectedExecutionException ignored){running.set(false);}
    }
    public void runOne() {
        if(!jobs.enabled()||!quality.enabled()||!guard.allowed()) return;
        Long id=mapper.candidate();if(id==null) return;
        Target target=mapper.target(id);if(target==null) return;
        try {
            authorize(target.getOwnerUserId());
            try(var prepared=input.prepare(target.getScanId(),()->!jobs.enabled()||!quality.enabled())) {
                transactions.executeWithoutResult(tx->{
                    var batch=batches.lock(target.getBatchId());
                    if(Thread.currentThread().isInterrupted()||batch==null||!"RUNNING".equals(batch.getStatus())||!batch.isAutoApply()||!jobs.enabled()||!quality.enabled()||!guard.allowed()) return;
                    AuthSession actor=authorize(batch.getOwnerUserId());
                    var suggestion=classifications.lockSuggestion(id);
                    if(suggestion==null||!"PENDING".equals(suggestion.getReviewStatus())||!"PENDING".equals(suggestion.getAutoStatus())) return;
                    var scan=classifications.lockScan(suggestion.getScanId());var fact=ocr.lockFact(suggestion.getScanId());
                    var config=config(target);
                    if(!Objects.equals(quality.checksum(),batch.getPolicyChecksum())
                            ||!quality.permits(suggestion.getRuleSetVersion(),config.fingerprint(),suggestion.getSuggestedType())) {
                        mapper.status(id,"REVIEW_REQUIRED","UNVALIDATED_CATEGORY");return;
                    }
                    if(!suggestion.isAutoEligible()||suggestion.getOriginalType()!=0||scan==null||scan.getBtype()==null||scan.getBtype()!=0
                            ||scan.getUploadflag()==null||scan.getUploadflag()==0||scan.getTypeVersion()!=suggestion.getOriginalTypeVersion()
                            ||!Objects.equals(scan.getArchiveId(),target.getArchiveId())||!ocr.belongs(scan.getId(),target.getArchiveId())) {
                        mapper.status(id,"REVIEW_REQUIRED","TYPE_OR_ARCHIVE_CHANGED");return;
                    }
                    if(fact==null||!Objects.equals(fact.getResultVersion(),suggestion.getOcrResultVersion())
                            ||!Objects.equals(fact.getModelChecksum(),config.fingerprint())||!"SUCCEEDED".equals(fact.getStatus())
                            ||!prepared.hash().equals(suggestion.getOcrContentHash())||!prepared.hash().equals(fact.getContentHash())) {
                        ocr.stale(suggestion.getScanId(),suggestion.getOcrContentHash());mapper.status(id,"REVIEW_REQUIRED","SOURCE_CHANGED");return;
                    }
                    types.apply(scan,suggestion.getSuggestedType(),"AUTO_APPLY_UNCLASSIFIED",suggestion,actor,"BACKGROUND",batch.getId(),batch.getPolicyChecksum(),null);
                    mapper.status(id,"APPLIED","EXACT_TITLE");
                    if(classifications.reviewed(id,"ACCEPTED",suggestion.getSuggestedType(),actor.getUsername())!=1) throw new BusinessException(409,"建议已被处理");
                });
            }
        } catch(BusinessException revoked) {batches.status(target.getBatchId(),"PAUSED","AUTH_REVOKED");}
        catch(RuntimeException failed) {mapper.status(id,"REVIEW_REQUIRED","AUTO_APPLY_FAILED");}
    }
    public List<Revert> preview(AuthSession session,String batch,long after,int limit) {
        batchService.owned(session,batch);access.require(session.getId(),"classification:review","classification:override");
        return mapper.preview(batch,Math.max(0,after),Math.min(100,Math.max(1,limit)));
    }
    public RollbackResult rollback(AuthSession session,String batchId,RollbackRequest request) {
        if(request==null||request.afterAuditId()<0||request.limit()<1||request.limit()>100) throw new BusinessException(400,"回退范围无效");
        batchService.owned(session,batchId);
        AuthSession actor=access.require(session.getId(),"classification:review","classification:override");
        if(quality.enabled()) throw new BusinessException(409,"回退前必须关闭自动应用总开关");
        var candidates=mapper.preview(batchId,request.afterAuditId(),request.limit());int changed=0;long after=request.afterAuditId();
        for(var candidate:candidates) {
            boolean applied=Boolean.TRUE.equals(transactions.execute(tx->{
                var batch=batches.lock(batchId);
                if(batch==null||Set.of("RUNNING","CANCEL_REQUESTED").contains(batch.getStatus())) throw new BusinessException(409,"回退前请暂停或取消批次");
                access.require(actor.getId(),"classification:review","classification:override");
                var scan=classifications.lockScan(candidate.scanId());
                if(scan==null||!candidate.eligible()||scan.getTypeVersion()!=candidate.typeVersion()||scan.getBtype()==null||scan.getBtype()!=candidate.newType()
                        ||scan.getUploadflag()==null||scan.getUploadflag()==0||!mapper.latest(batchId,candidate.auditId(),candidate.scanId())) return false;
                types.apply(scan,0,"ROLLBACK_AUTO",null,actor,"HUMAN",batchId,null,candidate.auditId());return true;
            }));
            if(applied) changed++;after=candidate.auditId();
        }
        return new RollbackResult(changed,candidates.size()-changed,after);
    }
    private AuthSession authorize(Long id) {return access.require(id,"record:read","ocr:run","ocr:batch-run","classification:auto-apply","classification:review");}
    private OcrModels.Config config(Target target) {
        try{return OcrProtocol.JSON.readValue(target.getConfigSnapshot(),OcrModels.Config.class);}catch(Exception e){throw new OcrFailure("CONFIG_CHANGED");}
    }
    @PreDestroy public void close() {executor.shutdownNow();}
}
