package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ClassificationJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ClassificationJobMapper {

    @Insert("INSERT INTO mr_classification_job " +
            "(archive_id, scope_type, status, total_count, model_version, created_by) " +
            "VALUES (#{archiveId}, #{scopeType}, #{status}, #{totalCount}, #{modelVersion}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ClassificationJob job);

    @Select("SELECT * FROM mr_classification_job WHERE id = #{id}")
    ClassificationJob findById(@Param("id") Long id);

    @Update("UPDATE mr_classification_job SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING'")
    int markRunning(@Param("id") Long id);

    @Update("UPDATE mr_classification_job SET processed_count = #{processed}, " +
            "suggested_count = #{suggested}, no_match_count = #{noMatch}, failed_count = #{failed}, " +
            "cursor_scan_id = #{cursor}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateProgress(@Param("id") Long id,
                       @Param("processed") long processed,
                       @Param("suggested") long suggested,
                       @Param("noMatch") long noMatch,
                       @Param("failed") long failed,
                       @Param("cursor") Integer cursor);

    @Update("UPDATE mr_classification_job SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'RUNNING'")
    int markCompleted(@Param("id") Long id);

    @Update("UPDATE mr_classification_job SET status = 'FAILED', error_message = #{message}, " +
            "completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("message") String message);

    @Update("UPDATE mr_classification_job SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status IN ('PENDING', 'RUNNING')")
    int cancel(@Param("id") Long id);
}
