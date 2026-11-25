package com.zjcxph.imgapi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class StorageCircuitHistoryRepository {
    public static final int MAX_BATCH = 256;
    public static final int MAX_RESULTS = 100;
    private final JdbcTemplate jdbc;

    public StorageCircuitHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Event(UUID circuitId,long sequence,String sourceKind,int nodeOrdinal,
                        String eventType,Instant occurredAt,Instant collectedAt) {}

    private static OffsetDateTime time(Instant at) { return at.atOffset(ZoneOffset.UTC); }

    @Transactional(timeout=5)
    public void record(List<Event> events) {
        if(events.size()>MAX_BATCH) throw new IllegalArgumentException("event batch exceeds bound");
        if(events.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO app.mrr_storage_circuit_event(circuit_id,sequence,source_kind,node_ordinal,event_type,occurred_at,collected_at)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT(circuit_id,sequence) DO NOTHING
                """,events,MAX_BATCH,(ps,e)-> {
            ps.setObject(1,e.circuitId()); ps.setLong(2,e.sequence()); ps.setString(3,e.sourceKind());
            ps.setInt(4,e.nodeOrdinal()); ps.setString(5,e.eventType());
            ps.setObject(6,time(e.occurredAt())); ps.setObject(7,time(e.collectedAt()));
        });
    }

    @Transactional(readOnly=true,timeout=5)
    public List<Event> read(Instant from,Instant to) {
        return jdbc.query("""
                SELECT circuit_id,sequence,source_kind,node_ordinal,event_type,occurred_at,collected_at
                FROM app.mrr_storage_circuit_event WHERE occurred_at>=? AND occurred_at<=?
                ORDER BY occurred_at DESC,circuit_id,sequence DESC LIMIT 101
                """,(rs,n)->new Event(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3),rs.getInt(4),
                rs.getString(5),rs.getObject(6,OffsetDateTime.class).toInstant(),rs.getObject(7,OffsetDateTime.class).toInstant()),
                time(from),time(to));
    }

    @Transactional(timeout=5)
    public int prune(Instant cutoff) {
        return jdbc.update("""
                DELETE FROM app.mrr_storage_circuit_event e USING (
                    SELECT circuit_id,sequence FROM app.mrr_storage_circuit_event
                    WHERE occurred_at<? ORDER BY occurred_at LIMIT 500 FOR UPDATE SKIP LOCKED
                ) expired WHERE e.circuit_id=expired.circuit_id AND e.sequence=expired.sequence AND e.occurred_at<?
                """,time(cutoff),time(cutoff));
    }
}
