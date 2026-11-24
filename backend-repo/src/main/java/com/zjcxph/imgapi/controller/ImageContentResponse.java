package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.ImageContentService.ImageContent;
import com.zjcxph.imgapi.storage.ImageReadStream;
import com.zjcxph.imgapi.utils.HttpByteRange;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Shared internal/external response protocol; authorization stays with each controller. */
final class ImageContentResponse {
    private ImageContentResponse() {}

    static ResponseEntity<StreamingResponseBody> create(HttpServletRequest request, ImageContent content,
                                                        Runnable successAudit, Runnable failureAudit) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("private, no-store");
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        ImageReadStream opened = content.inputStream() instanceof ImageReadStream stream ? stream : null;
        boolean seekable = opened != null && opened.seekable();
        Long length = opened != null
                ? (opened.length() >= 0 ? Long.valueOf(opened.length()) : null)
                : content.contentLength();
        headers.set(HttpHeaders.ACCEPT_RANGES, seekable ? "bytes" : "none");

        String requested = request == null ? null : request.getHeader(HttpHeaders.RANGE);
        // No strong validator is available for mutable legacy files. If-Range must
        // therefore fall back to the full representation, never an unverified 206.
        boolean ifRange = request != null && request.getHeader(HttpHeaders.IF_RANGE) != null;
        HttpByteRange range = null;
        if (seekable && requested != null && requested.startsWith("bytes=")
                && !requested.contains(",") && !ifRange && "GET".equals(request.getMethod())) {
            try {
                range = HttpByteRange.parse(requested, length);
            } catch (IllegalArgumentException exception) {
                closeWithoutBody(content, failureAudit);
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
            }
        }
        headers.setContentType(content.mediaType());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8).build());
        if (range != null) {
            headers.setContentLength(range.length());
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + length);
        } else if (length != null && length >= 0) {
            headers.setContentLength(length);
        }

        HttpByteRange selected = range;
        long deadline = System.nanoTime() + budgetNanos(content.transferBudget());
        StreamingResponseBody body = output -> {
            try (content) {
                checkBudget(deadline);
                if (selected != null) {
                    opened.seek(selected.start());
                    copy(opened, output, selected.length(), deadline);
                } else {
                    // A concurrently grown file cannot exceed the advertised representation length.
                    copy(content.inputStream(), output, length == null ? -1 : length, deadline);
                }
                checkBudget(deadline);
            } catch (IOException | RuntimeException failure) {
                failureAudit.run();
                throw failure;
            }
            successAudit.run();
        };
        return ResponseEntity.status(range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT)
                .headers(headers).body(PendingImageStream.wrap(request, content, body, failureAudit));
    }

    private static long budgetNanos(java.time.Duration configured) {
        var duration = configured == null || configured.isZero() || configured.isNegative()
                ? java.time.Duration.ofSeconds(120) : configured;
        return duration.compareTo(java.time.Duration.ofHours(1)) > 0
                ? java.time.Duration.ofHours(1).toNanos() : duration.toNanos();
    }

    private static void checkBudget(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Image transfer interrupted");
        if (deadline - System.nanoTime() <= 0) throw new java.net.SocketTimeoutException("Image transfer budget exhausted");
    }

    private static void copy(java.io.InputStream input, java.io.OutputStream output, long length, long deadline)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long remaining = length;
        while (remaining != 0) {
            checkBudget(deadline);
            int count = remaining < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, count);
            checkBudget(deadline); // do not write bytes that arrive after the deadline
            if (read < 0) {
                if (remaining > 0) throw new EOFException("Image changed during transfer");
                return;
            }
            if (read > 0) {
                output.write(buffer, 0, read);
                checkBudget(deadline);
                if (remaining > 0) remaining -= read;
            }
        }
    }

    private static void closeWithoutBody(ImageContent content, Runnable failureAudit) {
        try {
            content.close();
        } catch (IOException | RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(ImageContentResponse.class)
                    .warn("Rejected image stream close failed: errorType={}", failure.getClass().getSimpleName());
        } finally {
            failureAudit.run();
        }
    }
}
