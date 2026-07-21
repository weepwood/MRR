package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
@Order(100)
public class LocalArchiveImageSource implements ArchiveImageSource {

    private final LocalImageStorage localImageStorage;
    private final SourcePermitGuard permitGuard;

    public LocalArchiveImageSource(LocalImageStorage localImageStorage,
                                   ArchiveImageSourceProperties properties) {
        this.localImageStorage = localImageStorage;
        this.permitGuard = new SourcePermitGuard(
                properties.getLocalMaxConcurrency(), properties.getAcquireTimeout());
    }

    @Override
    public boolean supports(PathDO image) {
        String type = normalizedType(image);
        return type.isEmpty() || "AUTO".equals(type) || "LOCAL".equals(type);
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return permitGuard.open(() -> localImageStorage.open(image));
    }

    @Override
    public long size(PathDO image) throws IOException {
        return permitGuard.call(() -> localImageStorage.size(image));
    }

    @Override
    public String describeSource(PathDO image) {
        return "LOCAL";
    }

    private String normalizedType(PathDO image) {
        return image == null || image.getSourceType() == null
                ? ""
                : image.getSourceType().trim().toUpperCase(Locale.ROOT);
    }
}
