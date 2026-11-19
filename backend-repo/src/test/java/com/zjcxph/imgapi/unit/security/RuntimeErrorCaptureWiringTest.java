package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.config.RuntimeErrorLogCapture;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import com.zjcxph.imgapi.service.SystemErrorEventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * ADR-0008：运行日志 journal 功能取消后，错误中心实时捕获成为唯一采集路径。
 *
 * <p>原 {@code DiagnosticFeatureSwitchTest} 验证的是「journal 开关二选一」；
 * 开关已随功能删除，这里固定新契约：{@code RuntimeErrorLogCapture} 无条件注册，
 * 不再受任何属性影响，避免将来误加回条件开关造成错误中心静默失数据。</p>
 */
class RuntimeErrorCaptureWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(SystemErrorEventMapper.class, () -> mock(SystemErrorEventMapper.class))
            .withBean(SystemErrorEventService.class, () -> mock(SystemErrorEventService.class))
            .withUserConfiguration(RuntimeErrorLogCapture.class);

    @Test void captureIsRegisteredUnconditionally() {
        context.run(app -> assertThat(app).hasSingleBean(RuntimeErrorLogCapture.class));
        context.withPropertyValues("app.runtime-logs.enabled=true")
                .run(app -> assertThat(app).hasSingleBean(RuntimeErrorLogCapture.class));
        context.withPropertyValues("app.runtime-logs.enabled=false")
                .run(app -> assertThat(app).hasSingleBean(RuntimeErrorLogCapture.class));
    }

    @Test void captureStartsAutomaticallyWithLifecycle() {
        context.run(app -> {
            RuntimeErrorLogCapture capture = app.getBean(RuntimeErrorLogCapture.class);
            assertThat(capture.isAutoStartup()).isTrue();
            assertThat(capture.getPhase()).isEqualTo(Integer.MAX_VALUE - 100);
        });
    }
}
