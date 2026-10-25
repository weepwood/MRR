package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.OcrBatchMapper;
import com.zjcxph.imgapi.mapper.OcrBatchMapper.*;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class OcrBatchService {
    public record Request(int afterId,int upperId,int maxPages,String idempotencyKey,boolean autoApply,String policyChecksum) {
        public Request(int afterId,int upperId,int maxPages,String idempotencyKey,boolean autoApply) {this(afterId,upperId,maxPages,idempotencyKey,autoApply,null);}
    }
    public record Preview(int eligibleInLimit,int upperId,boolean truncated,String readiness,boolean autoAvailable,String policyChecksum,List<Integer> autoTypes,int firstCasePages,String caseReadiness) {}
    public record View(String id,String status,int afterId,int upperId,int cursorId,int maxPages,int dispatchedCount,
                       boolean autoApply,String reasonCode,boolean discoveryDone,int processedCount,int successCount,int failedCount,int autoAppliedCount,int reviewRequiredCount,
                       boolean caseMode,int completedCases,String currentCaseCode,int currentCaseProcessed,int currentCasePlanned,int currentCaseContextCursor,boolean currentCaseContextDone) {}
    private View view(Batch b) {
        Job current=mapper.latestCase(b.getId());
        return new View(b.getId(),b.getStatus(),b.getAfterId(),b.getUpperId(),b.getCursorId(),b.getMaxPages(),b.getDispatchedCount(),b.isAutoApply(),b.getReasonCode(),b.isDiscoveryDone(),b.getProcessedCount(),b.getSuccessCount(),b.getFailedCount(),b.getAutoAppliedCount(),b.getReviewRequiredCount(),b.isCaseMode(),mapper.completedCases(b.getId()),current==null?null:current.getCaseCode(),current==null?0:current.getProcessedCount(),current==null?0:current.getPlannedCount(),current==null?0:current.getContextCursor(),current==null||current.isContextDone());
    }
    private final OcrBatchMapper mapper;private final OcrMapper queue;private final OcrAccess access;
    private final OcrJobService jobs;private final OcrLoadGuard guard;private final TransactionTemplate transactions;
    private final OcrQualityGate quality;
    public OcrBatchService(OcrBatchMapper mapper,OcrMapper queue,OcrAccess access,OcrJobService jobs,OcrLoadGuard guard,TransactionTemplate transactions,OcrQualityGate quality) {
        this.mapper=mapper;this.queue=queue;this.access=access;this.jobs=jobs;this.guard=guard;this.transactions=transactions;
        this.quality=quality;
    }
    public Preview preview(AuthSession session,Request request) {
        authorize(session);validate(request);
        int snapshot=mapper.maximumScanId();int upper=request.upperId()==0?snapshot:Math.min(request.upperId(),snapshot);
        if(upper<request.afterId()) throw new BusinessException(400,"扫描上界小于起点");
        var rows=mapper.candidates(request.afterId(),upper,request.maxPages()+1);
        List<Integer> types=List.of();
        try {var config=jobs.configuration();types=quality.types(Objects.requireNonNullElse(queue.ruleVersion(),"none"),config.fingerprint());}
        catch(RuntimeException unavailable) { /* Preview remains read-only when OCR is off. */ }
        var candidate=mapper.nextCase("",request.afterId(),upper);int firstPages=0;String caseReadiness="READY";
        if(candidate!=null) {
            String code=mapper.caseCode(candidate.archiveId());var pages=mapper.casePages(candidate.archiveId(),upper);firstPages=pages.size();
            if(code==null||mapper.caseArchives(code).size()!=1) caseReadiness="CASE_CODE_CONFLICT";
            else if(firstPages>10000) caseReadiness="CASE_TOO_LARGE";
            else if(!pages.isEmpty()&&(pages.getFirst()<=request.afterId()||mapper.caseOutsideRange(candidate.archiveId(),upper,snapshot))) caseReadiness="CASE_RANGE_INCOMPLETE";
            else if(firstPages>request.maxPages()) caseReadiness="CASE_EXCEEDS_BUDGET";
        }
        return new Preview(Math.min(rows.size(),request.maxPages()),upper,rows.size()>request.maxPages(),guard.reason(),!types.isEmpty(),quality.checksum(),types,firstPages,caseReadiness);
    }
    public View create(AuthSession session,Request request) {
        AuthSession actor=authorize(session);validate(request);
        var config=jobs.configuration();
        String ruleVersion=Objects.requireNonNullElse(queue.ruleVersion(),"none");
        if(request.autoApply()) {
            access.require(actor.getId(),"classification:auto-apply","classification:review");
            if(!Objects.equals(request.policyChecksum(),quality.checksum())||quality.types(ruleVersion,config.fingerprint()).isEmpty())
                throw new BusinessException(409,"验收策略已变化或缺少独立证据，请重新预览");
        }
        return transactions.execute(tx->{
            if(!queue.creationLock()) throw new BusinessException(429,"OCR 队列繁忙");
            if(!queue.ruleSnapshotLock()) throw new BusinessException(409,"规则正在发布，请稍后重试");
            if(!Objects.equals(ruleVersion,Objects.requireNonNullElse(queue.ruleVersion(),"none"))) throw new BusinessException(409,"规则已变化，请重新预览");
            Batch existing=mapper.idempotent(actor.getId(),request.idempotencyKey());
            if(existing!=null) {
                int requestedUpper=request.upperId();
                if(requestedUpper!=0&&existing.getScanSnapshotUpper()!=null) requestedUpper=Math.min(requestedUpper,existing.getScanSnapshotUpper());
                if(existing.getAfterId()!=request.afterId() || (request.upperId()!=0 && existing.getUpperId()!=requestedUpper)
                        || existing.getMaxPages()!=request.maxPages() || existing.isAutoApply()!=request.autoApply())
                    throw new BusinessException(409,"幂等键已用于其他批次范围");
                return view(existing);
            }
            if(mapper.activeCount()>=10) throw new BusinessException(429,"历史活动批次已达到上限 10");
            Batch batch=new Batch();batch.setId(UUID.randomUUID().toString());batch.setOwnerUserId(actor.getId());batch.setCreatedBy(actor.getUsername());
            batch.setIdempotencyKey(request.idempotencyKey());batch.setAfterId(request.afterId());
            batch.setScanSnapshotUpper(mapper.maximumScanId());
            batch.setUpperId(request.upperId()==0?batch.getScanSnapshotUpper():Math.min(request.upperId(),batch.getScanSnapshotUpper()));batch.setCursorId(request.afterId());
            if(batch.getUpperId()<request.afterId()) throw new BusinessException(400,"扫描上界小于起点");
            batch.setMaxPages(request.maxPages());batch.setStatus("RUNNING");batch.setAutoApply(request.autoApply());
            batch.setPolicyChecksum(request.autoApply()?quality.checksum():null);
            batch.setConfigSnapshot(json(config));batch.setRuleSetVersion(ruleVersion);
            batch.setLexiconVersion(Objects.requireNonNullElse(queue.lexiconVersion(),"none"));mapper.insert(batch);
            batch.setCaseMode(true);return view(batch);
        });
    }
    public Batch owned(AuthSession session,String id) {
        AuthSession actor=authorize(session);Batch batch=mapper.batch(id);
        if(batch==null) throw new BusinessException(404,"历史批次不存在");
        if(!actor.isAdmin()&&!actor.getId().equals(batch.getOwnerUserId())) throw new BusinessException(403,"无权访问此批次");
        return batch;
    }
    public View get(AuthSession session,String id) {return view(owned(session,id));}
    public List<View> list(AuthSession session,String after) {
        AuthSession actor=authorize(session);return mapper.list(actor.getId(),actor.isAdmin(),after).stream().map(this::view).toList();
    }
    public List<String> children(AuthSession session,String id,String after) {owned(session,id);return mapper.jobs(id,after);}
    public List<com.zjcxph.imgapi.ocr.OcrModels.JobView> cases(AuthSession session,String id,String after) {
        owned(session,id);return mapper.cases(id,after).stream().map(com.zjcxph.imgapi.ocr.OcrModels.JobView::from).toList();
    }
    public View action(AuthSession session,String id,String action) {
        owned(session,id);
        transactions.executeWithoutResult(tx->{
            Batch batch=mapper.lock(id);
            if("cancel".equals(action)) {
                if(!Set.of("RUNNING","PAUSED","CANCEL_REQUESTED").contains(batch.getStatus())) return;
                mapper.status(id,"CANCEL_REQUESTED",null);mapper.cancelJobs(id);
            } else if("pause".equals(action)&&"RUNNING".equals(batch.getStatus())) mapper.status(id,"PAUSED","USER_PAUSED");
            else if("resume".equals(action)&&"PAUSED".equals(batch.getStatus())) mapper.status(id,"RUNNING",null);
            else throw new BusinessException(409,"批次状态不支持此操作");
        });return get(session,id);
    }
    @Scheduled(fixedDelayString="${ocr.runtime.batch-poll-millis:3000}") public void dispatch() {
        if(!jobs.enabled()) return;
        for(String id:mapper.runnable()) {
            try {transactions.executeWithoutResult(tx->advance(id));}
            catch(BusinessException e) {mapper.status(id,"PAUSED","AUTH_OR_QUEUE_BLOCKED");}
            catch(org.springframework.dao.DuplicateKeyException e) { /* Same archive raced a manual request; cursor transaction rolls back. */ }
        }
    }
    private void advance(String id) {
        if(!queue.creationLock()) return;
        Batch batch=mapper.lock(id);
        if(batch==null||!Set.of("RUNNING","CANCEL_REQUESTED").contains(batch.getStatus())) return;
        mapper.progress(id);
        int pending=mapper.pending(id);
        if("CANCEL_REQUESTED".equals(batch.getStatus())) {
            mapper.cancelJobs(id);if(pending==0) mapper.status(id,"CANCELLED",null);return;
        }
        access.require(batch.getOwnerUserId(),"record:read","ocr:run","ocr:batch-run");
        if(pending>0) return;
        if(batch.isCaseMode()&&!quality.enabled()) mapper.disablePendingAutomatic(id);
        if(batch.isDiscoveryDone()) {
            if(mapper.pendingAutomatic(id)>0) {mapper.reason(id,quality.enabled()?"WAITING_AUTO_APPLY":"AUTO_APPLY_DISABLED");return;}
            mapper.status(id,mapper.failed(id)>0?"PARTIAL_SUCCESS":"SUCCESS",null);return;
        }
        if(!guard.allowed()) {mapper.reason(id,guard.reason());return;}
        if(mapper.historyBusy()) {mapper.reason(id,"WAITING_PREVIOUS_CASE");return;}
        if(batch.isCaseMode()) {advanceCase(batch);return;}
        int available=100-queue.queued();if(available<=0) {mapper.reason(id,"QUEUE_FULL");return;}
        int remaining=batch.getMaxPages()-batch.getDispatchedCount();
        if(remaining<=0) {mapper.advance(id,batch.getCursorId(),0,true);return;}
        List<Candidate> candidates=mapper.candidates(batch.getCursorId(),batch.getUpperId(),Math.min(200,remaining));
        Map<Long,List<Integer>> grouped=new LinkedHashMap<>();int cursor=batch.getCursorId();int selected=0;
        for(Candidate candidate:candidates) {
            if(!grouped.containsKey(candidate.archiveId())) {
                if(grouped.size()>=available||mapper.busyArchive(candidate.archiveId())) break;
                grouped.put(candidate.archiveId(),new ArrayList<>());
            }
            grouped.get(candidate.archiveId()).add(candidate.id());cursor=candidate.id();selected++;
        }
        for(var entry:grouped.entrySet()) {
            Job job=new Job();job.setId(UUID.randomUUID().toString());job.setArchiveId(entry.getKey());job.setBatchId(id);
            job.setOwnerUserId(batch.getOwnerUserId());job.setCreatedBy(batch.getCreatedBy());job.setConfigSnapshot(batch.getConfigSnapshot());
            job.setRuleSetVersion(batch.getRuleSetVersion());job.setLexiconVersion(batch.getLexiconVersion());queue.insert(job);
            for(Integer scan:entry.getValue()) queue.insertItem(job.getId(),scan);
            queue.planned(job.getId(),entry.getValue().size());
        }
        boolean done=candidates.isEmpty()||batch.getDispatchedCount()+selected>=batch.getMaxPages()||cursor>=batch.getUpperId();
        mapper.advance(id,cursor,selected,done);
    }
    private void advanceCase(Batch batch) {
        String id=batch.getId();
        if(queue.queued()>=100) {mapper.reason(id,"QUEUE_FULL");return;}
        if(batch.getDispatchedCount()>=batch.getMaxPages()) {finishDiscovery(batch);return;}
        Candidate candidate=mapper.nextCase(id,batch.getCursorId(),batch.getUpperId());
        if(candidate==null) {finishDiscovery(batch);return;}
        String code=mapper.caseCode(candidate.archiveId());
        if(code==null||mapper.caseArchives(code).size()!=1) {mapper.status(id,"PAUSED","CASE_CODE_CONFLICT");return;}
        if(mapper.busyArchive(candidate.archiveId())) {mapper.reason(id,"CASE_BUSY");return;}
        List<Integer> pages=mapper.casePages(candidate.archiveId(),batch.getUpperId());
        if(pages.size()>10000) {mapper.status(id,"PAUSED","CASE_TOO_LARGE");return;}
        if(pages.isEmpty()) return;
        if(pages.getFirst()<=batch.getAfterId()||mapper.caseOutsideRange(candidate.archiveId(),batch.getUpperId(),batch.getScanSnapshotUpper())) {mapper.status(id,"PAUSED","CASE_RANGE_INCOMPLETE");return;}
        if(pages.size()>batch.getMaxPages()-batch.getDispatchedCount()) {mapper.status(id,"PAUSED","CASE_EXCEEDS_BUDGET");return;}
        Job job=new Job();job.setId(UUID.randomUUID().toString());job.setArchiveId(candidate.archiveId());job.setBatchId(id);
        job.setCaseCode(code);job.setContextEnabled(true);job.setContextDone(false);
        job.setOwnerUserId(batch.getOwnerUserId());job.setCreatedBy(batch.getCreatedBy());job.setConfigSnapshot(batch.getConfigSnapshot());
        job.setRuleSetVersion(batch.getRuleSetVersion());job.setLexiconVersion(batch.getLexiconVersion());queue.insert(job);
        for(Integer scan:pages) queue.insertItem(job.getId(),scan);
        queue.snapshotOrder(job.getId());queue.planned(job.getId(),pages.size());
        // Advance only past the seed, not the last page: IDs of different cases may interleave.
        mapper.advance(id,candidate.id(),pages.size(),false);
    }
    private void finishDiscovery(Batch batch) {
        mapper.advance(batch.getId(),batch.getCursorId(),0,true);
        mapper.status(batch.getId(),mapper.failed(batch.getId())>0?"PARTIAL_SUCCESS":"SUCCESS",null);
    }
    private AuthSession authorize(AuthSession session) {return access.require(session==null?null:session.getId(),"record:read","ocr:run","ocr:batch-run");}
    private void validate(Request r) {
        if(r==null||r.afterId()<0||r.upperId()<0||(r.upperId()!=0&&r.upperId()<r.afterId())||r.maxPages()<1||r.maxPages()>10000
                ||r.idempotencyKey()==null||!r.idempotencyKey().matches("[A-Za-z0-9._:-]{1,128}")) throw new BusinessException(400,"历史批次范围无效");
    }
    private String json(Object value) {try{return OcrProtocol.JSON.writeValueAsString(value);}catch(Exception e){throw new BusinessException(400,"OCR 配置无效");}}
}
