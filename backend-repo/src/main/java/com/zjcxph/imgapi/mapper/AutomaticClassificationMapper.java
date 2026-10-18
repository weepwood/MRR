package com.zjcxph.imgapi.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AutomaticClassificationMapper {
    @Data class Target {
        private Long suggestionId;private String batchId;private Long ownerUserId;private Long archiveId;
        private Integer scanId;private String ocrContentHash;private String ruleSetVersion;private String configSnapshot;private String policyChecksum;
    }
    record Revert(Long auditId,Integer scanId,int newType,long typeVersion,boolean eligible) {}
    @Select("""
        SELECT s.id FROM app.mr_image_classification_suggestion s JOIN app.mr_smart_classification_job j ON j.id=s.job_id
        JOIN app.mr_ocr_batch b ON b.id=j.batch_id WHERE b.status='RUNNING' AND b.auto_apply
          AND s.review_status='PENDING' AND s.auto_status='PENDING' AND s.auto_eligible ORDER BY s.id LIMIT 1
        """) Long candidate();
    @Select("""
        SELECT s.id AS suggestion_id,b.id AS batch_id,b.owner_user_id,j.archive_id,s.scan_id,s.ocr_content_hash,
          s.rule_set_version,j.config_snapshot::text,b.policy_checksum
        FROM app.mr_image_classification_suggestion s JOIN app.mr_smart_classification_job j ON j.id=s.job_id
        JOIN app.mr_ocr_batch b ON b.id=j.batch_id WHERE s.id=#{id}
        """) Target target(Long id);
    @Update("UPDATE app.mr_image_classification_suggestion SET auto_status=#{status},reason_code=#{reason},updated_at=NOW() WHERE id=#{id} AND auto_status='PENDING' AND review_status='PENDING'")
    void status(@Param("id") Long id,@Param("status") String status,@Param("reason") String reason);
    @Select("""
        SELECT a.id AS audit_id,a.scan_id,a.new_type,a.type_version,
          (s.type_version=a.type_version AND s.btype=a.new_type AND s.uploadflag<>0
           AND s.archive_id IS NOT DISTINCT FROM a.archive_id
           AND a.id=(SELECT MAX(v.id) FROM app.mr_image_type_audit v WHERE v.scan_id=a.scan_id)
           AND NOT EXISTS(SELECT 1 FROM app.mr_image_type_audit r WHERE r.reverts_audit_id=a.id)) AS eligible
        FROM app.mr_image_type_audit a JOIN app.mr_scan s ON s.id=a.scan_id
        WHERE a.batch_id=#{batch} AND a.operation='AUTO_APPLY_UNCLASSIFIED' AND a.old_type=0 AND a.id>#{after}
        ORDER BY a.id LIMIT #{limit}
        """) List<Revert> preview(@Param("batch") String batch,@Param("after") long after,@Param("limit") int limit);
    @Select("""
        SELECT EXISTS(SELECT 1 FROM app.mr_image_type_audit a WHERE a.id=#{audit} AND a.scan_id=#{scan}
          AND a.operation='AUTO_APPLY_UNCLASSIFIED' AND a.batch_id=#{batch} AND a.old_type=0
          AND a.id=(SELECT MAX(v.id) FROM app.mr_image_type_audit v WHERE v.scan_id=#{scan})
          AND EXISTS(SELECT 1 FROM app.mr_scan s WHERE s.id=a.scan_id AND s.type_version=a.type_version
              AND s.btype=a.new_type AND s.uploadflag<>0 AND s.archive_id IS NOT DISTINCT FROM a.archive_id)
          AND NOT EXISTS(SELECT 1 FROM app.mr_image_type_audit r WHERE r.reverts_audit_id=a.id))
        """) boolean latest(@Param("batch") String batch,@Param("audit") Long audit,@Param("scan") Integer scan);
}
