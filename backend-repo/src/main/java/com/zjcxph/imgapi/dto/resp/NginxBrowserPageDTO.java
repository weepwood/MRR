package com.zjcxph.imgapi.dto.resp;

import java.time.Instant;
import java.util.List;

/**
 * Nginx 静态资源目录浏览结果。
 */
public record NginxBrowserPageDTO(
        String server,
        String serverName,
        String baseUrl,
        String path,
        List<Entry> entries,
        int offset,
        int limit,
        int totalEntries,
        boolean truncated,
        int loadedDirectories,
        int loadedFiles,
        long loadedBytes
) {

    public record Server(
            String key,
            String name,
            String baseUrl,
            boolean configured
    ) {
    }

    public record Entry(
            String name,
            String path,
            boolean directory,
            long size,
            Instant lastModified
    ) {
    }
}
