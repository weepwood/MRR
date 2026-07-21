package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class ResolvedImageStorage implements ImageStorage {

    private static final int MAX_REASON_LENGTH = 160;

    private final ArchiveImageSourceResolver resolver;
    private final MeterRegistry meterRegistry;

    public ResolvedImageStorage(ArchiveImageSourceResolver resolver, MeterRegistry meterRegistry) {
        this.resolver = resolver;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        List<Throwable> failures = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ArchiveImageSource source : candidates) {
            String sourceType = source.describeSource(image);
            try {
                InputStream stream = source.open(image);
                counter("mrr.archive.image.source.open", sourceType, "success").increment();
                if (!failures.isEmpty()) {
                    counter("mrr.archive.image.source.fallback", sourceType, "success").increment();
                }
                return stream;
            } catch (IOException | RuntimeException exception) {
                counter("mrr.archive.image.source.open", sourceType, "failure").increment();
                failures.add(exception);
                reasons.add(sourceType + ": " + safeReason(sourceType, exception));
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
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        List<Throwable> failures = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ArchiveImageSource source : candidates) {
            String sourceType = source.describeSource(image);
            try {
                long size = source.size(image);
                counter("mrr.archive.image.source.size", sourceType, "success").increment();
                return size;
            } catch (IOException | RuntimeException exception) {
                counter("mrr.archive.image.source.size", sourceType, "failure").increment();
                failures.add(exception);
                reasons.add(sourceType + ": " + safeReason(sourceType, exception));
            }
        }
        IOException failure = new IOException(
                reasons.isEmpty()
                        ? "没有可用的受控图片来源"
                        : "所有受控图片来源均无法获取文件大小（" + String.join("；", reasons) + "）");
        failures.forEach(failure::addSuppressed);
        throw failure;
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
