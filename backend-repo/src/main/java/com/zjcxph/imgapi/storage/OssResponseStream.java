package com.zjcxph.imgapi.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Do not let Apache's response close drain an unread remote object after cancellation/diagnostics. */
final class OssResponseStream extends InputStream {
    private final ResponseInputStream<GetObjectResponse> response;
    private final Long length;
    private final AtomicBoolean closed = new AtomicBoolean();
    private long consumed;
    private boolean eof;

    OssResponseStream(ResponseInputStream<GetObjectResponse> response) {
        this.response = response;
        this.length = response.response().contentLength();
    }

    @Override public int read() throws IOException {
        int value = response.read();
        if (value < 0) eof = true;
        else consumed++;
        return value;
    }

    @Override public int read(byte[] bytes, int offset, int count) throws IOException {
        int read = response.read(bytes, offset, count);
        if (read < 0) eof = true;
        else consumed += read;
        return read;
    }

    @Override public int available() throws IOException { return response.available(); }

    @Override public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        if (eof || (length != null && length >= 0 && consumed >= length)) response.close();
        else response.abort();
    }
}
