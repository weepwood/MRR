package com.zjcxph.imgapi.service.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.OssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.HexFormat;
import java.util.Map;

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
        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();
        if (accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()) {
            logger.warn("OSS credentials are not configured. OSS operations will fail until configured.");
            return;
        }

        if (accessKeyId.startsWith("\"") || accessKeyId.endsWith("\"")) {
            throw new IllegalStateException("Invalid OSS Access Key ID format: contains quotes");
        }
        if (accessKeySecret.startsWith("\"") || accessKeySecret.endsWith("\"")) {
            throw new IllegalStateException("Invalid OSS Access Key Secret format: contains quotes");
        }

        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId, accessKeySecret);
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setConnectionTimeout(ossProperties.getConnectionTimeoutMs());
        clientConfig.setSocketTimeout(ossProperties.getSocketTimeoutMs());
        clientConfig.setMaxConnections(Math.max(1, ossProperties.getMaxConnections()));
        clientConfig.setMaxErrorRetry(Math.max(0, ossProperties.getMaxErrorRetry()));

        String endpoint = ossProperties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("OSS endpoint is not configured");
        }
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
                ? accessKeyId.substring(0, 4) + "****" + accessKeyId.substring(accessKeyId.length() - 4)
                : "****";
        logger.info("OSS client initialized: endpoint={}, bucket={}, maxConnections={}, accessKeyId={}",
                ossProperties.getEndpoint(), ossProperties.getBucket(),
                ossProperties.getMaxConnections(), maskedKeyId);
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            try {
                s3Client.shutdown();
                logger.info("OSS client shut down");
            } catch (Exception e) {
                logger.warn("Error shutting down OSS client", e);
            }
        }
    }

    private void ensureClient() {
        if (s3Client == null) {
            throw new IllegalStateException("OSS client is not initialized. Please configure OSS credentials.");
        }
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey, String checksumMd5) {
        ensureClient();
        File file = new File(localFilePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Local file does not exist: " + localFilePath);
        }

        String localMd5 = checksumMd5;
        if (localMd5 == null || localMd5.isBlank()) {
            localMd5 = calculateMd5(localFilePath);
        }
        localMd5 = localMd5.toLowerCase();

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());
            metadata.setContentType(resolveContentType(file.getName()));
            metadata.setContentMD5(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(localMd5)));
            metadata.addUserMetadata(SOURCE_MD5_METADATA, localMd5);

            PutObjectRequest putRequest = new PutObjectRequest(
                    ossProperties.getBucket(), ossKey, file).withMetadata(metadata);
            PutObjectResult result = s3Client.putObject(putRequest);

            String etag = normalizeEtag(result.getETag());
            if (etag != null && !etag.contains("-") && !etag.equalsIgnoreCase(localMd5)) {
                logger.warn("OSS ETag differs from source MD5 for key={}: source={}, etag={}",
                        ossKey, localMd5, etag);
            }

            logger.debug("Uploaded file to OSS: {} -> {}", localFilePath, ossKey);
            return ossKey;
        } catch (Exception e) {
            logger.error("Failed to upload file to OSS: {} -> {}", localFilePath, ossKey, e);
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(value = "ossSignedUrl", key = "#ossKey", unless = "#result == null")
    public String generatePresignedUrl(String ossKey) {
        ensureClient();
        try {
            Date expiration = new Date(System.currentTimeMillis()
                    + (long) ossProperties.getUrlExpireSeconds() * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossProperties.getBucket(), ossKey).withExpiration(expiration);
            URL url = s3Client.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            logger.error("Failed to generate presigned URL for: {}", ossKey, e);
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String calculateMd5(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return DigestUtils.md5Hex(fis);
        } catch (IOException e) {
            logger.error("Failed to calculate MD5 for: {}", filePath, e);
            throw new RuntimeException("MD5 calculation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesObjectExist(String ossKey) {
        ensureClient();
        try {
            return s3Client.doesObjectExist(ossProperties.getBucket(), ossKey);
        } catch (Exception e) {
            logger.error("Failed to check object existence: {}", ossKey, e);
            return false;
        }
    }

    @Override
    public boolean isObjectEquivalent(String ossKey, long expectedSize, String expectedMd5) {
        ensureClient();
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(ossProperties.getBucket(), ossKey);
            if (metadata.getContentLength() != expectedSize) {
                return false;
            }

            Map<String, String> userMetadata = metadata.getUserMetadata();
            String storedSourceMd5 = userMetadata == null ? null : userMetadata.get(SOURCE_MD5_METADATA);
            if (storedSourceMd5 != null && expectedMd5 != null) {
                return storedSourceMd5.equalsIgnoreCase(expectedMd5);
            }

            String etag = normalizeEtag(metadata.getETag());
            return etag != null && !etag.contains("-")
                    && expectedMd5 != null && etag.equalsIgnoreCase(expectedMd5);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Failed to inspect OSS object: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inspect OSS object: " + e.getMessage(), e);
        }
    }

    @Override
    @CacheEvict(value = "ossSignedUrl", key = "#ossKey")
    public void deleteObject(String ossKey) {
        ensureClient();
        try {
            s3Client.deleteObject(ossProperties.getBucket(), ossKey);
            logger.info("Deleted from OSS: {}", ossKey);
        } catch (Exception e) {
            logger.error("Failed to delete object from OSS: {}", ossKey, e);
            throw new RuntimeException("OSS delete failed: " + e.getMessage(), e);
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
            logger.warn("Cannot verify integrity: expected MD5 is empty for {}", ossKey);
            return false;
        }

        try {
            S3Object s3Object = s3Client.getObject(ossProperties.getBucket(), ossKey);
            try (InputStream inputStream = s3Object.getObjectContent()) {
                String actualMd5 = DigestUtils.md5Hex(inputStream);
                boolean match = actualMd5.equalsIgnoreCase(expectedMd5);
                if (!match) {
                    logger.error("Integrity verification failed for {}: expected={}, actual={}",
                            ossKey, expectedMd5, actualMd5);
                }
                return match;
            }
        } catch (Exception e) {
            logger.error("Failed to verify upload integrity for {}", ossKey, e);
            return false;
        }
    }

    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }

    private String normalizeEtag(String etag) {
        return etag == null ? null : etag.replace("\"", "").trim();
    }
}
