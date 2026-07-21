package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ArchiveImageSourceResolver {

    private final List<ArchiveImageSource> sources;

    public ArchiveImageSourceResolver(List<ArchiveImageSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public ArchiveImageSource resolve(PathDO image) throws IOException {
        for (ArchiveImageSource source : sources) {
            if (source.supports(image)) {
                return source;
            }
        }
        throw new IOException("没有可用的受控图片来源");
    }

    public String describeSource(PathDO image) throws IOException {
        return resolve(image).describeSource(image);
    }
}
