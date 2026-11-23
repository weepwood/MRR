package com.zjcxph.imgapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 调用 {@code V20261123170000__access_log_monthly_partitions.sql} 定义的分区迁移函数（ADR-0010）。
 *
 * <p>每个方法是一次自动提交的调用，即一个数据库事务；并发安全由函数内的状态行锁与咨询锁保证。</p>
 */
@Repository
public class AccessLogPartitionRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AccessLogPartitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status() {
        return json("SELECT app.access_log_partition_status()::text");
    }

    public Map<String, Object> copyBatch(int limit) {
        return json("SELECT app.access_log_partition_copy_batch(?)::text", limit);
    }

    public int maintainPartitions(int monthsAhead) {
        Integer created = jdbcTemplate.queryForObject(
                "SELECT app.access_log_maintain_partitions(?)", Integer.class, monthsAhead);
        return created == null ? 0 : created;
    }

    public int dropExpiredPartitions(LocalDateTime cutoff) {
        Integer dropped = jdbcTemplate.queryForObject(
                "SELECT app.access_log_drop_expired_partitions(?)", Integer.class, Timestamp.valueOf(cutoff));
        return dropped == null ? 0 : dropped;
    }

    public Map<String, Object> cutover() {
        return json("SELECT app.access_log_partition_cutover()::text");
    }

    public Map<String, Object> rollback() {
        return json("SELECT app.access_log_partition_rollback()::text");
    }

    public Map<String, Object> abort() {
        return json("SELECT app.access_log_partition_abort()::text");
    }

    private Map<String, Object> json(String sql, Object... args) {
        String value = jdbcTemplate.queryForObject(sql, String.class, args);
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("access_log 分区函数返回了无法解析的结果", e);
        }
    }
}
