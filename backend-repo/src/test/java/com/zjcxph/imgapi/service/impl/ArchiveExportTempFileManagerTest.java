package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ArchiveExportTempFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void reservesQuotaAndBuildsFilesInsideTheControlledDirectory() throws Exception {
        ArchiveExportProperties properties = properties(10, 10);
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties);
        manager.initialize();

        try (ArchiveExportTempFileManager.Reservation reservation = manager.reserve("job-0001", 6, "zip")) {
            assertThat(reservation.path().normalize().startsWith(tempDir.normalize())).isTrue();
            assertThat(reservation.path().getFileName().toString()).isEqualTo("job-0001.zip");
            assertThat(reservation.reservedBytes()).isEqualTo(10);
            assertThatThrownBy(() -> manager.reserve("job-0002", 5, "pdf"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("临时文件配额不足");
        }
    }

    @Test
    void doesNotCountTheActiveTaskFileAgainAfterItsQuotaWasReserved() throws Exception {
        ArchiveExportProperties properties = properties(20, 10);
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties);
        manager.initialize();

        try (ArchiveExportTempFileManager.Reservation first = manager.reserve("job-0001", 6, "zip")) {
            try (OutputStream output = manager.openOutput(first)) {
                output.write(new byte[6]);
            }

            try (ArchiveExportTempFileManager.Reservation second = manager.reserve("job-0002", 5, "pdf")) {
                assertThat(second.reservedBytes()).isEqualTo(10);
            }
        }
    }

    @Test
    void cleanupCannotDeleteTheManagedRootOrAnOutsideFile() throws Exception {
        ArchiveExportProperties properties = properties(20, 10);
        properties.setTempDirectory(tempDir.resolve("managed").toString());
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties);
        manager.initialize();
        Path outside = Files.writeString(tempDir.resolve("synthetic-outside.zip"), "synthetic");

        assertThat(manager.deleteManagedFile(outside.toString())).isFalse();
        assertThat(manager.deleteManagedFile(properties.getTempDirectory())).isFalse();
        assertThat(outside).exists();
        assertThat(Path.of(properties.getTempDirectory())).isDirectory();
    }

    private ArchiveExportProperties properties(long maxTotalBytes, long maxFileBytes) {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setTempDirectory(tempDir.toString());
        properties.setMaxTotalBytes(maxTotalBytes);
        properties.setMaxFileBytes(maxFileBytes);
        properties.setRetention(Duration.ofHours(1));
        return properties;
    }

    @Test
    void refusesNewQuotaWhenAnExistingFileCannotBeMeasuredAndCanRetryAfterRecovery() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(10, 10));
        manager.initialize();
        Path existing = Files.write(tempDir.resolve("synthetic.zip"), new byte[1]).toRealPath();
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(existing)).thenThrow(new AccessDeniedException("synthetic-private-path"));
            files.when(() -> Files.readAttributes(existing, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS))
                    .thenThrow(new AccessDeniedException("synthetic-private-path"));
            assertThatThrownBy(manager::currentUsageBytes).isInstanceOf(IOException.class)
                    .hasMessage("无法核算导出临时目录占用").hasNoCause();
            assertThatThrownBy(() -> manager.reserve("job-0001", 1, "zip")).isInstanceOf(IOException.class)
                    .hasMessage("无法核算导出临时目录占用").hasNoCause();
        }
        Files.delete(existing);
        try (var reservation = manager.reserve("job-0002", 1, "zip")) {
            assertThat(reservation.reservedBytes()).isEqualTo(10);
        }
    }

    @Test
    void directoryAccessFailureIsNotAnEmptyDirectory() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(10, 10));
        manager.initialize();
        Path root = tempDir.toRealPath();
        try (var files = mockStatic(Files.class)) {
            files.when(() -> Files.exists(root)).thenReturn(false);
            files.when(() -> Files.list(root)).thenThrow(new AccessDeniedException("synthetic-private-path"));
            assertThatThrownBy(() -> manager.reserve("job-0001", 1, "zip")).isInstanceOf(IOException.class)
                    .hasMessage("无法核算导出临时目录占用").hasNoCause();
        }
    }

    @Test
    void directoryIterationFailureCannotPartiallyCountAndReserve() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(10, 10));
        manager.initialize();
        Path root = tempDir.toRealPath();
        Path first = Files.write(root.resolve("synthetic.zip"), new byte[1]);
        BasicFileAttributes attributes = Files.readAttributes(first, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        AtomicBoolean closed = new AtomicBoolean();
        try (var files = mockStatic(Files.class)) {
            files.when(() -> Files.exists(root)).thenReturn(true);
            files.when(() -> Files.isRegularFile(first)).thenReturn(true);
            files.when(() -> Files.size(first)).thenReturn(1L);
            files.when(() -> Files.readAttributes(first, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS))
                    .thenReturn(attributes);
            files.when(() -> Files.list(root)).thenAnswer(invocation -> Stream.concat(Stream.of(first),
                    Stream.<Path>generate(() -> {
                        throw new UncheckedIOException(new IOException("synthetic-private-path"));
                    }).limit(1)).onClose(() -> closed.set(true)));
            assertThatThrownBy(() -> manager.reserve("job-0001", 1, "zip")).isInstanceOf(IOException.class)
                    .hasMessage("无法核算导出临时目录占用").hasNoCause();
            assertThat(closed).isTrue();
        }
        Files.delete(first);
        try (var reservation = manager.reserve("job-0002", 1, "zip")) {
            assertThat(reservation.reservedBytes()).isEqualTo(10);
        }
    }

    @Test
    void aFileRemovedByCleanupDoesNotPreventQuotaCalculation() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(10, 10));
        manager.initialize();
        Path vanished = Files.write(tempDir.resolve("synthetic.zip"), new byte[1]).toRealPath();
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(vanished)).thenThrow(new NoSuchFileException("synthetic-removed"));
            files.when(() -> Files.readAttributes(vanished, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS))
                    .thenThrow(new NoSuchFileException("synthetic-removed"));
            try (var reservation = manager.reserve("job-0001", 1, "zip")) {
                assertThat(reservation.reservedBytes()).isEqualTo(10);
            }
        }
    }

    @Test
    void overflowingUsageCannotWrapAndAllowNewQuota() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(10, 10));
        manager.initialize();
        Path first = Files.createFile(tempDir.resolve("synthetic-first.zip")).toRealPath();
        Path second = Files.createFile(tempDir.resolve("synthetic-second.zip")).toRealPath();
        BasicFileAttributes huge = mock(BasicFileAttributes.class);
        BasicFileAttributes small = mock(BasicFileAttributes.class);
        when(huge.isRegularFile()).thenReturn(true);
        when(huge.size()).thenReturn(Long.MAX_VALUE);
        when(small.isRegularFile()).thenReturn(true);
        when(small.size()).thenReturn(1L);
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(first)).thenReturn(Long.MAX_VALUE);
            files.when(() -> Files.size(second)).thenReturn(1L);
            files.when(() -> Files.readAttributes(first, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)).thenReturn(huge);
            files.when(() -> Files.readAttributes(second, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)).thenReturn(small);
            assertThat(manager.currentUsageBytes()).isEqualTo(Long.MAX_VALUE);
            assertThatThrownBy(() -> manager.reserve("job-0001", 1, "zip"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("临时文件配额不足");
        }
    }

    @Test
    void countsOnlyImmediateRegularFilesWithOneMetadataRead() throws Exception {
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties(20, 10));
        manager.initialize();
        Path file = Files.write(tempDir.resolve("synthetic.zip"), new byte[3]).toRealPath();
        Path nested = Files.createDirectory(tempDir.resolve("synthetic-subdir"));
        Files.write(nested.resolve("synthetic-nested.zip"), new byte[15]);
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            assertThat(manager.currentUsageBytes()).isEqualTo(3);
            files.verify(() -> Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS), times(1));
            files.verify(() -> Files.size(file), never());
        }
    }
}
