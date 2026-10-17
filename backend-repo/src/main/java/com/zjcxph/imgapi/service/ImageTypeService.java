package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.domain.MedicalRecordType;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.ClassificationMapper.*;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.OcrAccess;
import com.zjcxph.imgapi.ocr.OcrInput;
import com.zjcxph.imgapi.utils.AuthContext;
import org.slf4j.MDC;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import java.util.Objects;

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
        if(action==null || !java.util.Set.of("ACCEPT","REJECT","OVERRIDE").contains(action)) throw new BusinessException(400,"审核动作无效");
        // Read metadata first; IO never runs while holding database row locks.
        Suggestion snapshot=transactions.execute(tx->mapper.lockSuggestion(suggestionId));
        if(snapshot==null) throw new BusinessException(404,"分类建议不存在");
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
        try(var source=input.prepare(snapshot.getScanId(),()->false)) {
            transactions.executeWithoutResult(tx->{
                Suggestion current=mapper.lockSuggestion(suggestionId); requirePending(current);
                TypeState scan=mapper.lockScan(current.getScanId()); requireActive(scan);
                var fact=ocr.fact(current.getScanId());
                if(fact==null || !Objects.equals(fact.getResultVersion(),current.getOcrResultVersion())
                        || !source.hash().equals(current.getOcrContentHash()) || !ocr.belongs(scan.getId(),scan.getArchiveId())
                        || scan.getTypeVersion()!=current.getOriginalTypeVersion() || !Objects.equals(scan.getBtype(),current.getOriginalType()))
                    throw new BusinessException(409,"影像或类型已变化，请重新生成建议");
                access.require(reviewer.getId(),"record:read","classification:review");
                if("OVERRIDE".equals(action)) access.require(reviewer.getId(),"classification:override");
                apply(scan,type,"OVERRIDE".equals(action)?"OVERRIDE_SUGGESTION":"ACCEPT_SUGGESTION",current,reviewer);
                if(mapper.reviewed(current.getId(),"OVERRIDE".equals(action)?"OVERRIDDEN":"ACCEPTED",type,reviewer.getUsername())!=1)
                    throw new BusinessException(409,"建议已被处理");
            });
        }
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
