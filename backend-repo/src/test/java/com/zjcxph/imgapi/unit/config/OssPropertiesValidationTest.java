package com.zjcxph.imgapi.unit.config;

import com.zjcxph.imgapi.config.OssProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OssProperties OSS 配置校验")
class OssPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("本地模式未配置任何 OSS 凭据时允许启动")
    void allowsLocalModeWithoutCredentials() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OssProperties.class);
        });
    }

    @Test
    @DisplayName("只配置一半凭据时拒绝启动")
    void rejectsPartialCredentialPair() {
        contextRunner
                .withPropertyValues("oss.access-key-id=only-access-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("OSS Access Key ID 与 Secret 必须同时配置");
                });
    }

    @Test
    @DisplayName("配置凭据时要求 endpoint、bucket 和 region 完整")
    void rejectsMissingRequiredLocation() {
        contextRunner
                .withPropertyValues(
                        "oss.access-key-id=test-access-key",
                        "oss.access-key-secret=test-secret",
                        "oss.endpoint=",
                        "oss.bucket=test-bucket",
                        "oss.region=test-region"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("endpoint、bucket 和 region 不能为空");
                });
    }

    @Test
    @DisplayName("拒绝被引号包裹的凭据")
    void rejectsQuotedCredentials() {
        contextRunner
                .withPropertyValues(
                        "oss.access-key-id=\"quoted-access-key\"",
                        "oss.access-key-secret=test-secret",
                        "oss.endpoint=oss.example.com",
                        "oss.bucket=test-bucket",
                        "oss.region=test-region"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("凭据不能包含首尾引号或控制字符");
                });
    }

    @Test
    @DisplayName("配置凭据时限制预签名 URL 有效期")
    void rejectsInvalidUrlExpiry() {
        contextRunner
                .withPropertyValues(
                        "oss.access-key-id=test-access-key",
                        "oss.access-key-secret=test-secret",
                        "oss.endpoint=oss.example.com",
                        "oss.bucket=test-bucket",
                        "oss.region=test-region",
                        "oss.url-expire-seconds=30"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("URL 有效期必须在 60 至 86400 秒之间");
                });
    }

    @Test
    @DisplayName("完整 S3 兼容配置可以正常绑定")
    void acceptsCompleteS3CompatibleConfiguration() {
        contextRunner
                .withPropertyValues(
                        "oss.access-key-id=test-access-key",
                        "oss.access-key-secret=test-secret",
                        "oss.endpoint=http://10.0.0.10:9000",
                        "oss.bucket=test-bucket",
                        "oss.region=test-region",
                        "oss.url-expire-seconds=3600",
                        "oss.path-style-access=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OssProperties properties = context.getBean(OssProperties.class);
                    assertThat(properties.getEndpoint()).isEqualTo("http://10.0.0.10:9000");
                    assertThat(properties.getBucket()).isEqualTo("test-bucket");
                    assertThat(properties.isPathStyleAccess()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OssProperties.class)
    static class TestConfiguration {
    }
}
