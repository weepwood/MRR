package com.zjcxph.imgapi.service.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
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
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OssServiceImpl implements OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssServiceImpl.class);
    private static final String SOURCE_MD5_METADATA = "source-md5";
    private static final String BROWSER_ROOT_PREFIX = "medical-records/";
    private static final int DEFAULT_BROWSER_MAX_KEYS = 200;
    private static final int MAX_BROWSER_MAX_KEYS = 500;
    private static final int MAX_CONTINUATION_TOKEN_LENGTH = 8_192;

    private final OssProperties ossProperties;
    private S3Client s3Client;
    private S3Presigner s3Presigner;

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
            String endpoint = ossProperties.getEndpoint();
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                endpoint = "https://" + endpoint;
            }

            S3Configuration s3Configuration = S3Configuration.builder()
                    .pathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                    .build();
            URI endpointUri = URI.create(endpoint);
            Region region = Region.of(ossProperties.getRegion());
            StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, accessKeySecret));
            S3Client builtClient = S3Client.builder()
                    .endpointOverride(endpointUri)
                    .region(region)
                    .credentialsProvider(credentials)
                    .httpClientBuilder(ApacheHttpClient.builder()
                            .connectionTimeout(Duration.ofSeconds(30))
                            .socketTimeout(Duration.ofSeconds(60))
                            .maxConnections(50))
                    .serviceConfiguration(s3Configuration)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                    .build();
            try {
                s3Presigner = S3Presigner.builder()
                        .endpointOverride(endpointUri)
                        .region(region)
                        .credentialsProvider(credentials)
                        .serviceConfiguration(s3Configuration)
                        .build();
                s3Client = builtClient;
            } catch (RuntimeException exception) {
                builtClient.close();
                throw exception;
            }

            logger.info(
                    "OSS client initialized successfully: pathStyleAccess={}",
                    ossProperties.isPathStyleAccess()
            );
        } catch (Exception exception) {
            logger.error("Failed to initialize OSS client: {}", exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void destroy() {
        if (s3Client == null) {
            return;
        }
        try {
            if (s3Presigner != null) {
                s3Presigner.close();
            }
            s3Client.close();
            logger.info("OSS client shut down");
        } catch (Exception exception) {
            logger.warn("Error shutting down OSS client: {}", exception.getClass().getSimpleName());
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
            throw new IllegalArgumentException("Local file does not exist");
        }
        if (sourceMd5 == null || !sourceMd5.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Source MD5 must be a 32-character hexadecimal value");
        }

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(ossProperties.getBucket())
                    .key(ossKey)
                    .contentLength(file.length())
                    .contentMD5(Base64.getEncoder().encodeToString(Hex.decodeHex(sourceMd5.toCharArray())))
                    .metadata(Map.of(SOURCE_MD5_METADATA, sourceMd5))
                    .contentType(resolveContentType(file.getName()))
                    .build();

            PutObjectResponse result = s3Client.putObject(putRequest, RequestBody.fromFile(file));
            String etag = result.eTag();
            if (etag != null) {
                etag = etag.replace("\"", "").trim();
                if (etag.equalsIgnoreCase(sourceMd5)) {
                    logger.info("OSS upload verified by ETag");
                } else {
                    logger.info("OSS upload completed with Content-MD5 validation");
                }
            }

            logger.info("OSS upload completed");
            return ossKey;
        } catch (Exception exception) {
            logger.error("OSS upload failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS upload failed", exception);
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
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(ossProperties.getBucket())
                    .prefix(safePrefix)
                    .delimiter("/")
                    .maxKeys(safeMaxKeys)
                    .continuationToken(safeContinuationToken)
                    .build();

            ListObjectsV2Response result = s3Client.listObjectsV2(request);
            List<OssBrowserEntryDTO> entries = new ArrayList<>();

            if (result.commonPrefixes() != null) {
                for (var prefixEntry : result.commonPrefixes()) {
                    String commonPrefix = prefixEntry.prefix();
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

            List<S3Object> objectSummaries = result.contents();
            if (objectSummaries != null) {
                for (S3Object summary : objectSummaries) {
                    if (summary.key() == null || summary.key().equals(safePrefix)) {
                        continue;
                    }
                    String name = browserEntryName(safePrefix, summary.key(), false);
                    if (name.isBlank()) {
                        continue;
                    }
                    entries.add(OssBrowserEntryDTO.builder()
                            .name(name)
                            .key(summary.key())
                            .directory(false)
                            .size(summary.size() == null ? 0 : Math.max(0, summary.size()))
                            .lastModified(summary.lastModified() == null
                                    ? null
                                    : summary.lastModified())
                            .etag(summary.eTag())
                            .storageClass(summary.storageClassAsString())
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
                    result.nextContinuationToken(),
                    Boolean.TRUE.equals(result.isTruncated())
            );
        } catch (Exception exception) {
            logger.error("OSS directory listing failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS directory listing failed", exception);
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
            logger.error("OSS source MD5 calculation failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("MD5 calculation failed", exception);
        }
    }

    @Override
    public boolean doesObjectExist(String ossKey) {
        ensureClient();
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(ossProperties.getBucket()).key(ossKey).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            logger.error("OSS object existence check failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS object existence check failed", exception);
        } catch (Exception exception) {
            logger.error("OSS object existence check failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS object existence check failed", exception);
        }
    }

    @Override
    @CacheEvict(value = "ossSignedUrl", key = "#ossKey")
    public void deleteObject(String ossKey) {
        ensureClient();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(ossProperties.getBucket()).key(ossKey).build());
            logger.info("OSS object deleted");
        } catch (Exception exception) {
            logger.error("OSS delete failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS delete failed", exception);
        }
    }

    @Override
    public long getFileSize(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist");
        }
        return file.length();
    }

    @Override
    public boolean verifyUploadIntegrity(String ossKey, String expectedMd5) {
        ensureClient();
        if (expectedMd5 == null || expectedMd5.isBlank()) {
            throw new IllegalArgumentException("Expected MD5 is empty");
        }

        try {
            HeadObjectResponse metadata = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(ossProperties.getBucket()).key(ossKey).build());
            String storedSourceMd5 = metadata.metadata().get(SOURCE_MD5_METADATA);
            if (storedSourceMd5 != null && !storedSourceMd5.isBlank()) {
                boolean matches = storedSourceMd5.equalsIgnoreCase(expectedMd5);
                if (!matches) {
                    logger.error("OSS integrity metadata mismatch");
                }
                return matches;
            }

            try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(ossProperties.getBucket()).key(ossKey).build())) {
                String actualMd5 = DigestUtils.md5Hex(inputStream);
                boolean matches = actualMd5.equalsIgnoreCase(expectedMd5);
                if (!matches) {
                    logger.error("OSS integrity verification failed");
                }
                return matches;
            }
        } catch (Exception exception) {
            logger.error("OSS integrity verification failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("OSS integrity verification failed", exception);
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
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(ossProperties.getUrlExpireSeconds()))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(ossProperties.getBucket()).key(ossKey).build())
                    .build();
            return s3Presigner.presignGetObject(request).url().toString();
        } catch (Exception exception) {
            logger.error("OSS presigned URL generation failed: {}", exception.getClass().getSimpleName());
            throw new RuntimeException("Failed to generate presigned URL", exception);
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
