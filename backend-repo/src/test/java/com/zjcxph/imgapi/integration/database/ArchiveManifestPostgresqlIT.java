package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.ArchiveManifestRepository;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArchiveManifestPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void versionedObservationResumesAndOnlyRecordsExceptions() {
        long archive = archive();
        int first = scan(archive, 1, 1);
        int inactive = scan(archive, 2, 0);
        int last = scan(archive, null, 1);
        var repo = new ArchiveManifestRepository(jdbc);
        long job = repo.create(archive, 7L).orElseThrow();
        assertThat(repo.create(archive, 7L)).isEmpty();
        int late = scan(archive, 3, 1);
        var original = repo.claim(UUID.randomUUID()).orElseThrow();
        var row = repo.next(original).orElseThrow();
        assertThat(row.id()).isEqualTo(first);
        assertThat(repo.advance(original, row, "VERIFIED", 12, "a".repeat(64))).isTrue();
        assertThat(repo.advance(original, row, "VERIFIED", 12, "a".repeat(64))).isFalse();
        var resumed = repo.claim(UUID.randomUUID()).orElseThrow();
        var next = repo.next(resumed).orElseThrow();
        assertThat(next.id()).isEqualTo(last).isNotIn(inactive, late);
        assertThat(repo.advance(resumed, next, "MISSING_OR_UNREADABLE", 0, "")).isTrue();
        var ending = repo.claim(UUID.randomUUID()).orElseThrow();
        assertThat(repo.next(ending)).isEmpty();
        assertThat(repo.finish(ending, "COMPLETED")).isTrue();
        var summary = repo.get(job).orElseThrow();
        assertThat(summary.processed()).isEqualTo(2);
        assertThat(summary.verified()).isEqualTo(1);
        assertThat(summary.anomalies()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(repo.anomalies(job, 0)).extracting(ArchiveManifestRepository.Anomaly::scanId)
                .containsExactly(last);
        assertThat(repo.create(archive, 7L).orElseThrow()).isGreaterThan(job);
    }

    @Test
    void expiredLeaseCannotPublishAndChangedScanIsNotVerified() {
        long archive = archive();
        int scan = scan(archive, 1, 1);
        var repo = new ArchiveManifestRepository(jdbc);
        long job = repo.create(archive, 7L).orElseThrow();
        var old = repo.claim(UUID.randomUUID()).orElseThrow();
        var oldRow = repo.next(old).orElseThrow();
        jdbc.update("UPDATE app.mrr_archive_manifest SET lease_until = clock_timestamp() - interval '1 second' WHERE id=?", job);
        var recovered = repo.claim(UUID.randomUUID()).orElseThrow();
        assertThat(repo.advance(old, oldRow, "VERIFIED", 12, "a".repeat(64))).isFalse();
        var row = repo.next(recovered).orElseThrow();
        jdbc.update("UPDATE app.mr_scan SET filename='changed.synthetic' WHERE id=?", scan);
        assertThat(repo.advance(recovered, row, "VERIFIED", 12, "a".repeat(64))).isTrue();
        assertThat(repo.get(job).orElseThrow().verified()).isZero();
        assertThat(repo.anomalies(job, 0)).extracting(ArchiveManifestRepository.Anomaly::status)
                .containsExactly("STALE_SCAN");
        var next = repo.claim(UUID.randomUUID()).orElseThrow();
        assertThat(repo.cancel(job, 8L)).isTrue();
        assertThat(repo.finish(next, "COMPLETED")).isFalse();
        assertThat(repo.get(job).orElseThrow().status()).isEqualTo("CANCELLED");
    }

    @Test
    void membershipDoesNotShiftWhenPagesChangeOrRecordsDisappear() {
        long archive=archive();
        int a=scan(archive,1,1), b=scan(archive,2,1), c=scan(archive,3,1);
        var repo=new ArchiveManifestRepository(jdbc);
        long job=repo.create(archive,7L).orElseThrow();
        assertThat(repo.get(job).orElseThrow().snapshotCount()).isEqualTo(3);
        jdbc.update("UPDATE app.mr_scan SET pages=99 WHERE id=?",a);
        jdbc.update("DELETE FROM app.mr_scan WHERE id=?",b);
        scan(archive,0,1);
        for(int expected: new int[]{a,b,c}) {
            var lease=repo.claim(UUID.randomUUID()).orElseThrow();
            assertThat(repo.finish(lease,"COMPLETED")).isFalse();
            var row=repo.next(lease).orElseThrow();
            assertThat(row.id()).isEqualTo(expected);
            assertThat(repo.advance(lease,row,"VERIFIED",12,"a".repeat(64))).isTrue();
        }
        var end=repo.claim(UUID.randomUUID()).orElseThrow();
        assertThat(repo.next(end)).isEmpty();
        assertThat(repo.finish(end,"COMPLETED")).isTrue();
        assertThat(repo.anomalies(job,0)).extracting(ArchiveManifestRepository.Anomaly::scanId).containsExactly(a,b);
        assertThat(repo.get(job).orElseThrow().verified()).isEqualTo(1);
    }

    @Test
    void snapshotsContinuePastLegacySegmentAndRetentionKeepsLatestComplete() {
        long archive=archive();
        jdbc.update("""
            INSERT INTO app.mr_scan(archive_id,bah,pages,uploadflag,filename)
            SELECT ?,'123456',n,1,'synthetic.png' FROM generate_series(1,5002) n
            """,archive);
        var repo=new ArchiveManifestRepository(jdbc);
        long job=repo.create(archive,7L).orElseThrow();
        assertThat(repo.get(job).orElseThrow().snapshotCount()).isEqualTo(5002);
        // Seed a durable checkpoint to exercise the segment boundary without 5000 redundant reads.
        jdbc.update("UPDATE app.mrr_archive_manifest SET processed=5000,verified=5000 WHERE id=?",job);
        var lease=repo.claim(UUID.randomUUID()).orElseThrow();
        assertThat(lease.limit()).isEqualTo(5002);
        assertThat(repo.next(lease).orElseThrow().page()).isEqualTo(5001);
        assertThat(repo.finish(lease,"COMPLETED")).isFalse();
        assertThat(repo.cancel(job,7L)).isTrue();
        long newer=repo.create(archive,7L).orElseThrow();
        jdbc.update("UPDATE app.mrr_archive_manifest SET status='COMPLETED',processed=snapshot_count,verified=snapshot_count WHERE id=?",newer);
        jdbc.update("UPDATE app.mrr_archive_manifest SET updated_at=clock_timestamp()-interval '91 days' WHERE id IN (?,?)",job,newer);
        assertThat(repo.history(archive,null)).extracting(ArchiveManifestRepository.Summary::id).containsExactly(newer,job);
        assertThat(repo.history(archive,newer)).extracting(ArchiveManifestRepository.Summary::id).containsExactly(job);
        assertThat(repo.pruneExpired(90)).isEqualTo(1);
        assertThat(repo.get(job)).isEmpty();
        assertThat(repo.get(newer)).isPresent();
    }

    private long archive() {
        return jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES ('123456') RETURNING id", Long.class);
    }

    private int scan(long archive, Integer page, int active) {
        return jdbc.queryForObject("""
                INSERT INTO app.mr_scan(archive_id,bah,pages,uploadflag,filename)
                VALUES (?,'123456',?,?,'synthetic.png') RETURNING id
                """, Integer.class, archive, page, active);
    }
}
