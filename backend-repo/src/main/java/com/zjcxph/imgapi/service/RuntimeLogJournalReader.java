package com.zjcxph.imgapi.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** 有界按行读取；偏移为解压后的字节位置，半行不确认，历史文件限制解压规模。 */
public final class RuntimeLogJournalReader {
    public static final long MAX_BYTES = 128L * 1024 * 1024;
    public record Line(long offset, String text) { }
    public record Batch(List<Line> lines, long nextOffset, boolean complete, long invalidLines) { }
    private RuntimeLogJournalReader() { }

    public static Batch read(Path file, long offset, int limit) throws IOException {
        boolean gzip = file.getFileName().toString().endsWith(".gz");
        try (InputStream raw = Files.newInputStream(file);
             InputStream stream = new BufferedInputStream(gzip ? new GZIPInputStream(raw) : raw)) {
            if (offset < 0 || gzip && offset > MAX_BYTES) throw new IOException("SOURCE_LIMIT");
            stream.skipNBytes(offset);
            List<Line> lines = new ArrayList<>();
            long position = offset, confirmed = offset, invalid = 0;
            for (int n = 0; n < limit; n++) {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                long start = position;
                boolean oversized = false;
                int next;
                while ((next = stream.read()) != -1 && next != '\n') {
                    position++;
                    if (gzip && position > MAX_BYTES) throw new IOException("EXPANSION_LIMIT");
                    if (line.size() < 65536) line.write(next); else oversized = true;
                }
                if (next == -1) {
                    if (gzip && position > confirmed) {
                        if (oversized) invalid++; else lines.add(new Line(start, line.toString(StandardCharsets.UTF_8)));
                        confirmed = position;
                    }
                    return new Batch(lines, confirmed, position == confirmed, invalid);
                }
                position++;
                confirmed = position;
                if (oversized) invalid++; else lines.add(new Line(start, line.toString(StandardCharsets.UTF_8)));
            }
            return new Batch(lines, confirmed, false, invalid);
        }
    }
}
