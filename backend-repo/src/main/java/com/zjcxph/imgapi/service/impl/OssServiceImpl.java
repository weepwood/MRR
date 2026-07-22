package com.zjcxph.imgapi.service.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.OssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

@Service
public class OssServiceImpl implements OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssServiceImpl.class);
    private static final String SOURCE_MD5_METADATA = "source-md5";

    private final OssProperties ossProperties;
    private AmazonS3 s3Client;

    public OssServiceImpl(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @PostConstruct
    public void init() {
        if (ossProperties.getAccessKeyId() == null || ossProperties.getAccessKeyId().isBlank()) {
            logger.warn("OSS accessKeyId is not configured. OSS operations will fail until configured.");
            return;
        }

        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();
        if (accessKeySecret == null || accessKeySecret.isBlank()) {
            logger.warn("OSS accessKeySecret is not configured. OSS operations will fail until configured.");
            return;
        }

        if (accessKeyId.startsWith("\"") || accessKeyId.endsWith("\"")) {
            logger.error("OSS Access Key ID contains quotes. Remove quotes from configuration.");
            throw new IllegalStateException("Invalid OSS Access Key ID format: contains quotes");
        }

        if (accessKeySecret.startsWith("\"") || accessKeySecret.endsWith("\"")) {
            logger.error("OSS Access Key Secret contains quotes. Remove quotes from configuration.");
            throw new IllegalStateException("Invalid OSS Access Key Secret format: contains quotes");
        }

        try {
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId, accessKeySecret);

            ClientConfiguration clientConfig = new ClientConfiguration();
            clientConfig.setConnectionTimeout(30_000);
            clientConfig.setSocketTimeout(60_000);
            clientConfig.setMaxConnections(50);

            String endpoint = ossProperties.getEndpoint();
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                endpoint = "https://" + endpoint;
            }

            s3Client = AmazonS3ClientBuilder.standard()
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(endpoint, ossProperties.getRegion()))
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withClientConfiguration(clientConfig)
                    .withPathStyleAccessEnabled(false)
                    .build();

            String maskedKeyId = accessKeyId.length() > 8
                    ? accessKeyId.substring(0, 4)
                    + "****"
                    + accessKeyId.substring(accessKeyId.length() - 4)
                    : "****";
            logger.info(
                    "OSS client initialized successfully: endpoint={}, bucket={}, accessKeyId={}",
                    ossProperties.getEndpoint(),
                    ossProperties.getBucket(),
                    maskedKeyId
            );
        } catch (Exception exception) {
            logger.error("Failed to initialize OSS client", exception);
        }
    }

    @PreDestroy
    public void destroy() {
        if (s3Client == null) {
            return;
        }
        try {
            s3Client.shutdown();
            logger.info("OSS client shut down");
        } catch (Exception exception) {
            logger.warn("Error shutting down OSS client", exception);
        }
    }

    private void ensureClient() {
        if (s3Client == null) {
            throw new IllegalStateException("OSS client is not initialized. Please configure OSS credentials.");
        }
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey) {
        return uploadFile(localFilePath, ossKey, calculateMd5(localFilePath));
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey, String sourceMd5) {
        ensureClient();
        File file = new File(localFilePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Local file does not exist: " + localFilePath);
        }
        if (sourceMd5 == null || !sourceMd5.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Source MD5 must be a 32-character hexadecimal value");
        }

        try {
            logger.debug("Local file MD5: {} for {}", sourceMd5, localFilePath);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());
            metadata.setContentMD5(
                    Base64.getEncoder().encodeToString(Hex.decodeHex(sourceMd5.toCharArray()))
            );
            metadata.addUserMetadata(SOURCE_MD5_METADATA, sourceMd5);
            metadata.setContentType(resolveContentType(file.getName()));

            PutObjectRequest putRequest = new PutObjectRequest(
                    ossProperties.getBucket(),
                    ossKey,
                    file
            ).withMetadata(metadata);

            PutObjectResult result = s3Client.putObject(putRequest);
            String etag = result.getETag();
            if (etag != null) {
                etag = etag.replace("\"", "").trim();
                if (etag.equalsIgnoreCase(sourceMd5)) {
                    logger.info("Upload verified by ETag for {} -> {}", localFilePath, ossKey);
                } else {
                    logger.info(
                            "Upload completed with server-side Content-MD5 validation: {} -> {}, ETag={}",
                            localFilePath,
                            ossKey,
                            etag
                    );
                }
            }

            logger.info("Uploaded to OSS: {} -> {}", localFilePath, ossKey);
            return ossKey;
        } catch (Exception exception) {
            logger.error("Failed to upload file to OSS: {} -> {}", localFilePath, ossKey, exception);
            throw new RuntimeException("OSS upload failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Cacheable(value = "ossSignedUrl", key = "#ossKey", unless = "#result == null")
    public String generatePresignedUrl(String ossKey) {
        ensureClient();
        try {
            Date expiration = new Date(
                    System.currentTimeMillis() + (long) ossProperties.getUrlExpireSeconds() * 1000
            );
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossProperties.getBucket(),
                    ossKey
            ).withExpiration(expiration);
            URL url = s3Client.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception exception) {
            logger.error("Failed to generate presigned URL for: {}", ossKey, exception);
            throw new RuntimeException(
                    "Failed to generate presigned URL: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    public String calculateMd5(String filePath) {
        try (FileInputStream input = new FileInputStream(filePath)) {
            return DigestUtils.md5Hex(input);
        } catch (IOException exception) {
            logger.error("Failed to calculate MD5 for: {}", filePath, exception);
            throw new RuntimeException("MD5 calculation failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean doesObjectExist(String ossKey) {
        ensureClient();
        try {
            return s3Client.doesObjectExist(ossProperties.getBucket(), ossKey);
        } catch (Exception exception) {
            logger.error("Failed to check object existence: {}", ossKey, exception);
            throw new RuntimeException(
                    "OSS object existence check failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    @CacheEvict(value = "ossSignedUrl", key = "#ossKey")
    public void deleteObject(String ossKey) {
        ensureClient();
        try {
            s3Client.deleteObject(ossProperties.getBucket(), ossKey);
            logger.info("Deleted from OSS: {}", ossKey);
        } catch (Exception exception) {
            logger.error("Failed to delete object: {}", ossKey, exception);
            throw new RuntimeException("OSS delete failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public long getFileSize(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        return file.length();
    }

    @Override
    public boolean verifyUploadIntegrity(String ossKey, String expectedMd5) {
        ensureClient();
        if (expectedMd5 == null || expectedMd5.isBlank()) {
            throw new IllegalArgumentException("Expected MD5 is empty for " + ossKey);
        }

        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(ossProperties.getBucket(), ossKey);
            String storedSourceMd5 = metadata.getUserMetadata().get(SOURCE_MD5_METADATA);
            if (storedSourceMd5 != null && !storedSourceMd5.isBlank()) {
                boolean matches = storedSourceMd5.equalsIgnoreCase(expectedMd5);
                if (!matches) {
                    logger.error(
                            "Integrity metadata mismatch for {}. Expected: {}, stored: {}",
                            ossKey,
                            expectedMd5,
                            storedSourceMd5
                    );
                }
                return matches;
            }

            S3Object s3Object = s3Client.getObject(ossProperties.getBucket(), ossKey);
            try (InputStream inputStream = s3Object.getObjectContent()) {
                String actualMd5 = DigestUtils.md5Hex(inputStream);
                boolean matches = actualMd5.equalsIgnoreCase(expectedMd5);
                if (!matches) {
                    logger.error(
                            "Integrity verification failed for {}. Expected: {}, actual: {}",
                            ossKey,
                            expectedMd5,
                            actualMd5
                    );
                }
                return matches;
            }
        } catch (Exception exception) {
            logger.error("Failed to verify upload integrity for {}", ossKey, exception);
            throw new RuntimeException(
                    "OSS integrity verification failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private String resolveContentType(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalized.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (normalized.endsWith(".tif") || normalized.endsWith(".tiff")) {
            return "image/tiff";
        }
        return "application/octet-stream";
    }
}
