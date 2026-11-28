package com.zjcxph.imgapi.unit.config;

import com.zjcxph.imgapi.config.AsyncConfig;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.service.impl.AsyncLogServiceImpl;
import com.zjcxph.imgapi.setting.SettingRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.slf4j.MDC;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AsyncConfigTest {

    @Test
    void auditExecutorRejectsSubmissionAfterShutdownInsteadOfSilentlyDiscarding() {
        var executor = (ThreadPoolTaskExecutor) new AsyncConfig().logAsyncExecutor();
        try {
            executor.getThreadPoolExecutor().shutdown();
            assertThatThrownBy(() -> executor.execute(() -> {}))
                    .isInstanceOf(TaskRejectedException.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void actualAsyncProxyReportsClosedExecutorRejectionThroughPrometheus() {
        var mapper = mock(LogMapper.class);
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (var context = new AnnotationConfigApplicationContext()) {
            registry.config().commonTags("application", "mrr");
            context.registerBean(LogMapper.class, () -> mapper);
            context.registerBean(MeterRegistry.class, () -> registry);
            context.register(AsyncConfig.class, AsyncLogServiceImpl.class);
            context.refresh();

            var interceptor = new LogInterceptor(context.getBean(AsyncLogService.class), registry,
                    new SettingRegistry(new LogRetentionProperties()));
            var executor = context.getBean("logAsyncExecutor", ThreadPoolTaskExecutor.class);
            var request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
            var response = new MockHttpServletResponse();
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/scans/{id}");

            assertThat(registry.scrape()).contains("mrr_audit_submission_rejected_total{application=\"mrr\"} 0.0");
            executor.getThreadPoolExecutor().shutdown();
            interceptor.preHandle(request, response, new Object());
            interceptor.afterCompletion(request, response, new Object(), null);

            assertThat(registry.scrape()).contains("mrr_audit_submission_rejected_total{application=\"mrr\"} 1.0");
            assertThat(registry.get("mrr.audit.dropped").gauge().value()).isZero();
            assertThat(registry.get("http.requests.total").tag("method", "GET").tag("status", "200").counter().count()).isEqualTo(1);
            assertThat(MDC.get("requestId")).isNull();
            verifyNoInteractions(mapper);
        } finally {
            registry.close();
            MDC.clear();
        }
    }

    @Test
    void overloadedRunningAuditExecutorStillRunsInCallingThread() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new AsyncConfig().logAsyncExecutor();
        var entered = new CountDownLatch(5);
        var release = new CountDownLatch(1);
        Runnable blocker = () -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            for (int i = 0; i < 505; i++) {
                executor.execute(blocker);
            }
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.getThreadPoolExecutor().getQueue().size()).isEqualTo(500);
            var actual = new AtomicReference<Thread>();
            executor.execute(() -> actual.set(Thread.currentThread()));
            assertThat(actual.get()).isSameAs(Thread.currentThread());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
