package com.zjcxph.imgapi.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** 同时约束任务文件和兼容流式导出的实际输出字节数。 */
public final class ExportSizeLimitOutputStream extends FilterOutputStream {
    private final long maximum;
    private long written;

    public ExportSizeLimitOutputStream(OutputStream output, long maximum) {
        super(output);
        if (maximum <= 0) throw new IllegalArgumentException("导出文件上限必须大于零");
        this.maximum = maximum;
    }

    @Override
    public void write(int value) throws IOException {
        ensureCapacity(1);
        out.write(value);
        written++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        ensureCapacity(length);
        out.write(bytes, offset, length);
        written += length;
    }

    private void ensureCapacity(int increment) throws IOException {
        if (written > maximum - increment) {
            throw new IOException("导出文件超过单文件配额，请减少选页并分批导出");
        }
    }
}
