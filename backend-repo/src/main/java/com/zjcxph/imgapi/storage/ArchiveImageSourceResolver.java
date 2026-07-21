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

    public List<ArchiveImageSource> resolveCandidates(PathDO image) throws IOException {
        List<ArchiveImageSource> candidates = sources.stream()
                .filter(source -> source.supports(image))
                .toList();
        if (candidates.isEmpty()) {
            throw new IOException("没有可用的受控图片来源");
        }
        return candidates;
    }

    public ArchiveImageSource resolve(PathDO image) throws IOException {
        return resolveCandidates(image).get(0);
    }

    public String describeSource(PathDO image) throws IOException {
        return resolve(image).describeSource(image);
    }
}
