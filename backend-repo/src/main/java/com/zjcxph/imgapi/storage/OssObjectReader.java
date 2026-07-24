package com.zjcxph.imgapi.storage;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.zjcxph.imgapi.config.OssProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class OssObjectReader {

    private final OssProperties properties;
    private volatile AmazonS3 client;

    public OssObjectReader(OssProperties properties) {
        this.properties = properties;
    }

    public InputStream open(String objectKey) throws IOException {
        try {
            S3Object object = client().getObject(properties.getBucket(), objectKey);
            return object.getObjectContent();
        } catch (RuntimeException exception) {
            throw new IOException("OSS 图片读取失败", exception);
        }
    }

    public long size(String objectKey) throws IOException {
        try {
            return client().getObjectMetadata(properties.getBucket(), objectKey).getContentLength();
        } catch (RuntimeException exception) {
            throw new IOException("OSS 图片元数据读取失败", exception);
        }
    }

    private AmazonS3 client() throws IOException {
        AmazonS3 current = client;
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

    private AmazonS3 buildClient() throws IOException {
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

        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setConnectionTimeout(30_000);
        configuration.setSocketTimeout(60_000);
        configuration.setMaxConnections(50);

        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        endpoint,
                        properties.getRegion()))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKeyId, accessKeySecret)))
                .withClientConfiguration(configuration)
                .withPathStyleAccessEnabled(properties.isPathStyleAccess())
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
        AmazonS3 current = client;
        if (current != null) {
            current.shutdown();
        }
    }
}
