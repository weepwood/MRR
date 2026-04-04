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
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.OssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

@Service
public class OssServiceImpl implements OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssServiceImpl.class);

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

        // 验证配置是否正确（检查是否包含引号）
        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();
        
        if (accessKeyId.startsWith("\"") || accessKeyId.endsWith("\"")) {
            logger.error("OSS Access Key ID contains quotes! This will cause authentication failures. " +
                        "Please remove quotes from application.properties or environment variables.");
            throw new IllegalStateException("Invalid OSS Access Key ID format: contains quotes");
        }
        
        if (accessKeySecret.startsWith("\"") || accessKeySecret.endsWith("\"")) {
            logger.error("OSS Access Key Secret contains quotes! This will cause authentication failures. " +
                        "Please remove quotes from application.properties or environment variables.");
            throw new IllegalStateException("Invalid OSS Access Key Secret format: contains quotes");
        }

        try {
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId, accessKeySecret);

            ClientConfiguration clientConfig = new ClientConfiguration();
            clientConfig.setConnectionTimeout(30000);
            clientConfig.setSocketTimeout(60000);
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

            // 隐藏敏感信息，只显示前4位和后4位
            String maskedKeyId = accessKeyId.length() > 8 ? 
                accessKeyId.substring(0, 4) + "****" + accessKeyId.substring(accessKeyId.length() - 4) : "****";
            logger.info("OSS client initialized successfully: endpoint={}, bucket={}, accessKeyId={}", 
                       ossProperties.getEndpoint(), ossProperties.getBucket(), maskedKeyId);
        } catch (Exception e) {
            logger.error("Failed to initialize OSS client", e);
        }
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
    public String uploadFile(String localFilePath, String ossKey) {
        ensureClient();
        File file = new File(localFilePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Local file does not exist: " + localFilePath);
        }

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());

            // Determine content type from filename
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                metadata.setContentType("image/jpeg");
            } else if (filename.endsWith(".png")) {
                metadata.setContentType("image/png");
            } else if (filename.endsWith(".gif")) {
                metadata.setContentType("image/gif");
            } else if (filename.endsWith(".bmp")) {
                metadata.setContentType("image/bmp");
            } else {
                metadata.setContentType("application/octet-stream");
            }

            PutObjectRequest putRequest = new PutObjectRequest(
                    ossProperties.getBucket(), ossKey, file)
                    .withMetadata(metadata);

            s3Client.putObject(putRequest);
            logger.info("Uploaded to OSS: {} -> {}", localFilePath, ossKey);
            return ossKey;
        } catch (Exception e) {
            logger.error("Failed to upload file to OSS: {} -> {}", localFilePath, ossKey, e);
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String ossKey) {
        ensureClient();
        try {
            Date expiration = new Date(System.currentTimeMillis()
                    + (long) ossProperties.getUrlExpireSeconds() * 1000);

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossProperties.getBucket(), ossKey)
                    .withExpiration(expiration);

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
}
