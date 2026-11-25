package com.zjcxph.imgapi.repository;

import com.zjcxph.imgapi.exception.BusinessException;
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
    public static final int MAX_SNAPSHOT_FILES = 50000;
    private static final Set<String> RESULTS = Set.of("VERIFIED", "READABLE_NO_BASELINE",
            "SIZE_MISMATCH", "CHECKSUM_MISMATCH", "EMPTY", "READ_FAILED", "SOURCE_UNAVAILABLE",
            "TIMEOUT", "INTERRUPTED", "MISSING_OR_UNREADABLE", "BUDGET_EXCEEDED", "STALE_SCAN");
    private static final String SOURCE_FINGERPRINT =
            "encode(sha256(convert_to(ROW(archive_id, uploadflag, pages, folder, filename, brxh, bah, sjh,"
            + " source_type, source_node, source_ref, oss_url, file_size, checksum_md5, xmin::text)::text,'UTF8')),'hex')";
    private final JdbcTemplate jdbc;

    public ArchiveManifestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Lease(long id, long archiveId, int upperScanId, Integer cursorPage,
                        int cursorScanId, int processed, UUID token, Integer snapshotCount) {
        public Lease(long id, long archiveId, int upperScanId, Integer cursorPage, int cursorScanId, int processed, UUID token) {
            this(id, archiveId, upperScanId, cursorPage, cursorScanId, processed, token, null);
        }
        public int limit() { return snapshotCount == null ? MAX_FILES : snapshotCount; }
    }
    public record Candidate(int id, Integer page, String fingerprint) {}
    public record Summary(long id, long archiveId, String status, int upperScanId,
                          int processed, int verified, int anomalies, int failures,
                          String digestChain, String createdAt, String updatedAt, Integer snapshotCount, int recoveryCount) {}
    public record Anomaly(int ordinal, int scanId, String status, long bytesRead,
                          String sha256, String checkedAt) {}

    @Transactional(timeout = 5)
    public Optional<Long> create(long archiveId, long actorId) {
        Optional<Long> created = jdbc.query("""
                INSERT INTO app.mrr_archive_manifest(archive_id, created_by, upper_scan_id)
                SELECT id, ?, COALESCE((SELECT max(id) FROM app.mr_scan),0)
                FROM app.mr_archive WHERE id=?
                ON CONFLICT DO NOTHING RETURNING id
                """, (rs, n) -> rs.getLong(1), actorId, archiveId).stream().findFirst();
        if (created.isEmpty()) return created;
        long id = created.get();
        // One statement/MVCC snapshot for the complete membership. Abort, never silently truncate.
        int count = jdbc.queryForObject("""
                WITH members AS MATERIALIZED (SELECT id,pages, """ + SOURCE_FINGERPRINT + """
                     AS fingerprint FROM app.mr_scan WHERE archive_id=? AND uploadflag<>0
                     ORDER BY pages NULLS LAST,id LIMIT 50001),
                captured AS (
                    INSERT INTO app.mrr_archive_manifest_member(manifest_id,ordinal,scan_id,pages,fingerprint)
                    SELECT ?, row_number() OVER(ORDER BY pages NULLS LAST,id), id, pages, fingerprint
                    FROM members WHERE (SELECT count(*) FROM members)<=50000
                    RETURNING ordinal
                )
                SELECT count(*)::integer FROM members
                """, Integer.class, archiveId,id);
        if (count > MAX_SNAPSHOT_FILES) throw new BusinessException(409,"病案超过当前快照容量，未创建截断快照");
        jdbc.update("UPDATE app.mrr_archive_manifest SET snapshot_count=?, upper_scan_id=COALESCE((SELECT max(scan_id) FROM app.mrr_archive_manifest_member WHERE manifest_id=?),0) WHERE id=?", count,id,id);
        return created;
    }

    @Transactional(timeout = 5)
    public Optional<Lease> claim(UUID token) {
        return jdbc.query("""
                UPDATE app.mrr_archive_manifest SET status='RUNNING', lease_token=?,
                    lease_until=clock_timestamp()+interval '2 minutes', updated_at=clock_timestamp()
                WHERE id=(SELECT id FROM app.mrr_archive_manifest
                    WHERE status='PENDING' OR (status='RUNNING' AND lease_until<=clock_timestamp())
                    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id, archive_id, upper_scan_id, cursor_page, cursor_scan_id, processed, snapshot_count
                """, (rs,n) -> new Lease(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                (Integer)rs.getObject(4), rs.getInt(5), rs.getInt(6), token, (Integer)rs.getObject(7)), token).stream().findFirst();
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Candidate> next(Lease lease) {
        if (lease.snapshotCount()!=null) {
            return candidates("SELECT scan_id,pages,fingerprint FROM app.mrr_archive_manifest_member WHERE manifest_id=? AND ordinal=?",
                    lease.id(), lease.processed()+1).stream().findFirst();
        }
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

    @Transactional(readOnly = true, timeout = 5)
    public boolean matches(Lease lease, Candidate candidate) {
        return jdbc.query("SELECT " + SOURCE_FINGERPRINT
                + " FROM app.mr_scan WHERE id=? AND archive_id=? AND uploadflag<>0",
                (rs,n)->rs.getString(1), candidate.id(),lease.archiveId())
                .stream().anyMatch(candidate.fingerprint()::equals);
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
                    AND processed=? AND processed<COALESCE(snapshot_count,5000) FOR UPDATE
                """, (rs,n) -> rs.getInt(1), lease.id(), lease.token(), lease.processed());
        if (owned.isEmpty()) return false;
        if (lease.snapshotCount()!=null && !next(lease).filter(candidate::equals).isPresent()) return false;
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
                    AND (snapshot_count IS NULL OR (?='COMPLETED' AND processed=snapshot_count))
                """, status, lease.id(), lease.token(),status) == 1;
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

    /** Resume only the latest failed fixed snapshot, with a bounded persistent audit trail.
     * The existing global active unique index fences races against create or other recoveries.
     */
    @Transactional(timeout = 5)
    public boolean resume(long archiveId, long version, long actorId) {
        return jdbc.update("""
                WITH resumed AS (
                    UPDATE app.mrr_archive_manifest m SET status='PENDING', failures=0,
                        recovery_count=recovery_count+1, updated_at=clock_timestamp()
                    WHERE m.id=? AND m.archive_id=? AND m.status='FAILED'
                      AND m.snapshot_count IS NOT NULL AND m.processed<m.snapshot_count AND m.recovery_count<3
                      AND NOT EXISTS(SELECT 1 FROM app.mrr_archive_manifest newer WHERE newer.archive_id=m.archive_id AND newer.id>m.id)
                      AND NOT EXISTS(SELECT 1 FROM app.mrr_archive_manifest active WHERE active.status IN ('PENDING','RUNNING'))
                      AND m.snapshot_count=(SELECT count(*) FROM app.mrr_archive_manifest_member WHERE manifest_id=m.id)
                      AND m.snapshot_count=(SELECT max(ordinal) FROM app.mrr_archive_manifest_member WHERE manifest_id=m.id)
                      AND m.anomalies=(SELECT count(*) FROM app.mrr_archive_manifest_anomaly WHERE manifest_id=m.id)
                      AND NOT EXISTS(SELECT 1 FROM app.mrr_archive_manifest_anomaly WHERE manifest_id=m.id AND ordinal>m.processed)
                      AND m.recovery_count=(SELECT count(*) FROM app.mrr_archive_manifest_recovery WHERE manifest_id=m.id)
                    RETURNING id,recovery_count,processed
                )
                INSERT INTO app.mrr_archive_manifest_recovery(manifest_id,attempt,actor_id,processed)
                SELECT id,recovery_count,?,processed FROM resumed
                """,version,archiveId,actorId)==1;
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

    @Transactional(readOnly = true, timeout = 5)
    public List<Summary> history(long archiveId, Long before) {
        return before == null ? summaries("WHERE archive_id=? ORDER BY id DESC LIMIT 50",archiveId)
                : summaries("WHERE archive_id=? AND id<? ORDER BY id DESC LIMIT 50",archiveId,before);
    }

    public record Segment(int number, int firstOrdinal, int lastOrdinal, int planned,
                          int processed, int verified, int anomalies) {}
    public record Report(long version, long archiveId, String status, int snapshotCount,
                         int processed, int verified, int anomalies, boolean completeDigestAvailable,
                         String updatedAt, List<Segment> segments, java.util.Map<String,Integer> anomalyCounts) {}
    private record ReportPart(long version,long archiveId,String status,int snapshotCount,
                              int processed,int verified,int anomalies,boolean digest,String updatedAt,
                              Integer segment,String anomalyType,int count) {}

    /** A single statement reads one retained fixed membership and its durable results.
     * No storage access, current scan join, or raw filenames/checksums in the report.
     */
    @Transactional(readOnly = true, timeout = 5)
    public Optional<Report> report(long archiveId,long version) {
        var parts = jdbc.query("""
                WITH v AS MATERIALIZED (
                    SELECT id,archive_id,status,snapshot_count,processed,verified,anomalies,
                           (status='COMPLETED' AND processed>0 AND digest_chain IS NOT NULL) AS digest,
                           updated_at::text AS updated
                    FROM app.mrr_archive_manifest m
                    WHERE id=? AND archive_id=? AND snapshot_count IS NOT NULL
                      AND snapshot_count=(SELECT count(*) FROM app.mrr_archive_manifest_member WHERE manifest_id=m.id)
                      AND NOT EXISTS(SELECT 1 FROM app.mrr_archive_manifest_anomaly a
                          WHERE a.manifest_id=m.id AND a.ordinal>m.processed)
                ), counts AS (
                    SELECT (a.ordinal-1)/5000+1 AS segment,a.status,count(*)::int AS count
                    FROM app.mrr_archive_manifest_anomaly a JOIN v ON v.id=a.manifest_id
                    GROUP BY 1,2
                )
                SELECT v.id,v.archive_id,v.status,v.snapshot_count,v.processed,v.verified,v.anomalies,
                       v.digest,v.updated,g.segment,c.status,COALESCE(c.count,0)
                FROM v LEFT JOIN LATERAL generate_series(1,(v.snapshot_count+4999)/5000) g(segment) ON true
                LEFT JOIN counts c ON c.segment=g.segment ORDER BY g.segment,c.status
                """, (rs,n)->new ReportPart(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getInt(4),
                rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getBoolean(8),rs.getString(9),
                rs.getObject(10,Integer.class),rs.getString(11),rs.getInt(12)),version,archiveId);
        if (parts.isEmpty()) return Optional.empty();
        var head = parts.getFirst();
        var totals = new java.util.TreeMap<String,Integer>();
        var segmentCounts = new java.util.TreeMap<Integer,Integer>();
        for (var part: parts) {
            if (part.segment()==null) continue;
            segmentCounts.merge(part.segment(),part.count(),Integer::sum);
            if (part.anomalyType()!=null) {
                if (!RESULTS.contains(part.anomalyType()) || "VERIFIED".equals(part.anomalyType())) return Optional.empty();
                totals.merge(part.anomalyType(),part.count(),Integer::sum);
            }
        }
        if (totals.values().stream().mapToInt(Integer::intValue).sum()!=head.anomalies()) return Optional.empty();
        var segments = new java.util.ArrayList<Segment>();
        for (var entry: segmentCounts.entrySet()) {
            int n=entry.getKey(), first=(n-1)*MAX_FILES+1, last=Math.min(n*MAX_FILES,head.snapshotCount());
            int planned=last-first+1, processed=Math.max(0,Math.min(last,head.processed())-first+1);
            int anomalies=entry.getValue();
            if (anomalies>processed) return Optional.empty();
            segments.add(new Segment(n,first,last,planned,processed,processed-anomalies,anomalies));
        }
        return Optional.of(new Report(head.version(),head.archiveId(),head.status(),head.snapshotCount(),
                head.processed(),head.verified(),head.anomalies(),head.digest(),head.updatedAt(),
                List.copyOf(segments),java.util.Collections.unmodifiableMap(totals)));
    }

    public record Comparison(long archiveId, long baseVersion, long targetVersion,
                             int baseCount, int targetCount, int added, int removed,
                             int changed, int unchanged, String digestComparison) {}

    /** One statement: retained immutable memberships only; no current scan table or source access. */
    @Transactional(readOnly = true, timeout = 5)
    public Optional<Comparison> compare(long archiveId, long baseVersion, long targetVersion) {
        return jdbc.query("""
                WITH versions AS MATERIALIZED (
                    SELECT a.id AS base_id,b.id AS target_id,a.archive_id,
                           a.snapshot_count AS base_count,b.snapshot_count AS target_count,
                           CASE WHEN a.status='COMPLETED' AND b.status='COMPLETED'
                                      AND a.processed>0 AND b.processed>0
                                      AND a.digest_chain IS NOT NULL AND b.digest_chain IS NOT NULL
                                THEN CASE WHEN a.digest_chain=b.digest_chain THEN 'SAME' ELSE 'DIFFERENT' END
                                ELSE 'UNAVAILABLE' END AS digest_comparison
                    FROM app.mrr_archive_manifest a JOIN app.mrr_archive_manifest b ON a.archive_id=b.archive_id
                    WHERE a.archive_id=? AND a.id=? AND b.id=? AND a.id<>b.id
                        AND a.snapshot_count IS NOT NULL AND b.snapshot_count IS NOT NULL
                ), base_members AS MATERIALIZED (
                    SELECT m.scan_id,m.fingerprint FROM versions v
                    JOIN app.mrr_archive_manifest_member m ON m.manifest_id=v.base_id
                ), target_members AS MATERIALIZED (
                    SELECT m.scan_id,m.fingerprint FROM versions v
                    JOIN app.mrr_archive_manifest_member m ON m.manifest_id=v.target_id
                ), counts AS (
                    SELECT count(*) FILTER (WHERE a.scan_id IS NULL)::int AS added,
                           count(*) FILTER (WHERE b.scan_id IS NULL)::int AS removed,
                           count(*) FILTER (WHERE a.scan_id IS NOT NULL AND b.scan_id IS NOT NULL
                               AND a.fingerprint<>b.fingerprint)::int AS changed,
                           count(*) FILTER (WHERE a.scan_id IS NOT NULL AND b.scan_id IS NOT NULL
                               AND a.fingerprint=b.fingerprint)::int AS unchanged
                    FROM base_members a FULL JOIN target_members b ON a.scan_id=b.scan_id
                )
                SELECT v.archive_id,v.base_id,v.target_id,v.base_count,v.target_count,
                       c.added,c.removed,c.changed,c.unchanged,v.digest_comparison
                FROM versions v CROSS JOIN counts c
                WHERE v.base_count=c.removed+c.changed+c.unchanged
                  AND v.target_count=c.added+c.changed+c.unchanged
                """, (rs,n) -> new Comparison(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getInt(8),rs.getInt(9),rs.getString(10)),
                archiveId,baseVersion,targetVersion).stream().findFirst();
    }

    @Transactional(timeout = 5)
    public int pruneExpired(int days) {
        return jdbc.update("""
                DELETE FROM app.mrr_archive_manifest WHERE id=(
                    SELECT m.id FROM app.mrr_archive_manifest m
                    WHERE m.status NOT IN ('PENDING','RUNNING')
                      AND m.updated_at<clock_timestamp()-make_interval(days=>?)
                      AND EXISTS(SELECT 1 FROM app.mrr_archive_manifest newer
                                 WHERE newer.archive_id=m.archive_id AND newer.id>m.id
                                   AND newer.status='COMPLETED')
                    ORDER BY m.updated_at,m.id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                """,Math.max(90,days));
    }

    private List<Summary> summaries(String clause, Object... args) {
        return jdbc.query("""
                SELECT id, archive_id, status, upper_scan_id, processed, verified, anomalies, failures,
                    CASE WHEN status='COMPLETED' AND processed>0 THEN digest_chain END,
                    created_at::text, updated_at::text, snapshot_count, recovery_count
                FROM app.mrr_archive_manifest
                """ + clause, (rs,n) -> new Summary(rs.getLong(1),rs.getLong(2),rs.getString(3),
                rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getInt(8),
                rs.getString(9),rs.getString(10),rs.getString(11),(Integer)rs.getObject(12),rs.getInt(13)), args);
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
