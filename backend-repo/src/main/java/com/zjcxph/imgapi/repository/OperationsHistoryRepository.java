package com.zjcxph.imgapi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class OperationsHistoryRepository {
    public static final int BUCKET_SECONDS = 300;
    private final JdbcTemplate jdbc;

    public OperationsHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Sample(Instant sampledAt, Instant readinessCheckedAt, String mode, int criticalFailures,
                         long heapUsedBytes, long heapMaxBytes, Long scanTotalBytes,
                         Integer storageOpenNodes, Integer storageWaiting) {}

    public static Instant bucket(Instant instant) {
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), BUCKET_SECONDS) * BUCKET_SECONDS);
    }

    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }

    @Transactional(timeout = 5)
    public int record(Sample sample) {
        return jdbc.update("""
                INSERT INTO app.mrr_operations_sample(bucket_at,sampled_at,readiness_checked_at,mode,critical_failures,
                    heap_used_bytes,heap_max_bytes,scan_total_bytes,storage_open_nodes,storage_waiting)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (bucket_at) DO NOTHING
                """, time(bucket(sample.sampledAt())), time(sample.sampledAt()), time(sample.readinessCheckedAt()), sample.mode(), sample.criticalFailures(),
                sample.heapUsedBytes(), sample.heapMaxBytes(), sample.scanTotalBytes(),
                sample.storageOpenNodes(), sample.storageWaiting());
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Sample> read(Instant from, Instant to) {
        return jdbc.query("""
                SELECT sampled_at,readiness_checked_at,mode,critical_failures,heap_used_bytes,heap_max_bytes,scan_total_bytes,
                       storage_open_nodes,storage_waiting
                FROM app.mrr_operations_sample WHERE bucket_at >= ? AND bucket_at <= ? AND sampled_at >= ? AND sampled_at <= ?
                ORDER BY bucket_at LIMIT 2017
                """, (rs,n) -> new Sample(rs.getObject(1,OffsetDateTime.class).toInstant(),rs.getObject(2,OffsetDateTime.class).toInstant(),rs.getString(3),
                rs.getInt(4),rs.getLong(5),rs.getLong(6),rs.getObject(7,Long.class),
                rs.getObject(8,Integer.class),rs.getObject(9,Integer.class)),time(bucket(from)),time(bucket(to)),time(from),time(to));
    }

    @Transactional(timeout = 5)
    public int prune(Instant cutoff) {
        return jdbc.update("""
                DELETE FROM app.mrr_operations_sample WHERE bucket_at IN (
                    SELECT bucket_at FROM app.mrr_operations_sample WHERE bucket_at < ?
                    ORDER BY bucket_at LIMIT 500 FOR UPDATE SKIP LOCKED
                ) AND bucket_at < ?
                """, time(bucket(cutoff)),time(bucket(cutoff)));
    }
}
