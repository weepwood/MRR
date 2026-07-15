package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTransferStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesCsvUploadInsideConfiguredJobDirectory() throws Exception {
        DataTransferProperties properties = properties();
        DataTransferStorageService storage = new DataTransferStorageService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "statistics.csv",
                "text/csv",
                "bah,sjh\n00000001,00000002\n".getBytes(StandardCharsets.UTF_8)
        );

        DataTransferStorageService.StoredFile stored = storage.storeUpload(10L, 1, file);

        assertEquals("statistics.csv", stored.originalFilename());
        assertTrue(Files.isRegularFile(stored.path()));
        assertTrue(stored.path().startsWith(Path.of(properties.getBaseDir()).toAbsolutePath().normalize()));
        assertEquals(64, stored.sha256().length());
    }

    @Test
    void rejectsUnsupportedUploadExtension() {
        DataTransferStorageService storage = new DataTransferStorageService(properties());
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "payload.exe",
                "application/octet-stream",
                new byte[] { 1, 2, 3 }
        );

        assertThrows(BusinessException.class, () -> storage.storeUpload(1L, 1, file));
    }

    @Test
    void onlyRegistersFilesFromConfiguredInbox() throws Exception {
        DataTransferProperties properties = properties();
        Path inbox = Path.of(properties.getInboxDir());
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("scan.csv"), "sjh,bah\n1,2\n", StandardCharsets.UTF_8);
        DataTransferStorageService storage = new DataTransferStorageService(properties);

        DataTransferStorageService.StoredFile stored = storage.registerInboxFile(20L, 1, "scan.csv");

        assertTrue(Files.isRegularFile(stored.path()));
        assertEquals(1, storage.listInboxFiles().size());
        assertThrows(BusinessException.class, () -> storage.registerInboxFile(20L, 2, "missing.csv"));
    }

    private DataTransferProperties properties() {
        DataTransferProperties properties = new DataTransferProperties();
        properties.setBaseDir(tempDir.resolve("transfer").toString());
        properties.setInboxDir(tempDir.resolve("inbox").toString());
        properties.setWebUploadMaxBytes(1024 * 1024);
        return properties;
    }
}
