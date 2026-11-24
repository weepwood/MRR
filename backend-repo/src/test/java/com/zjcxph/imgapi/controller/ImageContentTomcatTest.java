package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.ImageContentService.ImageContent;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual loopback TCP + embedded Tomcat + Spring MVC, without production configuration or data. */
class ImageContentTomcatTest {
    @TempDir Path directory;

    @Test
    void slowClientDisconnectReleasesClaimedStreamAndNextRequestRecovers() throws Exception {
        var tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        var connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", directory.toString());
        var mvc = new AnnotationConfigWebApplicationContext();
        mvc.register(MvcConfig.class);
        var servlet = Tomcat.addServlet(context, "mvc", new DispatcherServlet(mvc));
        servlet.setAsyncSupported(true);
        servlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "mvc");
        try {
            tomcat.start();
            var probe = mvc.getBean(Probe.class);
            // Register only in this isolated MVC context; no annotated application controller
            // enters the production component or access-policy scans.
            var mapping = mvc.getBean(RequestMappingHandlerMapping.class);
            mapping.registerMapping(RequestMappingInfo.paths("/synthetic").methods(RequestMethod.GET)
                    .options(mapping.getBuilderConfiguration()).build(), probe,
                    Probe.class.getDeclaredMethod("stream", HttpServletRequest.class, boolean.class));
            int port = connector.getLocalPort();
            try (var client = new Socket()) {
                client.setReceiveBufferSize(1024);
                client.setSoTimeout(5000);
                client.setSoLinger(true, 0);
                client.connect(new InetSocketAddress("127.0.0.1", port), 2000);
                client.getOutputStream().write(("GET /synthetic?large=true HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                assertThat(probe.started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(client.getInputStream().read()).isGreaterThanOrEqualTo(0);
                assertThat(probe.active.get()).isTrue();
                assertThat(probe.readBytes.get()).isLessThan(64 * 1024 * 1024);
                // A genuinely slow reader stops consuming bytes, then resets the TCP connection.
                Thread.sleep(150);
            }
            assertThat(probe.closed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.failed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.active.get()).isFalse();
            assertThat(probe.closeCount.get()).isEqualTo(1);
            var next = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/synthetic")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(next.statusCode()).isEqualTo(200);
            assertThat(next.body()).hasSize(1);
            assertThat(probe.closeCount.get()).isEqualTo(2);
        } finally {
            try { tomcat.stop(); } finally { tomcat.destroy(); mvc.close(); }
        }
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig implements WebMvcConfigurer {
        @Bean Probe probe() { return new Probe(); }
        @Bean ThreadPoolTaskExecutor streamExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(2);
            return executor;
        }
        @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
            configurer.setTaskExecutor(streamExecutor()).setDefaultTimeout(10000);
        }
    }

    static class Probe {
        final AtomicBoolean active = new AtomicBoolean();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicInteger readBytes = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();

        ResponseEntity<StreamingResponseBody> stream(HttpServletRequest request,
                                                       @RequestParam(defaultValue="false") boolean large) {
            if (!active.compareAndSet(false, true)) return ResponseEntity.status(503).build();
            long length = large ? 64L * 1024 * 1024 : 1;
            var input = new InputStream() {
                private long remaining = length;
                @Override public int read() { return remaining-- > 0 ? 0 : -1; }
                @Override public int read(byte[] bytes, int offset, int count) {
                    if (remaining == 0) return -1;
                    int n = (int)Math.min(remaining, count);
                    remaining -= n;
                    if (large) { readBytes.addAndGet(n); started.countDown(); }
                    return n;
                }
                @Override public void close() {
                    closeCount.incrementAndGet();
                    active.set(false);
                    if (large) closed.countDown();
                }
            };
            return ImageContentResponse.create(request, new ImageContent(input, "synthetic.bin",
                    MediaType.APPLICATION_OCTET_STREAM, length, null, Duration.ofSeconds(10)),
                    () -> {}, () -> { if (large) failed.countDown(); });
        }
    }
}
