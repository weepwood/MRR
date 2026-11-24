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
    private final FileChannel channel;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ImageReadStream(InputStream delegate, long length, FileChannel channel, Runnable release) {
        super(delegate);
        this.length = length;
        this.channel = channel;
        this.release = release;
    }

    static ImageReadStream openFile(Path file) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        try {
            return new ImageReadStream(Channels.newInputStream(channel), channel.size(), channel, () -> {});
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

    ImageReadStream withPermit(Runnable release) {
        return new ImageReadStream(this, length, channel, release);
    }

    public long length() { return length; }
    public boolean seekable() { return channel != null; }

    public void seek(long offset) throws IOException {
        if (channel == null) throw new IOException("Image source is not seekable");
        channel.position(offset);
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
