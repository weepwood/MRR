package com.zjcxph.imgapi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistent observation cursor and fenced leases. Does not change medical facts. */
@Repository
public class ArchiveManifestRepository {
    public static final int MAX_FILES = 5000;
    private static final Set<String> RESULTS = Set.of("VERIFIED", "READABLE_NO_BASELINE",
            "SIZE_MISMATCH", "CHECKSUM_MISMATCH", "EMPTY", "READ_FAILED", "SOURCE_UNAVAILABLE",
            "TIMEOUT", "INTERRUPTED", "MISSING_OR_UNREADABLE", "BUDGET_EXCEEDED", "STALE_SCAN");
    private static final String SOURCE_FINGERPRINT =
            "md5(ROW(archive_id, uploadflag, pages, folder, filename, brxh, bah, sjh,"
            + " source_type, source_node, source_ref, oss_url, file_size, checksum_md5)::text)";
    private final JdbcTemplate jdbc;

    public ArchiveManifestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Lease(long id, long archiveId, int upperScanId, Integer cursorPage,
                        int cursorScanId, int processed, UUID token) {}
    public record Candidate(int id, Integer page, String fingerprint) {}
    public record Summary(long id, long archiveId, String status, int upperScanId,
                          int processed, int verified, int anomalies, int failures,
                          String digestChain, String createdAt, String updatedAt) {}
    public record Anomaly(int ordinal, int scanId, String status, long bytesRead,
                          String sha256, String checkedAt) {}

    @Transactional(timeout = 5)
    public Optional<Long> create(long archiveId, long actorId) {
        return jdbc.query("""
                INSERT INTO app.mrr_archive_manifest(archive_id, created_by, upper_scan_id)
                SELECT id, ?, COALESCE((SELECT max(id) FROM app.mr_scan),0)
                FROM app.mr_archive WHERE id=?
                ON CONFLICT DO NOTHING RETURNING id
                """, (rs, n) -> rs.getLong(1), actorId, archiveId).stream().findFirst();
    }

