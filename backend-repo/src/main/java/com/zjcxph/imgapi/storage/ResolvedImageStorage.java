package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import com.zjcxph.imgapi.exception.OssOperationException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class ResolvedImageStorage implements ImageStorage {

    private static final int MAX_REASON_LENGTH = 160;

    private final ArchiveImageSourceResolver resolver;
    private final MeterRegistry meterRegistry;
    private final ArchiveImageSourceProperties properties;

    public ResolvedImageStorage(ArchiveImageSourceResolver resolver, MeterRegistry meterRegistry) {
        this(resolver, meterRegistry, new ArchiveImageSourceProperties());
    }

    @Autowired
    public ResolvedImageStorage(ArchiveImageSourceResolver resolver, MeterRegistry meterRegistry,
                                ArchiveImageSourceProperties properties) {
        this.resolver = resolver;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return open(image, null);
    }

    /** Runs the same ordered, permit-guarded source selection as the gateway.
     *  Opening and closing is not a full content integrity check. */
    public SourceProbe probe(PathDO image) {
        List<SourceAttempt> attempts = new ArrayList<>();
        try (InputStream ignored = open(image, attempts::add)) {
            // Do not load the medical image, sign a URL or issue an additional HEAD.
        } catch (IOException | RuntimeException failure) {
            String error = attempts.isEmpty() ? classify(failure)
                    : attempts.getLast().available() ? "SOURCE_CLOSE_FAILED" : "ALL_SOURCES_FAILED";
            return new SourceProbe(List.copyOf(attempts), "", false, error);
        }
        return new SourceProbe(List.copyOf(attempts), attempts.getLast().source(), true, "");
    }

    public record SourceAttempt(String source, boolean available, String error) {}
    public record SourceProbe(List<SourceAttempt> attempts, String selectedSource, boolean available, String error) {}

    private InputStream open(PathDO image, Consumer<SourceAttempt> observation) throws IOException {
        try (var budget = SourceOpenBudget.start(properties.getOpenBudget())) {
            return openWithinBudget(image, observation);
        }
    }

    private InputStream openWithinBudget(PathDO image, Consumer<SourceAttempt> observation) throws IOException {
        SourceOpenBudget.check();
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        List<Throwable> failures = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ArchiveImageSource source : candidates) {
            SourceOpenBudget.check();
            String sourceType = source.describeSource(image);
            try {
                InputStream stream = source.open(image);
                try {
                    SourceOpenBudget.check();
                } catch (IOException stop) {
                    try { stream.close(); } catch (IOException | RuntimeException closeFailure) { stop.addSuppressed(closeFailure); }
                    throw stop;
                }
                counter("mrr.archive.image.source.open", sourceType, "success").increment();
                if (!failures.isEmpty()) {
                    counter("mrr.archive.image.source.fallback", sourceType, "success").increment();
                }
                if (observation != null) observation.accept(new SourceAttempt(safeSource(sourceType), true, ""));
                return stream;
            } catch (IOException | RuntimeException exception) {
                counter("mrr.archive.image.source.open", sourceType, "failure").increment();
                failures.add(exception);
                reasons.add(sourceType + ": " + safeReason(sourceType, exception));
                if (observation != null) {
                    observation.accept(new SourceAttempt(safeSource(sourceType), false, classify(exception)));
                }
                stopIfCancelled(exception);
                SourceOpenBudget.check();
            }
        }
        IOException failure = new IOException(
                reasons.isEmpty()
                        ? "没有可用的受控图片来源"
                        : "所有受控图片来源均读取失败（" + String.join("；", reasons) + "）");
        failures.forEach(failure::addSuppressed);
        throw failure;
    }

    @Override
    public long size(PathDO image) throws IOException {
        try (var budget = SourceOpenBudget.start(properties.getOpenBudget())) {
            return sizeWithinBudget(image);
        }
    }

    private long sizeWithinBudget(PathDO image) throws IOException {
        SourceOpenBudget.check();
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        List<Throwable> failures = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ArchiveImageSource source : candidates) {
            SourceOpenBudget.check();
            String sourceType = source.describeSource(image);
            try {
                long size = source.size(image);
                SourceOpenBudget.check();
                counter("mrr.archive.image.source.size", sourceType, "success").increment();
                return size;
            } catch (IOException | RuntimeException exception) {
                counter("mrr.archive.image.source.size", sourceType, "failure").increment();
                failures.add(exception);
                reasons.add(sourceType + ": " + safeReason(sourceType, exception));
                stopIfCancelled(exception);
                SourceOpenBudget.check();
            }
        }
        IOException failure = new IOException(
                reasons.isEmpty()
                        ? "没有可用的受控图片来源"
                        : "所有受控图片来源均无法获取文件大小（" + String.join("；", reasons) + "）");
        failures.forEach(failure::addSuppressed);
        throw failure;
    }

    private void stopIfCancelled(Throwable failure) throws IOException {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof java.nio.channels.ClosedByInterruptException) {
                Thread.currentThread().interrupt();
                throw new IOException("影像来源操作已中断", failure);
            }
            if (current instanceof SourceOpenBudget.DeadlineExceeded deadline) throw deadline;
        }
        SourceOpenBudget.check();
    }

    private String safeSource(String source) {
        return source != null && Set.of("OSS", "NGINX", "NAS", "HTTP", "LOCAL").contains(source)
                ? source : "UNKNOWN";
    }

    private String classify(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof SourceHttpStatusException http) return httpStatus(http.statusCode());
            if (current instanceof S3Exception s3) return httpStatus(s3.statusCode());
            if (current instanceof OssOperationException oss) return oss.getType().name();
            if (current instanceof SourceOpenBudget.DeadlineExceeded) return "TIMEOUT";
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException
                    || current instanceof software.amazon.awssdk.core.exception.ApiCallTimeoutException
                    || current instanceof software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException) return "TIMEOUT";
            if (current instanceof NasNodeCircuit.CircuitOpen) return "SOURCE_CIRCUIT_OPEN";
            if (current instanceof SourcePermitGuard.CapacityException) return "SOURCE_BUSY";
            if (current instanceof InterruptedException) return "INTERRUPTED";
            if (current instanceof InvalidImagePathException) return "INVALID_PATH";
            if (current instanceof FileNotFoundException || current instanceof NoSuchFileException) return "NOT_FOUND";
            if (current instanceof AccessDeniedException) return "ACCESS_DENIED";
        }
        return "SOURCE_UNAVAILABLE";
    }

    private String httpStatus(int status) {
        if (status == 401 || status == 403) return "ACCESS_DENIED";
        if (status == 404 || status == 410) return "NOT_FOUND";
        if (status == 408 || status == 504) return "TIMEOUT";
        if (status == 429) return "SOURCE_BUSY";
        if (status >= 300 && status < 400) return "REDIRECT_REJECTED";
        return "HTTP_ERROR";
    }

    private String safeReason(String sourceType, Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        if ("LOCAL".equals(sourceType) && message.startsWith("影像文件不存在或不可读:")) {
            message = "后端本地文件不存在或不可读";
        } else if ("OSS".equals(sourceType)
                && !message.startsWith("OSS 图片缺少合法的 Object Key")) {
            // SDK 异常可能包含带病案目录的 Object Key，不返回到前端任务错误中。
            message = "OSS 对象不存在、无权限或读取失败";
        }
        return message.length() <= MAX_REASON_LENGTH
                ? message
                : message.substring(0, MAX_REASON_LENGTH) + "…";
    }

    private Counter counter(String name, String source, String result) {
        return Counter.builder(name)
                .tag("source", source)
                .tag("result", result)
                .register(meterRegistry);
    }
}
