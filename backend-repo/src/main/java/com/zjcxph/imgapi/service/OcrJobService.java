package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OcrJobService {
    private final OcrMapper mapper; private final ScanMapper scans; private final OcrAccess access;
    private final OcrEngine engine; private final OcrInput input; private final SystemSettingService settings;
    private final TransactionTemplate transactions;
    public OcrJobService(OcrMapper mapper, ScanMapper scans, OcrAccess access, OcrEngine engine,
                         OcrInput input, SystemSettingService settings, TransactionTemplate transactions) {
        this.mapper=mapper; this.scans=scans; this.access=access; this.engine=engine;
        this.input=input; this.settings=settings; this.transactions=transactions;
    }
    public boolean enabled() { return "true".equalsIgnoreCase(settings.getSetting("ocrEnabled")); }
    public JobView create(AuthSession session, Long archive, Create request) {
        AuthSession actor=access.require(session==null?null:session.getId(), "record:read", "ocr:run");
        if (!enabled()) throw new BusinessException(409,"OCR 尚未启用");
        if (archive==null || archive<=0 || request==null || request.idempotencyKey()==null
                || !request.idempotencyKey().matches("[A-Za-z0-9._:-]{1,128}")) throw new BusinessException(400,"OCR 任务参数无效");
        String profile=settings.getSetting("ocrProfile");
        Config config=new Config(profile,engine.fingerprint(profile),integer("ocrPageTimeoutSeconds",30,5,300),
                integer("ocrMaxOutputBytes",4194304,65536,16777216),decimal("ocrLowConfidenceThreshold",0.7));
        try {
            return transactions.execute(tx -> {
                if (!mapper.creationLock()) throw new BusinessException(429,"OCR 队列繁忙，请稍后重试");
                Job existing=mapper.idempotent(actor.getId(),request.idempotencyKey());
                if (existing!=null) {
                    if (!archive.equals(existing.getArchiveId()) || !Objects.equals(request.scanId(),existing.getSelectedScanId()))
                        throw new BusinessException(409,"幂等键已用于其他 OCR 范围");
                    return JobView.from(existing);
                }
                if (mapper.queued()>=100) throw new BusinessException(429,"OCR 队列已满");
                List<Integer> candidates=mapper.candidates(archive,request.scanId());
                if (candidates.isEmpty()) throw new BusinessException(409,"没有关联校验通过的未分类有效影像");
                if (candidates.size()>10000) throw new BusinessException(400,"单任务最多 10000 页，请使用历史批次");
                Job job=new Job(); job.setId(UUID.randomUUID().toString()); job.setArchiveId(archive);
                job.setOwnerUserId(actor.getId()); job.setCreatedBy(actor.getUsername()); job.setStatus("PENDING");
                job.setIdempotencyKey(request.idempotencyKey()); job.setSelectedScanId(request.scanId());
                job.setConfigSnapshot(json(config));
                job.setRuleSetVersion(Objects.requireNonNullElse(mapper.ruleVersion(),"none"));
                job.setLexiconVersion(Objects.requireNonNullElse(mapper.lexiconVersion(),"none"));
                mapper.insert(job);
                for (Integer scan:candidates) mapper.insertItem(job.getId(),scan);
                mapper.planned(job.getId(),candidates.size()); job.setPlannedCount(candidates.size());
                return JobView.from(job);
            });
        } catch (DuplicateKeyException e) { throw new BusinessException(409,"该病案已有活动 OCR 任务"); }
    }
    public Job owned(AuthSession session,String id,String permission) {
        AuthSession actor=access.require(session==null?null:session.getId(),"record:read",permission);
        Job job=mapper.job(id);
        if (job==null) throw new BusinessException(404,"OCR 任务不存在");
        if (!actor.isAdmin() && !actor.getId().equals(job.getOwnerUserId())) throw new BusinessException(403,"无权访问此任务");
        return job;
    }
    public JobView cancel(AuthSession session,String id) { owned(session,id,"ocr:run"); mapper.cancel(id); return JobView.from(mapper.job(id)); }
    public List<JobView> jobs(AuthSession session,Long archive) {
        AuthSession actor=access.require(session==null?null:session.getId(),"record:read","ocr:read");
        return mapper.jobs(archive).stream().filter(j -> actor.isAdmin()||actor.getId().equals(j.getOwnerUserId())).map(JobView::from).toList();
    }
    public List<Item> items(AuthSession session,String id,int after,int limit) {
        owned(session,id,"ocr:read"); return mapper.items(id,Math.max(0,after),Math.min(50,Math.max(1,limit)));
    }
    public FactView fact(AuthSession session,Integer scan,boolean full) {
        access.require(session==null?null:session.getId(),"record:read",full?"ocr:text-read":"ocr:read");
        var image=scans.findById(scan);
        if (image==null || image.getArchiveId()==null || !mapper.belongs(scan,image.getArchiveId()))
            throw new BusinessException(404,"没有有效关联影像");
        Fact fact=mapper.fact(scan);
        return fact==null?new FactView(scan,image.getArchiveId(),"PENDING",null,null,null,null,null,null):fact.view(full);
    }
    /** Claims one page, keeping database transactions out of file IO and inference. */
    public void runOne() {
        if (!enabled()) return;
        String token=UUID.randomUUID().toString(); Job job=mapper.claim(token);
        if (job==null) return;
        Item item=null;
        try {
            if (job.isCancelRequested()) { finish(job,token,null,null,"CANCELLED"); return; }
            access.require(job.getOwnerUserId(),"record:read","ocr:run");
            item=mapper.next(job.getId());
            if (item==null) { finish(job,token,null,null,null); return; }
            if (mapper.processing(job.getId(),item.getScanId(),token)!=1) return;
            var scan=scans.findById(item.getScanId());
            if (scan==null || !Integer.valueOf(0).equals(scan.getBtype()) || !mapper.belongs(item.getScanId(),job.getArchiveId())) {
                finish(job,token,item,null,"SKIPPED"); return;
            }
            Config config=OcrProtocol.JSON.readValue(job.getConfigSnapshot(),Config.class);
            if (!config.fingerprint().equals(engine.fingerprint(config.profile()))) throw new OcrFailure("MODEL_INVALID");
            final String jobId=job.getId();
            try (var prepared=input.prepare(item.getScanId(),()->!enabled()||!mapper.alive(jobId,token))) {
                Fact fact=mapper.fact(item.getScanId());
                if (fact==null || !prepared.hash().equals(fact.getContentHash()) || !config.fingerprint().equals(fact.getModelChecksum())
                        || !"rgb-exif-v1".equals(fact.getPreprocessingVersion())
                        || !java.util.Set.of("SUCCEEDED","LOW_CONFIDENCE").contains(fact.getStatus())) {
                    var result=engine.recognize(config.profile(),prepared.path(),config.timeout(),config.maxOutput(),()->!enabled()||!mapper.alive(jobId,token));
                    fact=new Fact(); fact.setScanId(item.getScanId()); fact.setArchiveId(job.getArchiveId());
                    fact.setResultVersion(UUID.randomUUID().toString()); fact.setContentHash(prepared.hash());
                    fact.setModelChecksum(config.fingerprint()); fact.setEngineVersion(result.engineVersion());
                    fact.setPreprocessingVersion("rgb-exif-v1"); fact.setOcrText(result.text()); fact.setLinesJson(json(result.lines()));
                    fact.setConfidence(result.confidence()); fact.setImageWidth(result.width()); fact.setImageHeight(result.height());
                    fact.setStatus(result.confidence()<config.lowConfidence()?"LOW_CONFIDENCE":"SUCCEEDED");
                }
                finish(job,token,item,fact,null);
            }
        } catch (Exception failure) {
            String code=failure instanceof OcrFailure f?f.code():failure instanceof BusinessException?"AUTH_REVOKED":"OCR_FAILED";
            finish(job,token,item,null,code);
        }
    }
    private void finish(Job job,String token,Item item,Fact fact,String failure) {
        transactions.executeWithoutResult(tx -> {
            Job current=mapper.lock(job.getId());
            if (current==null || !token.equals(current.getLeaseToken()) || current.getLeaseUntil().isBefore(java.time.LocalDateTime.now())) return;
            if (current.isCancelRequested() || "CANCELLED".equals(failure)) { mapper.cancel(job.getId()); mapper.cancelItems(job.getId()); }
            else if (item!=null) {
                if (fact!=null && mapper.belongs(item.getScanId(),job.getArchiveId())) {
                    access.require(job.getOwnerUserId(),"record:read","ocr:run");
                    Fact old=mapper.fact(item.getScanId());
                    if (old==null || !Objects.equals(old.getResultVersion(),fact.getResultVersion())) {
                        mapper.archiveFact(item.getScanId(),UUID.randomUUID().toString()); mapper.saveFact(fact);
                    }
                    mapper.complete(job.getId(),item.getScanId(),"LOW_CONFIDENCE".equals(fact.getStatus())?"LOW_CONFIDENCE":"NO_MATCH",fact.getStatus(),fact.getResultVersion(),null);
                } else if (failure!=null && new OcrFailure(failure).retryable() && item.getAttempts()<2) {
                    mapper.retry(job.getId(),item.getScanId(),failure);
                } else mapper.complete(job.getId(),item.getScanId(),"SKIPPED".equals(failure)?"SKIPPED":"FAILED","FAILED",null,failure);
            } else if ("AUTH_REVOKED".equals(failure)) { mapper.cancel(job.getId()); mapper.cancelItems(job.getId()); }
            mapper.release(job.getId(),token);
        });
    }
    private int integer(String key,int fallback,int min,int max) {
        try { return Math.max(min,Math.min(max,Integer.parseInt(settings.getSetting(key)))); } catch (Exception e) { return fallback; }
    }
    private double decimal(String key,double fallback) {
        try { double value=Double.parseDouble(settings.getSetting(key)); return Double.isFinite(value)?Math.max(0,Math.min(1,value)):fallback; } catch(Exception e) { return fallback; }
    }
    private String json(Object value) {
        try { return OcrProtocol.JSON.writeValueAsString(value); } catch(Exception e) { throw new OcrFailure("INVALID_RESPONSE"); }
    }
}
