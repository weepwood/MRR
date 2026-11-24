package com.zjcxph.imgapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transfers ownership of an already opened source to the MVC streaming task. */
final class PendingImageStream {
    private static final Logger logger = LoggerFactory.getLogger(PendingImageStream.class);

    private PendingImageStream() {}

    static StreamingResponseBody wrap(HttpServletRequest request, AutoCloseable content,
                                      StreamingResponseBody body, Runnable abandonedAudit) {
        AtomicBoolean claimed = new AtomicBoolean();
        Runnable closePending = () -> {
            if (!claimed.compareAndSet(false, true)) return;
            try {
                content.close();
            } catch (Exception exception) {
                logger.warn("Abandoned streaming resource close failed: errorType={}", exception.getClass().getSimpleName());
            } finally {
                try {
                    abandonedAudit.run();
                } catch (RuntimeException exception) {
                    logger.warn("Abandoned image stream audit failed: errorType={}", exception.getClass().getSimpleName());
                }
            }
        };
        if (request != null) {
            try {
                WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(new Object(),
                        new CallableProcessingInterceptor() {
                            @Override
                            public <T> Object handleTimeout(NativeWebRequest webRequest, Callable<T> task) {
                                closePending.run();
                                return RESULT_NONE;
                            }

                            @Override
                            public <T> Object handleError(NativeWebRequest webRequest, Callable<T> task, Throwable failure) {
                                closePending.run();
                                return RESULT_NONE;
                            }

                            @Override
                            public <T> void afterCompletion(NativeWebRequest webRequest, Callable<T> task) {
                                closePending.run();
                            }
                        });
            } catch (RuntimeException exception) {
                closePending.run();
                throw exception;
            }
        }
        return output -> {
            if (!claimed.compareAndSet(false, true)) {
                throw new IOException("Streaming resource is no longer available");
            }
            // Once streaming starts, only the body's existing try-with-resources owns the source.
            body.writeTo(output);
        };
    }
}
