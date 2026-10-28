package com.zjcxph.imgapi.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import com.zjcxph.imgapi.config.OssProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Component
public class OssObjectReader {

    private final OssProperties properties;
    private volatile S3Client client;

    public OssObjectReader(OssProperties properties) {
        this.properties = properties;
    }

    public InputStream open(String objectKey) throws IOException {
        try {
            return client().getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build());
        } catch (RuntimeException exception) {
            throw new IOException("OSS 图片读取失败", exception);
        }
    }

    public long size(String objectKey) throws IOException {
        try {
            return client().headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build()).contentLength();
        } catch (RuntimeException exception) {
            throw new IOException("OSS 图片元数据读取失败", exception);
        }
    }

    private S3Client client() throws IOException {
        S3Client current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = buildClient();
            }
            return client;
        }
    }

    private S3Client buildClient() throws IOException {
        String accessKeyId = require(properties.getAccessKeyId(), "OSS_ACCESS_KEY_ID");
        String accessKeySecret = require(properties.getAccessKeySecret(), "OSS_ACCESS_KEY_SECRET");
        String endpoint = require(properties.getEndpoint(), "OSS_ENDPOINT");
        String bucket = require(properties.getBucket(), "OSS_BUCKET");
        if (bucket.contains("/") || bucket.contains("\\")) {
            throw new IOException("OSS Bucket 配置不合法");
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(require(properties.getRegion(), "OSS_REGION")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, accessKeySecret)))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(30))
                        .socketTimeout(Duration.ofSeconds(60))
                        .maxConnections(50))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess()).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private String require(String value, String name) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(name + " 未配置");
        }
        String normalized = value.trim();
        if (normalized.startsWith("\"") || normalized.endsWith("\"")) {
            throw new IOException(name + " 不能包含引号");
        }
        return normalized;
    }

    @PreDestroy
    public void close() {
        S3Client current = client;
        if (current != null) {
            current.close();
        }
    }
}
