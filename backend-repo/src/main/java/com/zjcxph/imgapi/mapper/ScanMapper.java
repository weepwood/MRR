package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ScanMapper {
    String BAH_SEARCH_EXPRESSION = "CASE WHEN BAH ~ '^[0-9]+$' " +
            "THEN COALESCE(NULLIF(LTRIM(BAH, '0'), ''), '0') ELSE BAH END";
    String SJH_SEARCH_EXPRESSION = "CASE WHEN SJH ~ '^[0-9]+$' " +
            "THEN COALESCE(NULLIF(LTRIM(SJH, '0'), ''), '0') ELSE SJH END";

    @Select("SELECT * FROM mr_scan WHERE " +
            "BAH = #{normalizedCode} " +
            "OR " + BAH_SEARCH_EXPRESSION + " = #{searchCode} " +
            "ORDER BY pages")
    List<Scan> findBAH(
            @Param("normalizedCode") String normalizedCode,
            @Param("searchCode") String searchCode
    );

    @Select("<script>"
            + "SELECT * FROM mr_scan "
            + "<where>"
            + "<choose>"
            + "<when test='normalizedBah != null and normalizedBah != \"\" and normalizedSjh != null and normalizedSjh != \"\"'>"
            + "(BAH = #{normalizedBah} OR " + BAH_SEARCH_EXPRESSION + " = #{bahSearchCode}) "
            + "AND (SJH = #{normalizedSjh} OR " + SJH_SEARCH_EXPRESSION + " = #{sjhSearchCode})"
            + "</when>"
            + "<when test='normalizedBah != null and normalizedBah != \"\"'>"
            + "BAH = #{normalizedBah} OR " + BAH_SEARCH_EXPRESSION + " = #{bahSearchCode}"
            + "</when>"
            + "<when test='normalizedSjh != null and normalizedSjh != \"\"'>"
            + "SJH = #{normalizedSjh} OR " + SJH_SEARCH_EXPRESSION + " = #{sjhSearchCode}"
            + "</when>"
            + "</choose>"
            + "</where>"
            + " ORDER BY pages"
            + "</script>")
    List<Scan> findByCode(
            @Param("normalizedBah") String normalizedBah,
            @Param("bahSearchCode") String bahSearchCode,
            @Param("normalizedSjh") String normalizedSjh,
            @Param("sjhSearchCode") String sjhSearchCode
    );

    List<PathDO> getImagePathList(@Param("ids") List<String> ids);

    @Update("UPDATE mr_scan SET btype = #{type} WHERE id = #{id}")
    int updateImageType(@Param("id") Integer id, @Param("type") Integer type);

    @Insert("INSERT INTO mr_scan (BRXH, BAH, sjh, filename, btype, pages, openerno, uploaddate, uploadflag, folder) " +
            "VALUES (#{brxh}, #{bah}, #{sjh}, #{filename}, #{btype}, #{pages}, #{openerNo}, #{uploadDate}, #{uploadFlag}, #{folder})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Scan scan);

    @Update("UPDATE mr_scan SET uploadflag = 0 WHERE id = #{id} AND uploadflag <> 0")
    int softDeleteById(Integer id);

    int update(Scan scan);

    @Select("SELECT * FROM mr_scan ORDER BY id")
    List<Scan> findAll();

    @Select("SELECT * FROM mr_scan WHERE id = #{id}")
    Scan findById(Integer id);

    @Select("SELECT * FROM mr_scan WHERE BAH = #{normalizedBah} " +
            "OR " + BAH_SEARCH_EXPRESSION + " = #{searchCode} ORDER BY pages")
    List<Scan> findByBah(
            @Param("normalizedBah") String normalizedBah,
            @Param("searchCode") String searchCode
    );

    default List<Scan> findByBah(String bah) {
        return findByBah(
                MedicalRecordCodeUtils.normalizeOrEmpty(bah),
                MedicalRecordCodeUtils.toSearchTerm(bah)
        );
    }

    @Select("SELECT * FROM mr_scan WHERE folder = #{folder} ORDER BY id")
    List<Scan> findByFolder(@Param("folder") String folder);

    @Select("SELECT * FROM mr_scan WHERE BRXH = #{brxh} ORDER BY id")
    List<Scan> findByBrxh(@Param("brxh") String brxh);

    @Select("SELECT * FROM mr_scan ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Scan> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);

    List<Scan> findByCondition(ScanRequest request);

    List<Scan> findByConditionWithPagination(@Param("request") ScanRequest request,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countByCondition(@Param("request") ScanRequest request);

    // ==================== OSS migration ====================

    @Update("UPDATE mr_scan SET oss_url = #{ossUrl}, file_size = #{fileSize}, " +
            "checksum_md5 = #{checksumMd5}, migration_status = #{migrationStatus}, " +
            "migrated_at = NOW(), migration_next_retry = NULL, migration_lease_owner = NULL, " +
            "migration_lease_until = NULL, migration_last_error = NULL " +
            "WHERE id = #{id}")
    int updateOssInfo(@Param("id") Integer id,
                      @Param("ossUrl") String ossUrl,
                      @Param("fileSize") Long fileSize,
                      @Param("checksumMd5") String checksumMd5,
                      @Param("migrationStatus") String migrationStatus);

    @Select("SELECT * FROM mr_scan WHERE uploadflag != 0 " +
            "AND (oss_url IS NULL OR oss_url = '') " +
            "AND migration_status IS DISTINCT FROM 'failed_permanent' " +
            "ORDER BY id LIMIT #{limit}")
    List<Scan> findPendingMigration(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM mr_scan WHERE migration_status = #{status}")
    long countByMigrationStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM mr_scan WHERE uploadflag != 0")
    long countTotalUploadedScans();

    @Select("SELECT "
            + "SUM(CASE WHEN uploadflag != 0 THEN 1 ELSE 0 END) AS total, "
            + "SUM(CASE WHEN migration_status = 'migrated' THEN 1 ELSE 0 END) AS migrated, "
            + "SUM(CASE WHEN migration_status = 'verified' THEN 1 ELSE 0 END) AS verified, "
            + "SUM(CASE WHEN migration_status = 'failed_permanent' THEN 1 ELSE 0 END) AS failed "
            + "FROM mr_scan")
    Map<String, Object> countMigrationStats();

    @Select("SELECT folder, COUNT(*) AS cnt FROM mr_scan " +
            "WHERE uploadflag != 0 AND (oss_url IS NULL OR oss_url = '') " +
            "AND migration_status IS DISTINCT FROM 'failed_permanent' " +
            "GROUP BY folder ORDER BY folder")
    List<Map<String, Object>> findPendingFolders();

    @Select("SELECT * FROM mr_scan WHERE folder = #{folder} " +
            "AND uploadflag != 0 AND (oss_url IS NULL OR oss_url = '') " +
            "AND migration_status IS DISTINCT FROM 'failed_permanent' " +
            "ORDER BY id LIMIT #{limit}")
    List<Scan> findPendingByFolder(@Param("folder") String folder, @Param("limit") int limit);

    @Select("SELECT MAX(id) FROM mr_scan WHERE uploadflag != 0 " +
            "AND (oss_url IS NULL OR oss_url = '') " +
            "AND migration_status IS DISTINCT FROM 'failed_permanent' " +
            "AND COALESCE(migration_attempts, 0) < #{maxAttempts}")
    Long findMaxPendingMigrationId(@Param("maxAttempts") int maxAttempts);

    @Select("SELECT COUNT(*) FROM mr_scan WHERE id <= #{maxScanId} " +
            "AND uploadflag != 0 AND (oss_url IS NULL OR oss_url = '') " +
            "AND migration_status IS DISTINCT FROM 'failed_permanent' " +
            "AND COALESCE(migration_attempts, 0) < #{maxAttempts}")
    long countPendingMigrationsUpTo(@Param("maxScanId") long maxScanId,
                                    @Param("maxAttempts") int maxAttempts);

    /**
     * Atomically claims a batch. SKIP LOCKED prevents duplicate processing
     * when multiple worker threads or future worker processes are enabled.
     */
    @Select("""
            WITH candidates AS (
                SELECT id
                FROM mr_scan
                WHERE id <= #{maxScanId}
                  AND uploadflag != 0
                  AND (oss_url IS NULL OR oss_url = '')
                  AND COALESCE(migration_attempts, 0) < #{maxAttempts}
                  AND (migration_next_retry IS NULL OR migration_next_retry <= NOW())
                  AND (
                      migration_status IS NULL
                      OR migration_status IN ('not_migrated', 'pending', 'retry_wait', 'failed')
                      OR (
                          migration_status IN ('claimed', 'uploading')
                          AND (migration_lease_until IS NULL OR migration_lease_until < NOW())
                      )
                  )
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT #{limit}
            )
            UPDATE mr_scan s
            SET migration_status = 'claimed',
                migration_job_id = #{jobId},
                migration_attempts = COALESCE(s.migration_attempts, 0) + 1,
                migration_lease_owner = #{workerId},
                migration_lease_until = NOW() + (#{leaseSeconds} * INTERVAL '1 second'),
                migration_last_error = NULL
            FROM candidates c
            WHERE s.id = c.id
            RETURNING s.*
            """)
    List<Scan> claimPendingMigrations(@Param("jobId") long jobId,
                                      @Param("maxScanId") long maxScanId,
                                      @Param("workerId") String workerId,
                                      @Param("limit") int limit,
                                      @Param("leaseSeconds") int leaseSeconds,
                                      @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE mr_scan SET migration_status = 'uploading', " +
            "migration_lease_until = NOW() + (#{leaseSeconds} * INTERVAL '1 second') " +
            "WHERE id = #{id} AND migration_job_id = #{jobId} " +
            "AND migration_lease_owner = #{workerId} AND migration_status = 'claimed'")
    int markMigrationUploading(@Param("id") int id,
                               @Param("jobId") long jobId,
                               @Param("workerId") String workerId,
                               @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE mr_scan SET migration_status = 'retry_wait', " +
            "migration_next_retry = NOW() + (#{retryDelaySeconds} * INTERVAL '1 second'), " +
            "migration_last_error = #{errorMessage}, migration_lease_owner = NULL, " +
            "migration_lease_until = NULL " +
            "WHERE id = #{id} AND migration_job_id = #{jobId}")
    int markMigrationRetry(@Param("id") int id,
                           @Param("jobId") long jobId,
                           @Param("retryDelaySeconds") int retryDelaySeconds,
                           @Param("errorMessage") String errorMessage);

    @Update("UPDATE mr_scan SET migration_status = 'failed_permanent', " +
            "migration_last_error = #{errorMessage}, migration_next_retry = NULL, " +
            "migration_lease_owner = NULL, migration_lease_until = NULL " +
            "WHERE id = #{id} AND migration_job_id = #{jobId}")
    int markMigrationPermanentFailure(@Param("id") int id,
                                      @Param("jobId") long jobId,
                                      @Param("errorMessage") String errorMessage);

    @Update("UPDATE mr_scan SET migration_status = 'retry_wait', migration_next_retry = NOW(), " +
            "migration_job_id = NULL, migration_lease_owner = NULL, migration_lease_until = NULL, " +
            "migration_last_error = COALESCE(migration_last_error, '服务重启，已释放迁移租约') " +
            "WHERE migration_status IN ('claimed', 'uploading') AND (oss_url IS NULL OR oss_url = '')")
    int releaseInterruptedMigrationLeases();
}
