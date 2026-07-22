package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.MigrationJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MigrationJobMapper {

    @Select("select * from migration_job order by created_at desc limit #{size} offset #{offset}")
    List<MigrationJob> findAllPaginated(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from migration_job")
    int countAll();

    @Select("select * from migration_job where id = #{id}")
    MigrationJob findById(@Param("id") Long id);

    @Select("select * from migration_job where status in ('running', 'pending', 'cancelling') " +
            "order by created_at desc limit 1")
    MigrationJob findLatestActive();

    @Insert("insert into migration_job (status, mode, scope_value, requested_count, max_scan_id, " +
            "cancel_requested, total_count, processed_count, failed_count, rate, created_by, started_at, created_at, updated_at) " +
            "values (#{status}, #{mode}, #{scopeValue}, #{requestedCount}, #{maxScanId}, " +
            "#{cancelRequested}, #{totalCount}, #{processedCount}, #{failedCount}, #{rate}, #{createdBy}, #{startedAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MigrationJob job);

    @Update("update migration_job set status = #{status}, mode = #{mode}, scope_value = #{scopeValue}, " +
            "requested_count = #{requestedCount}, max_scan_id = #{maxScanId}, cancel_requested = #{cancelRequested}, " +
            "total_count = #{totalCount}, processed_count = #{processedCount}, failed_count = #{failedCount}, " +
            "rate = #{rate}, error_message = #{errorMessage}, started_at = #{startedAt}, " +
            "completed_at = #{completedAt}, updated_at = NOW() where id = #{id}")
    int update(MigrationJob job);

    @Update("update migration_job set cancel_requested = true, status = 'cancelling', updated_at = NOW() " +
            "where id = #{id} and status in ('pending', 'running')")
    int requestCancel(@Param("id") Long id);

    @Select("select cancel_requested from migration_job where id = #{id}")
    Boolean isCancelRequested(@Param("id") Long id);

    @Update("update migration_job set status = 'interrupted', completed_at = NOW(), " +
            "error_message = COALESCE(error_message, '应用重启导致任务中断，可重新创建迁移任务继续处理'), updated_at = NOW() " +
            "where status in ('pending', 'running', 'cancelling')")
    int interruptActiveJobs();
}
