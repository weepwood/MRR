package com.zjcxph.imgapi.storage;

import java.io.IOException;

/** Fixed source and HTTP status only; no URL, credentials or response body. */
final class SourceHttpStatusException extends IOException {
    private final int statusCode;

    SourceHttpStatusException(String source, int statusCode) {
        super(source + " 图片服务返回状态码 " + statusCode);
        this.statusCode = statusCode;
    }

    int statusCode() {
        return statusCode;
    }
}
