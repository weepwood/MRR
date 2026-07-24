package com.zjcxph.imgapi.service.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.dto.resp.OssBrowserEntryDTO;
import com.zjcxph.imgapi.dto.resp.OssBrowserPageDTO;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class OssServiceImpl implements OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssServiceImpl.class);
    private static final String SOURCE_MD5_METADATA = "source-md5";
    private static final String BROWSER_ROOT_PREFIX = "medical-records/";
    private static final int DEFAULT_BROWSER_MAX_KEYS = 200;
    private static final int MAX_BROWSER_MAX_KEYS = 500;
    private static final int MAX_CONTINUATION_TOKEN_LENGTH = 8_192;

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
                    .withPathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                    .build();

            String maskedKeyId = accessKeyId.length() > 8
                    ? accessKeyId.substring(0, 4)
                    + "****"
                    + accessKeyId.substring(accessKeyId.length() - 4)
                    : "****";
            logger.info(
                    "OSS client initialized successfully: endpoint={}, bucket={}, pathStyleAccess={}, accessKeyId={}",
                    ossProperties.getEndpoint(),
                    ossProperties.getBucket(),
                    ossProperties.isPathStyleAccess(),
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
        return createPresignedUrl(ossKey);
    }

    @Override
    public OssBrowserPageDTO browseObjects(String prefix, String continuationToken, int maxKeys) {
        int safeMaxKeys = normalizeBrowserMaxKeys(maxKeys);
        String safePrefix = normalizeBrowserPrefix(prefix);
        String safeContinuationToken = normalizeContinuationToken(continuationToken);

        if (s3Client == null) {
            return browserPage(false, safePrefix, safeMaxKeys, List.of(), null, false);
        }

        try {
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(ossProperties.getBucket())
                    .withPrefix(safePrefix)
                    .withDelimiter("/")
                    .withMaxKeys(safeMaxKeys);
            if (safeContinuationToken != null) {
                request.setContinuationToken(safeContinuationToken);
            }

            ListObjectsV2Result result = s3Client.listObjectsV2(request);
            List<OssBrowserEntryDTO> entries = new ArrayList<>();

            List<String> commonPrefixes = result.getCommonPrefixes();
            if (commonPrefixes != null) {
                for (String commonPrefix : commonPrefixes) {
                    String name = browserEntryName(safePrefix, commonPrefix, true);
                    if (!name.isBlank()) {
                        entries.add(OssBrowserEntryDTO.builder()
                                .name(name)
                                .key(commonPrefix)
                                .directory(true)
                                .size(0)
                                .build());
                    }
                }
            }

            List<S3ObjectSummary> objectSummaries = result.getObjectSummaries();
            if (objectSummaries != null) {
                for (S3ObjectSummary summary : objectSummaries) {
                    if (summary.getKey() == null || summary.getKey().equals(safePrefix)) {
                        continue;
                    }
                    String name = browserEntryName(safePrefix, summary.getKey(), false);
                    if (name.isBlank()) {
                        continue;
                    }
                    entries.add(OssBrowserEntryDTO.builder()
                            .name(name)
                            .key(summary.getKey())
                            .directory(false)
                            .size(Math.max(0, summary.getSize()))
                            .lastModified(summary.getLastModified() == null
                                    ? null
                                    : summary.getLastModified().toInstant())
                            .etag(summary.getETag())
                            .storageClass(summary.getStorageClass())
                            .build());
                }
            }

            entries.sort(Comparator
                    .comparing(OssBrowserEntryDTO::isDirectory).reversed()
                    .thenComparing(OssBrowserEntryDTO::getName, String.CASE_INSENSITIVE_ORDER));

            return browserPage(
                    true,
                    safePrefix,
                    safeMaxKeys,
                    entries,
                    result.getNextContinuationToken(),
                    result.isTruncated()
            );
        } catch (Exception exception) {
            logger.error("Failed to browse OSS prefix: {}", safePrefix, exception);
            throw new RuntimeException("OSS directory listing failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Cacheable(value = "ossSignedUrl", key = "#ossKey", unless = "#result == null")
    public String generateBrowserPresignedUrl(String ossKey) {
        ensureClient();
        return createPresignedUrl(normalizeBrowserObjectKey(ossKey));
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

    private OssBrowserPageDTO browserPage(boolean configured,
                                          String prefix,
                                          int maxKeys,
                                          List<OssBrowserEntryDTO> entries,
                                          String nextContinuationToken,
                                          boolean truncated) {
        int loadedDirectories = 0;
        int loadedFiles = 0;
        long loadedBytes = 0;
        for (OssBrowserEntryDTO entry : entries) {
            if (entry.isDirectory()) {
                loadedDirectories++;
            } else {
                loadedFiles++;
                loadedBytes += Math.max(0, entry.getSize());
            }
        }
        return OssBrowserPageDTO.builder()
                .configured(configured)
                .bucket(ossProperties.getBucket())
                .endpoint(ossProperties.getEndpoint())
                .region(ossProperties.getRegion())
                .rootPrefix(BROWSER_ROOT_PREFIX)
                .prefix(prefix)
                .entries(entries)
                .nextContinuationToken(nextContinuationToken)
                .truncated(truncated)
                .maxKeys(maxKeys)
                .loadedDirectories(loadedDirectories)
                .loadedFiles(loadedFiles)
                .loadedBytes(loadedBytes)
                .build();
    }

    private String createPresignedUrl(String ossKey) {
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

    private int normalizeBrowserMaxKeys(int maxKeys) {
        if (maxKeys <= 0) {
            return DEFAULT_BROWSER_MAX_KEYS;
        }
        return Math.min(maxKeys, MAX_BROWSER_MAX_KEYS);
    }

    private String normalizeBrowserPrefix(String prefix) {
        String normalized = prefix == null || prefix.isBlank() ? BROWSER_ROOT_PREFIX : prefix.trim();
        validateManagedPath(normalized);
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private String normalizeBrowserObjectKey(String ossKey) {
        if (ossKey == null || ossKey.isBlank()) {
            throw new IllegalArgumentException("OSS Object Key 不能为空");
        }
        String normalized = ossKey.trim();
        validateManagedPath(normalized);
        if (normalized.endsWith("/")) {
            throw new IllegalArgumentException("目录不能生成文件预览地址");
        }
        return normalized;
    }

    private void validateManagedPath(String value) {
        if (!value.startsWith(BROWSER_ROOT_PREFIX)) {
            throw new IllegalArgumentException("只允许浏览 " + BROWSER_ROOT_PREFIX + " 下的迁移文件");
        }
        if (value.indexOf('\0') >= 0 || value.contains("\\")) {
            throw new IllegalArgumentException("OSS 路径包含非法字符");
        }
        for (String segment : value.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("OSS 路径不能包含相对路径片段");
            }
        }
    }

    private String normalizeContinuationToken(String continuationToken) {
        if (continuationToken == null || continuationToken.isBlank()) {
            return null;
        }
        String normalized = continuationToken.trim();
        if (normalized.length() > MAX_CONTINUATION_TOKEN_LENGTH) {
            throw new IllegalArgumentException("OSS 分页游标过长");
        }
        return normalized;
    }

    private String browserEntryName(String currentPrefix, String key, boolean directory) {
        if (key == null || !key.startsWith(currentPrefix)) {
            return "";
        }
        String relative = key.substring(currentPrefix.length());
        if (directory && relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        int slashIndex = relative.indexOf('/');
        return slashIndex >= 0 ? relative.substring(0, slashIndex) : relative;
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
