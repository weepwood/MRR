package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** All queue statements are bounded; lease fencing prevents late workers from publishing. */
@Mapper
public interface OcrMapper {
    @Select("SELECT * FROM app.mr_smart_classification_job WHERE id=#{id}") Job job(String id);
    @Select("SELECT * FROM app.mr_smart_classification_job WHERE id=#{id} FOR UPDATE") Job lock(String id);
    @Select("SELECT * FROM app.mr_smart_classification_job WHERE owner_user_id=#{owner} AND idempotency_key=#{key}")
    Job idempotent(@Param("owner") Long owner, @Param("key") String key);
    @Select("SELECT COUNT(*) FROM app.mr_smart_classification_job WHERE status IN ('PENDING','RUNNING','CANCEL_REQUESTED')")
    int queued();
    @Select("SELECT pg_try_advisory_xact_lock(1804518)") boolean creationLock();
    @Select("SELECT pg_try_advisory_xact_lock_shared(1804519)") boolean ruleSnapshotLock();
    @Select("SELECT version FROM app.mr_classification_rule_set WHERE status='PUBLISHED'") String ruleVersion();
    @Select("SELECT version FROM app.mr_ocr_lexicon_set WHERE status='PUBLISHED'") String lexiconVersion();
    @Select("""
        SELECT s.id FROM app.mr_scan s JOIN app.mr_archive a ON a.id=s.archive_id
        WHERE s.archive_id=#{archive} AND s.btype=0 AND s.uploadflag<>0
          AND a.bah IS NOT DISTINCT FROM NULLIF(BTRIM(s.bah),'')
          AND a.sjh IS NOT DISTINCT FROM NULLIF(BTRIM(s.sjh),'')
          AND (#{scan,jdbcType=INTEGER} IS NULL OR s.id=#{scan})
        ORDER BY s.id LIMIT 10001
        """) List<Integer> candidates(@Param("archive") Long archive, @Param("scan") Integer scan);
    @Select("""
        SELECT EXISTS(SELECT 1 FROM app.mr_scan s JOIN app.mr_archive a ON a.id=s.archive_id
        WHERE s.id=#{scan} AND s.archive_id=#{archive} AND s.uploadflag<>0
          AND a.bah IS NOT DISTINCT FROM NULLIF(BTRIM(s.bah),'')
          AND a.sjh IS NOT DISTINCT FROM NULLIF(BTRIM(s.sjh),''))
        """) boolean belongs(@Param("scan") Integer scan, @Param("archive") Long archive);
    @Insert("""
        INSERT INTO app.mr_smart_classification_job
        (id,archive_id,scope,owner_user_id,created_by,idempotency_key,selected_scan_id,config_snapshot,rule_set_version,lexicon_version,batch_id)
        VALUES(#{id},#{archiveId},'UNCLASSIFIED',#{ownerUserId},#{createdBy},#{idempotencyKey},#{selectedScanId},
        CAST(#{configSnapshot} AS jsonb),#{ruleSetVersion},#{lexiconVersion},#{batchId})
        """) void insert(Job job);
    @Insert("INSERT INTO app.mr_smart_classification_job_item(job_id,scan_id,original_type,original_type_version) SELECT #{job},id,btype,type_version FROM app.mr_scan WHERE id=#{scan}")
    void insertItem(@Param("job") String job, @Param("scan") Integer scan);
    @Update("UPDATE app.mr_smart_classification_job SET planned_count=#{count} WHERE id=#{id}")
    void planned(@Param("id") String id, @Param("count") int count);
    @Select(value = """
        WITH target AS (SELECT id FROM app.mr_smart_classification_job
        WHERE status IN ('PENDING','RUNNING','CANCEL_REQUESTED')
          AND (lease_until IS NULL OR lease_until < NOW())
          AND (cancel_requested OR batch_id IS NULL OR (#{allowBatch} AND EXISTS
            (SELECT 1 FROM app.mr_ocr_batch b WHERE b.id=batch_id AND b.status='RUNNING')))
        ORDER BY CASE WHEN batch_id IS NULL THEN 0 ELSE 1 END,created_at,id FOR UPDATE SKIP LOCKED LIMIT 1)
        UPDATE app.mr_smart_classification_job j SET lease_token=#{token}, lease_until=NOW()+INTERVAL '2 minutes',
          status=CASE WHEN cancel_requested THEN 'CANCEL_REQUESTED' ELSE 'RUNNING' END,
          started_at=COALESCE(started_at,NOW()),updated_at=NOW()
        FROM target WHERE j.id=target.id RETURNING j.*
        """, affectData = true) Job claimEligible(@Param("token") String token,@Param("allowBatch") boolean allowBatch);
    default Job claim(String token) {return claimEligible(token,true);}
    @Select("""
        SELECT * FROM app.mr_smart_classification_job_item WHERE job_id=#{id}
        AND status IN ('PENDING','PROCESSING') AND (next_attempt_at IS NULL OR next_attempt_at<=NOW())
        ORDER BY scan_id LIMIT 1
        """) Item next(String id);
    @Update("UPDATE app.mr_smart_classification_job_item SET status='PROCESSING',attempts=attempts+1,updated_at=NOW() WHERE job_id=#{job} AND scan_id=#{scan}")
    void processing(@Param("job") String job, @Param("scan") Integer scan);
    @Select("SELECT EXISTS(SELECT 1 FROM app.mr_smart_classification_job WHERE id=#{id} AND lease_token=#{token} AND lease_until>NOW() AND NOT cancel_requested)")
    boolean alive(@Param("id") String id, @Param("token") String token);
    @Update("UPDATE app.mr_smart_classification_job SET cancel_requested=true,status='CANCEL_REQUESTED',updated_at=NOW() WHERE id=#{id} AND status IN ('PENDING','RUNNING')")
    void cancel(String id);
    @Update("""
        UPDATE app.mr_smart_classification_job_item SET status=#{status},ocr_status=#{ocrStatus},
        ocr_result_version=#{version},failure_code=#{failure},processed_at=NOW(),updated_at=NOW()
        WHERE job_id=#{job} AND scan_id=#{scan}
        """) void complete(@Param("job") String job, @Param("scan") Integer scan, @Param("status") String status,
                            @Param("ocrStatus") String ocrStatus, @Param("version") String version, @Param("failure") String failure);
    @Update("""
        UPDATE app.mr_smart_classification_job_item SET status='PENDING',failure_code=#{failure},
          next_attempt_at=NOW()+INTERVAL '10 seconds',updated_at=NOW()
        WHERE job_id=#{job} AND scan_id=#{scan}
        """) void retry(@Param("job") String job, @Param("scan") Integer scan, @Param("failure") String failure);
    @Update("UPDATE app.mr_smart_classification_job_item SET status='CANCELLED',updated_at=NOW() WHERE job_id=#{id} AND status IN ('PENDING','PROCESSING')")
    void cancelItems(String id);
    @Update("""
        UPDATE app.mr_smart_classification_job j SET
          processed_count=t.done,ocr_success_count=t.success,failed_count=t.failed,
          status=CASE WHEN cancel_requested THEN 'CANCELLED' WHEN t.pending>0 THEN 'RUNNING'
             WHEN t.failed=0 THEN 'SUCCESS' WHEN t.success>0 THEN 'PARTIAL_SUCCESS' ELSE 'FAILED' END,
          finished_at=CASE WHEN t.pending=0 OR cancel_requested THEN NOW() ELSE NULL END,
          lease_token=NULL,lease_until=NULL,updated_at=NOW()
        FROM (SELECT COUNT(*) FILTER(WHERE status NOT IN ('PENDING','PROCESSING')) AS done,
              COUNT(*) FILTER(WHERE ocr_status IN ('SUCCEEDED','LOW_CONFIDENCE')) AS success,
              COUNT(*) FILTER(WHERE status='FAILED') AS failed,
              COUNT(*) FILTER(WHERE status IN ('PENDING','PROCESSING')) AS pending
              FROM app.mr_smart_classification_job_item WHERE job_id=#{id}) t
        WHERE j.id=#{id} AND j.lease_token=#{token}
        """) void release(@Param("id") String id, @Param("token") String token);
    @Select("SELECT * FROM app.mr_scan_ocr WHERE scan_id=#{scan}") Fact fact(Integer scan);
    @Select("SELECT * FROM app.mr_scan_ocr WHERE scan_id=#{scan} FOR UPDATE") Fact lockFact(Integer scan);
    @Update("UPDATE app.mr_scan_ocr SET status='STALE',updated_at=NOW() WHERE scan_id=#{scan} AND content_hash=#{hash}")
    void stale(@Param("scan") Integer scan,@Param("hash") String hash);
    @Insert("""
        INSERT INTO app.mr_scan_ocr_history(result_version,scan_id,archive_id,content_hash,engine_version,
          model_checksum,preprocessing_version,ocr_text,lines_json,confidence,image_width,image_height,processed_at)
        SELECT COALESCE(result_version,#{legacyVersion}),scan_id,archive_id,content_hash,engine_version,
          model_checksum,preprocessing_version,ocr_text,lines_json,confidence,image_width,image_height,processed_at
        FROM app.mr_scan_ocr WHERE scan_id=#{scan} AND ocr_text IS NOT NULL ON CONFLICT DO NOTHING
        """) void archiveFact(@Param("scan") Integer scan, @Param("legacyVersion") String legacyVersion);
    @Insert("""
        INSERT INTO app.mr_scan_ocr(scan_id,archive_id,status,engine,engine_version,ocr_text,confidence,text_length,
          content_hash,result_version,model_checksum,preprocessing_version,lines_json,image_width,image_height,processed_at)
        VALUES(#{scanId},#{archiveId},#{status},'rapidocr',#{engineVersion},#{ocrText},#{confidence},LENGTH(#{ocrText}),
          #{contentHash},#{resultVersion},#{modelChecksum},#{preprocessingVersion},CAST(#{linesJson} AS jsonb),#{imageWidth},#{imageHeight},NOW())
        ON CONFLICT(scan_id) DO UPDATE SET archive_id=EXCLUDED.archive_id,status=EXCLUDED.status,
          engine=EXCLUDED.engine,engine_version=EXCLUDED.engine_version,ocr_text=EXCLUDED.ocr_text,
          confidence=EXCLUDED.confidence,text_length=EXCLUDED.text_length,content_hash=EXCLUDED.content_hash,
          result_version=EXCLUDED.result_version,model_checksum=EXCLUDED.model_checksum,
          preprocessing_version=EXCLUDED.preprocessing_version,lines_json=EXCLUDED.lines_json,
          image_width=EXCLUDED.image_width,image_height=EXCLUDED.image_height,processed_at=NOW(),
          error_code=NULL,error_message=NULL,updated_at=NOW()
        """) void saveFact(Fact fact);
    @Select("""
        SELECT job_id,scan_id,original_type,original_type_version,status,attempts,failure_code,ocr_result_version,suggested_type,rule_score,ocr_confidence
        FROM app.mr_smart_classification_job_item WHERE job_id=#{job} AND scan_id>#{after}
        ORDER BY scan_id LIMIT #{limit}
        """) List<Item> items(@Param("job") String job, @Param("after") int after, @Param("limit") int limit);
    @Select("SELECT * FROM app.mr_smart_classification_job WHERE archive_id=#{archive} ORDER BY created_at DESC,id LIMIT 20")
    List<Job> jobs(Long archive);
}
