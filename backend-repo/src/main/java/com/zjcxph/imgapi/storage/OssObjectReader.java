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
    private volatile java.util.Map<String, Object> poolSample = java.util.Map.of();

    /** SDK request-attempt observation, not a live pool gauge. */
    public java.util.Map<String, Object> poolSnapshot() { return poolSample; }

    private void observePool(software.amazon.awssdk.metrics.MetricCollection collection) {
        var leased = collection.metricValues(software.amazon.awssdk.http.HttpMetric.LEASED_CONCURRENCY);
        var available = collection.metricValues(software.amazon.awssdk.http.HttpMetric.AVAILABLE_CONCURRENCY);
        var pending = collection.metricValues(software.amazon.awssdk.http.HttpMetric.PENDING_CONCURRENCY_ACQUIRES);
        var max = collection.metricValues(software.amazon.awssdk.http.HttpMetric.MAX_CONCURRENCY);
        if (!leased.isEmpty() && !available.isEmpty() && !pending.isEmpty() && !max.isEmpty()) {
            poolSample = java.util.Map.of("sampledAt", java.time.Instant.now().toString(),
                    "leased", leased.getLast(), "available", available.getLast(),
                    "pending", pending.getLast(), "max", max.getLast());
        }
        collection.children().forEach(this::observePool);
    }

    public OssObjectReader(OssProperties properties) {
        this.properties = properties;
    }

    public InputStream open(String objectKey) throws IOException {
        try {
            S3Client current = client();
            Duration timeout = SourceOpenBudget.limit(Duration.ofSeconds(120));
            var response = current.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey)
                    .overrideConfiguration(config -> config.apiCallTimeout(timeout)
                            .apiCallAttemptTimeout(timeout.compareTo(Duration.ofSeconds(60)) < 0 ? timeout : Duration.ofSeconds(60)))
                    .build());
            Long length = response.response().contentLength();
            return ImageReadStream.withLength(new OssResponseStream(response), length == null ? -1L : length);
        } catch (RuntimeException exception) {
            throw new IOException("OSS 图片读取失败", exception);
        }
    }

    public long size(String objectKey) throws IOException {
        try {
            S3Client current = client();
            Duration timeout = SourceOpenBudget.limit(Duration.ofSeconds(120));
            return current.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey)
                    .overrideConfiguration(config -> config.apiCallTimeout(timeout)
                            .apiCallAttemptTimeout(timeout.compareTo(Duration.ofSeconds(60)) < 0 ? timeout : Duration.ofSeconds(60)))
                    .build()).contentLength();
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
                        .connectionAcquisitionTimeout(Duration.ofSeconds(10))
                        .socketTimeout(Duration.ofSeconds(60))
                        .maxConnections(50))
                .overrideConfiguration(config -> config.addMetricPublisher(new software.amazon.awssdk.metrics.MetricPublisher() {
                    @Override public void publish(software.amazon.awssdk.metrics.MetricCollection collection) { observePool(collection); }
                    @Override public void close() {}
                }))
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
