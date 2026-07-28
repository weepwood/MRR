package com.zjcxph.imgapi.service;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;

public interface ImageContentService {

    ImageContent open(Integer scanId);

    record ImageContent(
            InputStream inputStream,
            String filename,
            MediaType mediaType,
            Long contentLength
    ) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
