package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

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
        ArchiveImageSource source = resolver.resolve(image);
        String sourceType = source.describeSource(image);
        try {
            InputStream stream = source.open(image);
            counter("mrr.archive.image.source.open", sourceType, "success").increment();
            return stream;
        } catch (IOException | RuntimeException exception) {
            counter("mrr.archive.image.source.open", sourceType, "failure").increment();
            throw exception;
        }
    }

    @Override
    public long size(PathDO image) throws IOException {
        ArchiveImageSource source = resolver.resolve(image);
        String sourceType = source.describeSource(image);
        try {
            long size = source.size(image);
            counter("mrr.archive.image.source.size", sourceType, "success").increment();
            return size;
        } catch (IOException | RuntimeException exception) {
            counter("mrr.archive.image.source.size", sourceType, "failure").increment();
            throw exception;
        }
    }

    private Counter counter(String name, String source, String result) {
        return Counter.builder(name)
                .tag("source", source)
                .tag("result", result)
                .register(meterRegistry);
    }
}
