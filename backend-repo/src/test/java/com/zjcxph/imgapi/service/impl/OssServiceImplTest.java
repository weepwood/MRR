package com.zjcxph.imgapi.service.impl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.zjcxph.imgapi.config.OssProperties;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssServiceImplTest {

    @TempDir
    Path tempDir;

    private AmazonS3 s3Client;
    private OssServiceImpl service;

    @BeforeEach
    void setUp() {
        OssProperties properties = new OssProperties();
        properties.setBucket("test-bucket");
        properties.setUrlExpireSeconds(3600);

        s3Client = mock(AmazonS3.class);
        service = spy(new OssServiceImpl(properties));
        ReflectionTestUtils.setField(service, "s3Client", s3Client);
    }

    @Test
    void uploadUsesProvidedChecksumWithoutReadingFileForMd5Again() throws Exception {
        byte[] content = "medical-record-image".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("0001.jpg");
        Files.write(file, content);
        String checksum = DigestUtils.md5Hex(content);

        PutObjectResult putResult = new PutObjectResult();
        putResult.setETag(checksum);
        when(s3Client.putObject(any(PutObjectRequest.class))).thenReturn(putResult);

        String key = service.uploadFile(file.toString(), "medical-records/0001.jpg", checksum);

        assertEquals("medical-records/0001.jpg", key);
        verify(service, never()).calculateMd5(file.toString());

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture());
        ObjectMetadata metadata = requestCaptor.getValue().getMetadata();
        assertEquals(content.length, metadata.getContentLength());
        assertEquals(checksum, metadata.getUserMetadata().get("source-md5"));
        assertNotNull(metadata.getContentMD5());
    }

    @Test
    void existingObjectIsEquivalentOnlyWhenSizeAndChecksumMatch() {
        String key = "medical-records/0001.jpg";
        String checksum = "80d2b6ccdab9bb887bf6f6255d5aa8fa";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(1024L);
        metadata.addUserMetadata("source-md5", checksum);
        when(s3Client.getObjectMetadata(eq("test-bucket"), eq(key))).thenReturn(metadata);

        assertTrue(service.isObjectEquivalent(key, 1024L, checksum));
        assertFalse(service.isObjectEquivalent(key, 2048L, checksum));
        assertFalse(service.isObjectEquivalent(key, 1024L, "00000000000000000000000000000000"));
    }
}
