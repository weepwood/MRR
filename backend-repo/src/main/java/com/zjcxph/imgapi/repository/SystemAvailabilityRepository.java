package com.zjcxph.imgapi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class SystemAvailabilityRepository {

    private static final RowMapper<Period> PERIOD_ROW_MAPPER = SystemAvailabilityRepository::mapPeriod;

    private final JdbcTemplate jdbcTemplate;

    public SystemAvailabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Period> findOpenPeriod() {
        return jdbcTemplate.query("""
                SELECT id, status, started_at, ended_at, last_heartbeat_at, reason
                FROM app.system_availability_period
                WHERE ended_at IS NULL
                ORDER BY started_at DESC
                LIMIT 1
                """, PERIOD_ROW_MAPPER).stream().findFirst();
    }

    public List<Period> findOverlapping(Instant rangeStart, Instant rangeEnd) {
        return jdbcTemplate.query("""
                SELECT id, status, started_at, ended_at, last_heartbeat_at, reason
                FROM app.system_availability_period
                WHERE started_at < ?
                  AND COALESCE(ended_at, CURRENT_TIMESTAMP) > ?
                ORDER BY started_at ASC
                """, PERIOD_ROW_MAPPER, toDatabaseTime(rangeEnd), toDatabaseTime(rangeStart));
    }

    public void insertOpen(String status, Instant startedAt, Instant heartbeatAt, String reason) {
        jdbcTemplate.update("""
                INSERT INTO app.system_availability_period
                    (status, started_at, ended_at, last_heartbeat_at, reason)
                VALUES (?, ?, NULL, ?, ?)
                """, status, toDatabaseTime(startedAt), toDatabaseTime(heartbeatAt), reason);
    }

    public void close(long id, Instant endedAt) {
        jdbcTemplate.update("""
                UPDATE app.system_availability_period
                SET ended_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND ended_at IS NULL
                """, toDatabaseTime(endedAt), id);
    }

    public void updateHeartbeat(long id, Instant heartbeatAt, String reason) {
        jdbcTemplate.update("""
                UPDATE app.system_availability_period
                SET last_heartbeat_at = ?, reason = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND ended_at IS NULL
                """, toDatabaseTime(heartbeatAt), reason, id);
    }

    public void deleteEndedBefore(Instant cutoff) {
        jdbcTemplate.update("""
                DELETE FROM app.system_availability_period
                WHERE ended_at IS NOT NULL AND ended_at < ?
                """, toDatabaseTime(cutoff));
    }

    private static Period mapPeriod(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Period(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                toInstant(resultSet.getObject("started_at", OffsetDateTime.class)),
                toNullableInstant(resultSet.getObject("ended_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("last_heartbeat_at", OffsetDateTime.class)),
                resultSet.getString("reason")
        );
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value.toInstant();
    }

    private static Instant toNullableInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record Period(
            long id,
            String status,
            Instant startedAt,
            Instant endedAt,
            Instant lastHeartbeatAt,
            String reason
    ) {
    }
}
