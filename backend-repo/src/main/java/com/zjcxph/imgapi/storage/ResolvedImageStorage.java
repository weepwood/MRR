package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@Primary
public class ResolvedImageStorage implements ImageStorage {

    private final ArchiveImageSourceResolver resolver;
    private final MeterRegistry meterRegistry;

    public ResolvedImageStorage(ArchiveImageSourceResolver resolver, MeterRegistry meterRegistry) {
        this.resolver = resolver;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        IOException failure = null;
        for (ArchiveImageSource source : candidates) {
            String sourceType = source.describeSource(image);
            try {
                InputStream stream = source.open(image);
                counter("mrr.archive.image.source.open", sourceType, "success").increment();
                if (failure != null) {
                    counter("mrr.archive.image.source.fallback", sourceType, "success").increment();
                }
                return stream;
            } catch (IOException exception) {
                counter("mrr.archive.image.source.open", sourceType, "failure").increment();
                if (failure == null) {
                    failure = new IOException("所有受控图片来源均读取失败");
                }
                failure.addSuppressed(exception);
            } catch (RuntimeException exception) {
                counter("mrr.archive.image.source.open", sourceType, "failure").increment();
                if (failure == null) {
                    failure = new IOException("所有受控图片来源均读取失败");
                }
                failure.addSuppressed(exception);
            }
        }
        throw failure == null ? new IOException("没有可用的受控图片来源") : failure;
    }

    @Override
    public long size(PathDO image) throws IOException {
        List<ArchiveImageSource> candidates = resolver.resolveCandidates(image);
        IOException failure = null;
        for (ArchiveImageSource source : candidates) {
            String sourceType = source.describeSource(image);
            try {
                long size = source.size(image);
                counter("mrr.archive.image.source.size", sourceType, "success").increment();
                return size;
            } catch (IOException exception) {
                counter("mrr.archive.image.source.size", sourceType, "failure").increment();
                if (failure == null) {
                    failure = new IOException("所有受控图片来源均无法获取文件大小");
                }
                failure.addSuppressed(exception);
            } catch (RuntimeException exception) {
                counter("mrr.archive.image.source.size", sourceType, "failure").increment();
                if (failure == null) {
                    failure = new IOException("所有受控图片来源均无法获取文件大小");
                }
                failure.addSuppressed(exception);
            }
        }
        throw failure == null ? new IOException("没有可用的受控图片来源") : failure;
    }

    private Counter counter(String name, String source, String result) {
        return Counter.builder(name)
                .tag("source", source)
                .tag("result", result)
                .register(meterRegistry);
    }
}
