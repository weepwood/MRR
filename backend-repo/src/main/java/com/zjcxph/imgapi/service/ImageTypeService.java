package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.domain.MedicalRecordType;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.ClassificationMapper.*;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.OcrAccess;
import com.zjcxph.imgapi.ocr.OcrInput;
import com.zjcxph.imgapi.ocr.OcrModels.Evidence;
import com.zjcxph.imgapi.ocr.OcrProtocol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zjcxph.imgapi.utils.AuthContext;
import org.slf4j.MDC;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import java.util.*;

/** Sole application path for formal image-type mutation, with short atomic audit transactions. */
@Service
public class ImageTypeService {
    private final ClassificationMapper mapper; private final OcrMapper ocr; private final OcrAccess access;
    private final OcrInput input; private final TransactionTemplate transactions; private final CacheManager caches;
    public ImageTypeService(ClassificationMapper mapper,OcrMapper ocr,OcrAccess access,OcrInput input,
                            TransactionTemplate transactions,CacheManager caches) {
        this.mapper=mapper;this.ocr=ocr;this.access=access;this.input=input;this.transactions=transactions;this.caches=caches;
    }
    public int manual(Integer scanId,Integer type) {
        if(!MedicalRecordType.isSupported(type)) throw new BusinessException(400,"病案类型无效");
        AuthSession session=AuthContext.getCurrentUser();
        AuthSession actor=access.require(session==null?null:session.getId(),"record:edit");
        return transactions.execute(tx->{
            TypeState scan=mapper.lockScan(scanId); requireActive(scan);
            apply(scan,type,"MANUAL_EDIT",null,actor); return 1;
        });
    }
    public void review(AuthSession session,Long suggestionId,String action,Integer requestedType) {
        AuthSession actor=access.require(session==null?null:session.getId(),"record:read","classification:review");
        if(action==null || !Set.of("ACCEPT","REJECT","OVERRIDE","CONFIRM_PAIR","REJECT_PAIR").contains(action)) throw new BusinessException(400,"审核动作无效");
        // Read metadata first; IO never runs while holding database row locks.
        Suggestion snapshot=transactions.execute(tx->mapper.lockSuggestion(suggestionId));
        if(snapshot==null) throw new BusinessException(404,"分类建议不存在");
        if("REJECT_PAIR".equals(action)) {
            transactions.executeWithoutResult(tx->{
                var current=mapper.lockSuggestion(suggestionId);if(current==null) throw new BusinessException(404,"分类建议不存在");
                access.require(actor.getId(),"record:read","classification:review");
                reviewPair(current,"REJECTED",actor);
                if("PENDING".equals(current.getReviewStatus())) mapper.reviewed(current.getId(),"REJECTED",null,actor.getUsername());
            });return;
        }
        if("REJECT".equals(action)) {
            transactions.executeWithoutResult(tx->{
                Suggestion current=mapper.lockSuggestion(suggestionId); requirePending(current);
                mapper.reviewed(suggestionId,"REJECTED",null,actor.getUsername());
            }); return;
        }
        AuthSession authorized="OVERRIDE".equals(action)?access.require(actor.getId(),"classification:override"):actor;
        int type="OVERRIDE".equals(action)?Objects.requireNonNullElse(requestedType,-1):snapshot.getSuggestedType();
        if(!MedicalRecordType.isSupported(type)) throw new BusinessException(400,"病案类型无效");
        final AuthSession reviewer=authorized;
        List<Evidence> evidence=evidence(snapshot);
        Map<Integer,OcrInput.Prepared> references=new TreeMap<>();
        try(var source=input.prepare(snapshot.getScanId(),()->false)) {
            for(var reference:evidence) references.put(reference.scanId(),input.prepare(reference.scanId(),()->false));
            transactions.executeWithoutResult(tx->{
                Suggestion current=mapper.lockSuggestion(suggestionId);
                if("CONFIRM_PAIR".equals(action)) {if(current==null) throw new BusinessException(404,"分类建议不存在");}
                else requirePending(current);
                if(!Objects.equals(current.getContextEvidence(),snapshot.getContextEvidence())) throw new BusinessException(409,"参考页已变化，请重新生成建议");
                // Lock dependent scans in stable order before reading any OCR version.
                TreeSet<Integer> ids=new TreeSet<>(references.keySet());ids.add(current.getScanId());
                for(int id:ids) mapper.lockScan(id);
                TypeState scan=mapper.lockScan(current.getScanId()); requireActive(scan);
                // Same lock order as automatic application: suggestion -> scan -> OCR fact.
                // Keep the verified OCR version stable through the type + audit commit.
                var fact=ocr.lockFact(current.getScanId());
                // Accepting this exact suggestion increments the type version once. That audited
                // change does not prevent a later independent confirmation of the physical pair.
                boolean acceptedHere="CONFIRM_PAIR".equals(action)&&Set.of("ACCEPTED","OVERRIDDEN").contains(current.getReviewStatus())
                        &&current.getFinalType()!=null&&scan.getTypeVersion()==current.getOriginalTypeVersion()+1
                        &&Objects.equals(scan.getBtype(),current.getFinalType());
                if(fact==null || !Objects.equals(fact.getResultVersion(),current.getOcrResultVersion())
                        || !source.hash().equals(current.getOcrContentHash()) || !ocr.belongs(scan.getId(),scan.getArchiveId())
                        || (!acceptedHere&&(scan.getTypeVersion()!=current.getOriginalTypeVersion() || !Objects.equals(scan.getBtype(),current.getOriginalType()))))
                    throw new BusinessException(409,"影像或类型已变化，请重新生成建议");
                if(current.getReferenceScanId()!=null&&(current.getPageNumber()==null||current.getPageNumber()<=0||!Objects.equals(scan.getPageNumber(),current.getPageNumber())))
                    throw new BusinessException(409,"页面顺序已变化，请重新生成建议");
                for(var reference:evidence) {
                    var state=mapper.lockScan(reference.scanId());var referenceFact=ocr.lockFact(reference.scanId());
                    if(state==null||state.getUploadflag()==null||state.getUploadflag()==0||!Objects.equals(state.getArchiveId(),scan.getArchiveId())
                            ||state.getTypeVersion()!=reference.typeVersion()||!Objects.equals(state.getPageNumber(),reference.pageNumber())
                            ||!ocr.belongs(state.getId(),scan.getArchiveId())||referenceFact==null||!"SUCCEEDED".equals(referenceFact.getStatus())
                            ||!Objects.equals(referenceFact.getResultVersion(),reference.ocrVersion())||!Objects.equals(referenceFact.getContentHash(),reference.contentHash())
                            ||!references.get(reference.scanId()).hash().equals(reference.contentHash()))
                        throw new BusinessException(409,"参考页已变化，请重新生成建议");
                }
                if(!evidence.isEmpty()) {
                    Map<Integer,Integer> expected=new HashMap<>();expected.put(scan.getPageNumber(),scan.getId());
                    for(var reference:evidence) expected.put(reference.pageNumber(),reference.scanId());
                    var neighbors=ocr.neighbors(scan.getArchiveId(),scan.getPageNumber());
                    if(neighbors.size()>=20) throw new BusinessException(409,"参考页顺序有歧义，请重新生成建议");
                    for(int id:neighbors) {
                        var neighbor=mapper.scan(id);
                        if(neighbor!=null&&expected.containsKey(neighbor.getPageNumber())&&!Objects.equals(expected.get(neighbor.getPageNumber()),id))
                            throw new BusinessException(409,"参考页顺序有歧义，请重新生成建议");
                    }
                }
                access.require(reviewer.getId(),"record:read","classification:review");
                if("OVERRIDE".equals(action)) access.require(reviewer.getId(),"classification:override");
                if("CONFIRM_PAIR".equals(action)) {reviewPair(current,"CONFIRMED",reviewer);return;}
                apply(scan,type,"OVERRIDE".equals(action)?"OVERRIDE_SUGGESTION":"ACCEPT_SUGGESTION",current,reviewer);
                if(mapper.reviewed(current.getId(),"OVERRIDE".equals(action)?"OVERRIDDEN":"ACCEPTED",type,reviewer.getUsername())!=1)
                    throw new BusinessException(409,"建议已被处理");
            });
        } finally {references.values().forEach(OcrInput.Prepared::close);}
    }
    private List<Evidence> evidence(Suggestion suggestion) {
        try {
            List<Evidence> rows=OcrProtocol.JSON.readValue(Objects.requireNonNullElse(suggestion.getContextEvidence(),"[]"),new TypeReference<>(){});
            if(rows.size()>2||(suggestion.getReferenceScanId()!=null&&(rows.isEmpty()||rows.stream().noneMatch(r->r.scanId()==suggestion.getReferenceScanId())))
                    ||rows.stream().anyMatch(r->r.scanId()==suggestion.getScanId())||rows.stream().map(Evidence::scanId).distinct().count()!=rows.size())
                throw new IllegalArgumentException();
            return rows;
        } catch(Exception invalid) {throw new BusinessException(409,"参考页证据无效，请重新生成建议");}
    }
    private void reviewPair(Suggestion suggestion,String status,AuthSession actor) {
        var item=ocr.item(suggestion.getJobId(),suggestion.getScanId());
        if(item==null||item.getRelatedScanId()==null||!Objects.equals(item.getRelatedScanId(),suggestion.getReferenceScanId())
                ||ocr.reviewPair(suggestion.getJobId(),item.getScanId(),item.getRelatedScanId(),status,actor.getId())!=2)
            throw new BusinessException(409,"正反面关联不存在或已被处理");
        ocr.auditPair(suggestion.getJobId(),item.getScanId(),item.getRelatedScanId(),status,actor.getId());
    }
    public void apply(TypeState scan,int type,String operation,Suggestion suggestion,AuthSession actor) {
        apply(scan,type,operation,suggestion,actor,"HUMAN",null,null,null);
    }
    public void apply(TypeState scan,int type,String operation,Suggestion suggestion,AuthSession actor,String mode,String batch,String policy,Long reverts) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("类型变更需要事务");
        if(mapper.change(scan.getId(),type,scan.getTypeVersion())!=1) throw new BusinessException(409,"影像类型已变化");
        mapper.audit(scan,type,operation,suggestion,actor,MDC.get("requestId"),scan.getTypeVersion()+1,mode,batch,policy,reverts);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for(String name:java.util.List.of("scanByBah","scanByCode","scanLookupByBah","scanLookupByCode","scanById")) {
                    var cache=caches.getCache(name); if(cache!=null) cache.clear();
                }
            }
        });
    }
    private void requireActive(TypeState scan) {
        if(scan==null || scan.getUploadflag()==null || scan.getUploadflag()==0) throw new BusinessException(404,"影像不存在或已删除");
    }
    private void requirePending(Suggestion suggestion) {
        if(suggestion==null || !"PENDING".equals(suggestion.getReviewStatus())) throw new BusinessException(409,"建议已被处理");
    }
}
