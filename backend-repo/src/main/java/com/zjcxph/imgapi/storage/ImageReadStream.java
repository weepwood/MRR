package com.zjcxph.imgapi.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/** Length and optional random access belong to the actual opened source. */
public final class ImageReadStream extends FilterInputStream {
    private final long length;
    @FunctionalInterface interface SeekOperation { void seek(long offset) throws IOException; }
    private final SeekOperation seekOperation;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ImageReadStream(InputStream delegate, long length, SeekOperation seekOperation, Runnable release) {
        super(delegate);
        this.length = length;
        this.seekOperation = seekOperation;
        this.release = release;
    }

    static ImageReadStream openFile(Path file) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        try {
            return new ImageReadStream(Channels.newInputStream(channel), channel.size(), channel::position, () -> {});
        } catch (IOException | RuntimeException failure) {
            try { channel.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    static long httpLength(java.net.http.HttpHeaders headers) {
        try {
            return headers.firstValueAsLong("Content-Length").orElse(-1L);
        } catch (NumberFormatException malformed) {
            return -1L;
        }
    }

    static ImageReadStream withLength(InputStream delegate, long length) {
        return new ImageReadStream(delegate, length, null, () -> {});
    }

    static ImageReadStream withSeek(InputStream delegate, long length, SeekOperation seek) {
        return new ImageReadStream(delegate, length, seek, () -> {});
    }

    ImageReadStream withPermit(Runnable release) {
        return new ImageReadStream(this, length, seekOperation, release);
    }

    public long length() { return length; }
    public boolean seekable() { return seekOperation != null; }

    public void seek(long offset) throws IOException {
        if (seekOperation == null) throw new IOException("Image source is not seekable");
        seekOperation.seek(offset);
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        try {
            super.close();
        } finally {
            release.run();
        }
    }
}
