package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchiveExportJobPolicyTest {

    @Test
    void routesByItemCountEstimatedBytesAndSourceCount() {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setAsyncItemThreshold(3);
        properties.setAsyncEstimatedBytesThreshold(1_000);
        properties.setAsyncSourceCountThreshold(2);
        properties.setFallbackBytesPerImage(600);
        ArchiveExportJobService service = new ArchiveExportJobService(
                mock(ArchiveExportService.class),
                mock(ArchiveExportJobRepository.class),
                mock(ArchiveExportTempFileManager.class),
                properties,
                (Executor) Runnable::run);

        assertThat(service.shouldUseJob(export(3, 1, "LOCAL"))).isTrue();
        assertThat(service.shouldUseJob(export(1, 1_000, "LOCAL"))).isTrue();

        PathDO local = item(1, 10, "LOCAL");
        PathDO oss = item(2, 10, "OSS");
        oss.setSourceRef("25.03/page-2.jpg");
        assertThat(service.shouldUseJob(new ArchiveExportService.BatchZipExport(List.of(local, oss)))).isTrue();
        assertThat(service.shouldUseJob(export(2, 10, "LOCAL"))).isFalse();

        PathDO known = item(3, 10, "LOCAL");
        PathDO unknown = item(4, 0, "LOCAL");
        unknown.setFileSize(null);
        assertThat(service.shouldUseJob(new ArchiveExportService.BatchZipExport(List.of(known, unknown)))).isTrue();
    }

    @Test
    void treatsOssRowsWithoutObjectKeysAsLocalFallbackWithinTheSameArchive() {
        PathDO migrated = item(1, 10, "OSS");
        migrated.setSourceRef("25.03/page-1.jpg");
        PathDO notMigrated = item(2, 10, "OSS");

        ArchiveExportService.BatchZipExport export =
                new ArchiveExportService.BatchZipExport(List.of(migrated, notMigrated));

        assertThat(export.sourceSummary()).containsExactly("OSS", "LOCAL");
    }

    private ArchiveExportService.BatchZipExport export(int count, long bytes, String source) {
        return new ArchiveExportService.BatchZipExport(
                IntStream.range(0, count).mapToObj(index -> item(index + 1, bytes, source)).toList());
    }

    private PathDO item(int id, long bytes, String source) {
        PathDO item = new PathDO("25.03.15", "page-" + id + ".jpg", "605746", "00789508");
        item.setScanId(id);
        item.setFileSize(bytes);
        item.setSourceType(source);
        return item;
    }
}
