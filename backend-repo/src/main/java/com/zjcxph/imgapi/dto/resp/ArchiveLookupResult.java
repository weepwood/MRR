package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.entity.Scan;

import java.util.List;
import java.util.Objects;

/**
 * 影像档案查询结果及其查询策略元数据。
 *
 * <p>图片明细最终均来自 {@code mr_scan}。该对象记录的是查询策略：
 * 是否通过 {@code mr_archive -> archive_id} 快速定位，或回退到旧 BAH/SJH 查询。</p>
 */
public record ArchiveLookupResult(
        List<Scan> scans,
        Strategy strategy,
        FallbackReason fallbackReason,
        Long archiveId
) {

    public ArchiveLookupResult {
        scans = scans == null ? List.of() : List.copyOf(scans);
        strategy = Objects.requireNonNull(strategy, "strategy");
        fallbackReason = Objects.requireNonNull(fallbackReason, "fallbackReason");
    }

    public static ArchiveLookupResult fastPath(
            List<Scan> scans,
            Strategy strategy,
            Long archiveId
    ) {
        if (strategy != Strategy.ARCHIVE_ID_EXACT && strategy != Strategy.ARCHIVE_ID_COMPAT) {
            throw new IllegalArgumentException("fast path requires an archive_id strategy");
        }
        return new ArchiveLookupResult(scans, strategy, FallbackReason.NONE, archiveId);
    }

    public static ArchiveLookupResult fallback(
            List<Scan> scans,
            FallbackReason fallbackReason,
            Long archiveId
    ) {
        return new ArchiveLookupResult(scans, Strategy.MR_SCAN_FALLBACK, fallbackReason, archiveId);
    }

    public static ArchiveLookupResult notFound(
            FallbackReason fallbackReason,
            Long archiveId
    ) {
        return new ArchiveLookupResult(List.of(), Strategy.NOT_FOUND, fallbackReason, archiveId);
    }

    public int resultCount() {
        return scans.size();
    }

    public enum Strategy {
        ARCHIVE_ID_EXACT,
        ARCHIVE_ID_COMPAT,
        MR_SCAN_FALLBACK,
        NOT_FOUND
    }

    public enum FallbackReason {
        NONE,
        ARCHIVE_NOT_FOUND,
        ARCHIVE_HAS_NO_LINKED_SCANS
    }
}