    @Transactional(timeout = 5)
    public Optional<Lease> claim(UUID token) {
        return jdbc.query("""
                UPDATE app.mrr_archive_manifest SET status='RUNNING', lease_token=?,
                    lease_until=clock_timestamp()+interval '2 minutes', updated_at=clock_timestamp()
                WHERE id=(SELECT id FROM app.mrr_archive_manifest
                    WHERE status='PENDING' OR (status='RUNNING' AND lease_until<=clock_timestamp())
                    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id, archive_id, upper_scan_id, cursor_page, cursor_scan_id, processed
                """, (rs,n) -> new Lease(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                (Integer)rs.getObject(4), rs.getInt(5), rs.getInt(6), token), token).stream().findFirst();
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Candidate> next(Lease lease) {
        // Reuse existing (archive_id,pages,id) index. Null pages form the final phase.
        String prefix = "SELECT id,pages," + SOURCE_FINGERPRINT
                + " FROM app.mr_scan WHERE archive_id=? AND id<=? AND uploadflag<>0";
        if (lease.cursorScanId() == 0 || lease.cursorPage() != null) {
            List<Candidate> rows = lease.cursorScanId() == 0
                    ? candidates(prefix + " AND pages IS NOT NULL ORDER BY pages,id LIMIT 1",
                        lease.archiveId(), lease.upperScanId())
                    : candidates(prefix + " AND (pages,id)>(?,?) ORDER BY pages,id LIMIT 1",
                        lease.archiveId(), lease.upperScanId(), lease.cursorPage(), lease.cursorScanId());
            if (!rows.isEmpty()) return Optional.of(rows.getFirst());
        }
        int after = lease.cursorPage() == null ? lease.cursorScanId() : 0;
        return candidates(prefix + " AND pages IS NULL AND id>? ORDER BY id LIMIT 1",
                lease.archiveId(), lease.upperScanId(), after).stream().findFirst();
    }

    private List<Candidate> candidates(String sql, Object... args) {
        return jdbc.query(sql, (rs,n) -> new Candidate(rs.getInt(1), (Integer)rs.getObject(2),
                rs.getString(3)), args);
    }

    @Transactional(timeout = 5)
    public boolean advance(Lease lease, Candidate candidate, String status, long bytes, String sha256) {
        if (!RESULTS.contains(status) || bytes < 0 || bytes > 8L*1024*1024+1)
            throw new IllegalArgumentException("Invalid verification result");
        String digest = sha256 != null && sha256.matches("[0-9a-f]{64}") ? sha256 : null;
        if ("VERIFIED".equals(status) && digest == null)
            throw new IllegalArgumentException("Missing verified digest");
        // Fencing + cursor guard; cancelled/reclaimed work must never publish.
        List<Integer> owned = jdbc.query("""
                SELECT processed FROM app.mrr_archive_manifest
                WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>clock_timestamp()
                    AND processed=? AND processed<5000 FOR UPDATE
                """, (rs,n) -> rs.getInt(1), lease.id(), lease.token(), lease.processed());
        if (owned.isEmpty()) return false;
        List<String> current = jdbc.query("SELECT " + SOURCE_FINGERPRINT
                + " FROM app.mr_scan WHERE id=? AND archive_id=? AND uploadflag<>0 FOR SHARE",
                (rs,n) -> rs.getString(1), candidate.id(), lease.archiveId());
        if (current.isEmpty() || !candidate.fingerprint().equals(current.getFirst())) {
            status = "STALE_SCAN"; bytes = 0; digest = null;
        }
        boolean verified = "VERIFIED".equals(status);
        int count = jdbc.update("""
                UPDATE app.mrr_archive_manifest SET cursor_page=?, cursor_scan_id=?,
                    processed=processed+1, verified=verified+?, anomalies=anomalies+?,
                    digest_chain=CASE WHEN ? AND digest_chain IS NOT NULL
                        THEN encode(sha256(convert_to(digest_chain || ':' || CAST(? AS text)
                            || ':' || CAST(? AS text),'UTF8')),'hex') ELSE NULL END,
                    status='PENDING', lease_token=NULL, lease_until=NULL, failures=0,
                    updated_at=clock_timestamp()
                WHERE id=? AND lease_token=? AND lease_until>clock_timestamp()
                """, candidate.page(), candidate.id(), verified ? 1 : 0, verified ? 0 : 1,
                verified, candidate.id(), digest, lease.id(), lease.token());
        if (count == 0) return false;
        if (!verified) jdbc.update("""
                INSERT INTO app.mrr_archive_manifest_anomaly
                    (manifest_id,ordinal,scan_id,status,bytes_read,sha256)
                VALUES (?,?,?,?,?,?)
                """, lease.id(), owned.getFirst()+1, candidate.id(), status, bytes, digest);
        return true;
    }

    @Transactional(timeout = 5)
    public boolean finish(Lease lease, String status) {
        if (!Set.of("COMPLETED","PARTIAL").contains(status)) throw new IllegalArgumentException("Invalid finish");
        return jdbc.update("""
                UPDATE app.mrr_archive_manifest SET status=?, lease_token=NULL, lease_until=NULL,
                    updated_at=clock_timestamp()
                WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>clock_timestamp()
                """, status, lease.id(), lease.token()) == 1;
    }

    @Transactional(timeout = 5)
    public void retry(Lease lease, boolean failure) {
        jdbc.update("""
                UPDATE app.mrr_archive_manifest SET
                    failures=failures+?,
                    status=CASE WHEN failures+? >= 3 THEN 'FAILED' ELSE 'PENDING' END,
                    lease_token=NULL, lease_until=NULL, updated_at=clock_timestamp()
                WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>clock_timestamp()
                """, failure ? 1 : 0, failure ? 1 : 0, lease.id(), lease.token());
    }

    @Transactional(timeout = 5)
    public boolean cancel(long id, long actorId) {
        return jdbc.update("""
                UPDATE app.mrr_archive_manifest SET status='CANCELLED', cancelled_by=?,
                    lease_token=NULL, lease_until=NULL, updated_at=clock_timestamp()
                WHERE id=? AND status IN ('PENDING','RUNNING')
                """, actorId, id) == 1;
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Summary> get(long id) {
        return summaries("WHERE id=?", id).stream().findFirst();
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Summary> latest(long archiveId) {
        return summaries("WHERE archive_id=? ORDER BY id DESC LIMIT 1", archiveId).stream().findFirst();
    }

    private List<Summary> summaries(String clause, Object... args) {
        return jdbc.query("""
                SELECT id, archive_id, status, upper_scan_id, processed, verified, anomalies, failures,
                    CASE WHEN status='COMPLETED' AND processed>0 THEN digest_chain END,
                    created_at::text, updated_at::text
                FROM app.mrr_archive_manifest
                """ + clause, (rs,n) -> new Summary(rs.getLong(1),rs.getLong(2),rs.getString(3),
                rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getInt(8),
                rs.getString(9),rs.getString(10),rs.getString(11)), args);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Anomaly> anomalies(long id, int afterOrdinal) {
        return jdbc.query("""
                SELECT ordinal,scan_id,status,bytes_read,sha256,checked_at::text
                FROM app.mrr_archive_manifest_anomaly WHERE manifest_id=? AND ordinal>?
                ORDER BY ordinal LIMIT 100
                """, (rs,n) -> new Anomaly(rs.getInt(1),rs.getInt(2),rs.getString(3),
                rs.getLong(4),rs.getString(5),rs.getString(6)), id, Math.max(0,afterOrdinal));
    }
}
