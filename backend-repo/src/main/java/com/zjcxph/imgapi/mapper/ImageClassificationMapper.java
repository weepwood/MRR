package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ImageClassification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ImageClassificationMapper {

    @Insert("INSERT INTO mr_image_classification " +
            "(scan_id, archive_id, predicted_btype, confidence, classification_state, effective_source, " +
            "model_version, rule_version, ocr_title, evidence, image_checksum, error_message, classified_at, updated_at) " +
            "VALUES (#{scanId}, #{archiveId}, #{predictedBtype}, #{confidence}, #{classificationState}, " +
            "#{effectiveSource}, #{modelVersion}, #{ruleVersion}, #{ocrTitle}, CAST(#{evidence} AS JSONB), " +
            "#{imageChecksum}, #{errorMessage}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (scan_id) DO UPDATE SET archive_id = EXCLUDED.archive_id, " +
            "predicted_btype = EXCLUDED.predicted_btype, confidence = EXCLUDED.confidence, " +
            "classification_state = EXCLUDED.classification_state, effective_source = EXCLUDED.effective_source, " +
            "model_version = EXCLUDED.model_version, rule_version = EXCLUDED.rule_version, " +
            "ocr_title = EXCLUDED.ocr_title, evidence = EXCLUDED.evidence, image_checksum = EXCLUDED.image_checksum, " +
            "error_message = EXCLUDED.error_message, classified_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP")
    int upsert(ImageClassification classification);

    @Select("SELECT * FROM mr_image_classification WHERE scan_id = #{scanId}")
    ImageClassification findByScanId(@Param("scanId") Integer scanId);

    @Select("SELECT c.* FROM mr_image_classification c " +
            "JOIN mr_scan s ON s.id = c.scan_id WHERE c.archive_id = #{archiveId} " +
            "ORDER BY s.pages NULLS LAST, s.id")
    List<ImageClassification> findByArchiveId(@Param("archiveId") Long archiveId);

    @Select("<script>SELECT * FROM mr_image_classification WHERE scan_id IN " +
            "<foreach collection='scanIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ImageClassification> findByScanIds(@Param("scanIds") List<Integer> scanIds);

    @Select("SELECT * FROM mr_image_classification WHERE archive_id = #{archiveId} " +
            "AND classification_state = 'SUGGESTED' AND confidence &gt;= #{threshold} " +
            "ORDER BY scan_id")
    List<ImageClassification> findHighConfidenceSuggestions(@Param("archiveId") Long archiveId,
                                                            @Param("threshold") BigDecimal threshold);

    @Update("UPDATE mr_image_classification SET reviewed_btype = #{reviewedBtype}, reviewed_by = #{reviewedBy}, " +
            "reviewed_at = CURRENT_TIMESTAMP, classification_state = #{state}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE scan_id = #{scanId}")
    int markReviewed(@Param("scanId") Integer scanId,
                     @Param("reviewedBtype") Integer reviewedBtype,
                     @Param("reviewedBy") String reviewedBy,
                     @Param("state") String state);

    @Insert("INSERT INTO mr_image_type_audit " +
            "(scan_id, archive_id, previous_btype, proposed_btype, final_btype, action, source, " +
            "model_version, confidence, operated_by, reason) " +
            "VALUES (#{scanId}, #{archiveId}, #{previousBtype}, #{proposedBtype}, #{finalBtype}, " +
            "#{action}, #{source}, #{modelVersion}, #{confidence}, #{operatedBy}, #{reason})")
    int insertAudit(@Param("scanId") Integer scanId,
                    @Param("archiveId") Long archiveId,
                    @Param("previousBtype") Integer previousBtype,
                    @Param("proposedBtype") Integer proposedBtype,
                    @Param("finalBtype") Integer finalBtype,
                    @Param("action") String action,
                    @Param("source") String source,
                    @Param("modelVersion") String modelVersion,
                    @Param("confidence") BigDecimal confidence,
                    @Param("operatedBy") String operatedBy,
                    @Param("reason") String reason);
}
