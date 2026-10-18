package com.zjcxph.imgapi.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OcrBatchMapper {
    @Data class Batch {
        private String id;private Long ownerUserId;private String createdBy;private String idempotencyKey;
        private String status;private int afterId;private int upperId;private int cursorId;private int maxPages;
        private int dispatchedCount;private String configSnapshot;private String ruleSetVersion;private String lexiconVersion;
        private int processedCount;private int successCount;private int failedCount;
        private boolean autoApply;private String reasonCode;private boolean discoveryDone;
    }
    record Candidate(Integer id,Long archiveId) {}
    record Preview(int sampledEligible,int maximumScanId,boolean truncated) {}
    @Select("SELECT COALESCE(MAX(id),0) FROM app.mr_scan") int maximumScanId();
    @Select("""
        SELECT s.id,s.archive_id FROM app.mr_scan s JOIN app.mr_archive a ON a.id=s.archive_id
        WHERE s.id>#{after} AND s.id<=#{upper} AND s.btype=0 AND s.uploadflag<>0 AND s.archive_id IS NOT NULL
          AND a.bah IS NOT DISTINCT FROM NULLIF(BTRIM(s.bah),'') AND a.sjh IS NOT DISTINCT FROM NULLIF(BTRIM(s.sjh),'')
        ORDER BY s.id LIMIT #{limit}
        """) @Options(timeout=10) List<Candidate> candidates(@Param("after") int after,@Param("upper") int upper,@Param("limit") int limit);
    @Select("SELECT * FROM app.mr_ocr_batch WHERE owner_user_id=#{owner} AND idempotency_key=#{key}")
    Batch idempotent(@Param("owner") Long owner,@Param("key") String key);
    @Select("SELECT COUNT(*) FROM app.mr_ocr_batch WHERE status IN ('RUNNING','PAUSED','CANCEL_REQUESTED')") int activeCount();
    @Insert("""
        INSERT INTO app.mr_ocr_batch(id,owner_user_id,created_by,idempotency_key,after_id,upper_id,cursor_id,max_pages,
          config_snapshot,rule_set_version,lexicon_version,auto_apply)
        VALUES(#{id},#{ownerUserId},#{createdBy},#{idempotencyKey},#{afterId},#{upperId},#{afterId},#{maxPages},
          CAST(#{configSnapshot} AS jsonb),#{ruleSetVersion},#{lexiconVersion},#{autoApply})
        """) void insert(Batch batch);
    @Select("SELECT * FROM app.mr_ocr_batch WHERE id=#{id}") Batch batch(String id);
    @Select("SELECT * FROM app.mr_ocr_batch WHERE id=#{id} FOR UPDATE") Batch lock(String id);
    @Select("SELECT id FROM app.mr_ocr_batch WHERE status IN ('RUNNING','CANCEL_REQUESTED') ORDER BY created_at,id LIMIT 10") List<String> runnable();
    @Select("SELECT * FROM app.mr_ocr_batch WHERE (owner_user_id=#{owner} OR #{admin}) AND id>#{after} ORDER BY id LIMIT 20")
    List<Batch> list(@Param("owner") Long owner,@Param("admin") boolean admin,@Param("after") String after);
    @Select("SELECT EXISTS(SELECT 1 FROM app.mr_smart_classification_job WHERE archive_id=#{archive} AND status IN ('PENDING','RUNNING','CANCEL_REQUESTED'))") boolean busyArchive(Long archive);
    @Select("SELECT COUNT(*) FROM app.mr_smart_classification_job WHERE batch_id=#{id} AND status IN ('PENDING','RUNNING','CANCEL_REQUESTED')") int pending(String id);
    @Select("SELECT COUNT(*) FROM app.mr_smart_classification_job WHERE batch_id=#{id} AND status IN ('FAILED','PARTIAL_SUCCESS','CANCELLED')") int failed(String id);
    @Update("UPDATE app.mr_ocr_batch SET cursor_id=#{cursor},dispatched_count=dispatched_count+#{count},discovery_done=#{done},reason_code=NULL,updated_at=NOW() WHERE id=#{id}")
    void advance(@Param("id") String id,@Param("cursor") int cursor,@Param("count") int count,@Param("done") boolean done);
    @Update("UPDATE app.mr_ocr_batch SET status=#{status},reason_code=#{reason},updated_at=NOW() WHERE id=#{id}")
    void status(@Param("id") String id,@Param("status") String status,@Param("reason") String reason);
    @Update("UPDATE app.mr_ocr_batch SET reason_code=#{reason},updated_at=NOW() WHERE id=#{id}") void reason(@Param("id") String id,@Param("reason") String reason);
    @Update("""
        UPDATE app.mr_ocr_batch b SET processed_count=t.processed,success_count=t.success,failed_count=t.failed
        FROM (SELECT COALESCE(SUM(processed_count),0) AS processed,COALESCE(SUM(ocr_success_count),0) AS success,
          COALESCE(SUM(failed_count),0) AS failed FROM app.mr_smart_classification_job WHERE batch_id=#{id}) t WHERE b.id=#{id}
        """) void progress(String id);
    @Update("UPDATE app.mr_smart_classification_job SET cancel_requested=true,status='CANCEL_REQUESTED' WHERE batch_id=#{id} AND status IN ('PENDING','RUNNING')") void cancelJobs(String id);
    @Select("SELECT id FROM app.mr_smart_classification_job WHERE batch_id=#{id} AND id>#{after} ORDER BY id LIMIT 50")
    List<String> jobs(@Param("id") String id,@Param("after") String after);
}
