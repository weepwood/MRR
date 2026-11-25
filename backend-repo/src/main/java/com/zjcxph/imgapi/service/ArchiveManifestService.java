package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.repository.ArchiveManifestRepository;
import com.zjcxph.imgapi.storage.ResolvedImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Explicit observations only; no automatic repair, background sweep or archive lifecycle transition. */
@Service
public class ArchiveManifestService {
    private static final Logger log = LoggerFactory.getLogger(ArchiveManifestService.class);
    private final ArchiveManifestRepository repository;
    private final ImageContentMapper mapper;
    private final ResolvedImageStorage storage;
    private final DeploymentReadinessService readiness;
    private final MaintenanceModeService maintenance;
    private final boolean enabled;

    public ArchiveManifestService(ArchiveManifestRepository repository, ImageContentMapper mapper,
                                  ResolvedImageStorage storage, DeploymentReadinessService readiness,
                                  MaintenanceModeService maintenance,
                                  @Value("${app.archive-manifest.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.mapper = mapper;
        this.storage = storage;
        this.readiness = readiness;
        this.maintenance = maintenance;
        this.enabled = enabled;
    }

    public record Observation(long version, long archiveId, String status, int processed,
                              int verified, int anomalies, boolean completeDigestAvailable,
                              String createdAt, String updatedAt, Integer snapshotCount, int recoveryCount) {
        static Observation from(ArchiveManifestRepository.Summary s) {
            return new Observation(s.id(), s.archiveId(), s.status(), s.processed(), s.verified(),
                    s.anomalies(), s.digestChain()!=null, s.createdAt(), s.updatedAt(),s.snapshotCount(),s.recoveryCount());
        }
    }
    public record Issue(int cursor, int scanId, String status, long bytesRead, boolean digestAvailable, String checkedAt) {}

    public boolean enabled() { return enabled; }

    public Observation start(long archiveId, long actorId) {
        if (!enabled) throw new BusinessException(503, "病案完整性后台校验已关闭");
        if (readiness.isReadOnly() || maintenance.isEnabled()) throw new BusinessException(503, "系统处于只读模式");
        if (archiveId <= 0 || actorId <= 0) throw new BusinessException(400, "无效病案或操作者");
        long id = repository.create(archiveId, actorId)
                .orElseThrow(() -> new BusinessException(409, "病案不存在或已有后台校验任务"));
        return get(id);
    }

    public Observation latest(long archiveId) {
        return repository.latest(archiveId).map(Observation::from).orElse(null);
    }

    public List<Observation> history(long archiveId, Long before) {
        if (archiveId<=0 || (before!=null && before<=0)) throw new BusinessException(400,"无效病案或游标");
        return repository.history(archiveId,before).stream().map(Observation::from).toList();
    }

    public ArchiveManifestRepository.Comparison compare(long archiveId, long baseVersion, long targetVersion) {
        if (archiveId <= 0 || baseVersion <= 0 || targetVersion <= 0 || baseVersion == targetVersion)
            throw new BusinessException(400, "请选择同一病案的两个不同版本");
        return repository.compare(archiveId,baseVersion,targetVersion)
                .orElseThrow(() -> new BusinessException(409, "版本不存在、已清理或不具备完整固定成员快照"));
    }

    public ArchiveManifestRepository.Report report(long archiveId,long version) {
        if (archiveId<=0 || version<=0) throw new BusinessException(400,"无效病案或校验版本");
        return repository.report(archiveId,version)
                .orElseThrow(() -> new BusinessException(409,"版本不存在、已清理或固定成员与观察结果不一致"));
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void pruneHistory() {
        if (enabled && !readiness.isReadOnly() && !maintenance.isEnabled()) repository.pruneExpired(90);
    }

    public Observation get(long id) {
        return repository.get(id).map(Observation::from)
                .orElseThrow(() -> new BusinessException(404, "校验版本不存在"));
    }

    public List<Issue> issues(long id, int after) {
        get(id);
        return repository.anomalies(id, after).stream().map(a ->
                new Issue(a.ordinal(), a.scanId(), a.status(), a.bytesRead(), a.sha256()!=null, a.checkedAt())).toList();
    }

    public Observation resume(long archiveId, long version, long actorId) {
        if (!enabled) throw new BusinessException(503,"病案完整性后台校验已关闭");
        if (readiness.isReadOnly() || maintenance.isEnabled()) throw new BusinessException(503,"系统处于只读模式");
        if (archiveId<=0 || version<=0 || actorId<=0) throw new BusinessException(400,"无效病案、版本或操作者");
        try {
            if (!repository.resume(archiveId,version,actorId))
                throw new BusinessException(409,"仅能恢复最新失败且成员完整的固定快照，或恢复次数已达上限/已有活动任务");
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessException(409,"已有活动校验任务或恢复冲突");
        }
        return get(version);
    }

    public Observation cancel(long id, long actorId) {
        if (actorId <= 0) throw new BusinessException(400, "无效操作者");
        if (!repository.cancel(id, actorId)) throw new BusinessException(409, "校验任务不存在或已结束");
        return get(id);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 10000)
    public void tick() {
        if (!enabled || readiness.isReadOnly() || maintenance.isEnabled()) return;
        var lease = repository.claim(UUID.randomUUID()).orElse(null);
        if (lease == null) return;
        try {
            var candidate = repository.next(lease).orElse(null);
            if (candidate == null) {
                if (lease.snapshotCount()!=null && lease.processed()<lease.snapshotCount()) {
                    // Missing persisted ordinal is a consistency error, never a completed observation.
                    repository.retry(lease,true);
                } else {
                    repository.finish(lease, "COMPLETED");
                }
                return;
            }
            if (lease.processed() >= lease.limit()) {
                repository.finish(lease, "PARTIAL");
                return;
            }
            if (lease.snapshotCount()!=null && !repository.matches(lease,candidate)) {
                repository.advance(lease,candidate,"STALE_SCAN",0,"");
                return;
            }
            var scan = mapper.findActiveById(candidate.id());
            if (scan == null || !Objects.equals(scan.getArchiveId(), lease.archiveId())) {
                repository.advance(lease, candidate, "STALE_SCAN", 0, "");
                return;
            }
            var verification = storage.verifyContent(PathDO.fromScan(scan), scan.getChecksumMd5());
            // A user-initiated single-image diagnostic may own the shared permit.
            if ("BUSY".equals(verification.status())) {
                repository.retry(lease, false);
            } else {
                repository.advance(lease, candidate, verification.status(), verification.bytesRead(), verification.sha256());
            }
        } catch (RuntimeException failure) {
            log.warn("Archive manifest observation failed: {}", failure.getClass().getSimpleName());
            repository.retry(lease, true);
        }
    }
}
