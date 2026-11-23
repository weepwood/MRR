package com.zjcxph.imgapi.service;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;

public interface ImageContentService {

    ImageContent open(Integer scanId);

    /**
     * @param auditTarget 访问审计目标（病案号，或“病案号:上架号”），与档案袋列表审计口径一致；
     *                    审计落库前由 AuditPayloadSanitizer 做 HMAC 保护，不以明文保存
     */
    record ImageContent(
            InputStream inputStream,
            String filename,
            MediaType mediaType,
            Long contentLength,
            String auditTarget
    ) implements AutoCloseable {

        public ImageContent(InputStream inputStream, String filename, MediaType mediaType, Long contentLength) {
            this(inputStream, filename, mediaType, contentLength, null);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
