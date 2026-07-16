package com.zjcxph.imgapi.storage;

import java.io.IOException;

/**
 * 影像元数据无法安全解析为存储路径。
 */
public class InvalidImagePathException extends IOException {

    public InvalidImagePathException(String message) {
        super(message);
    }
}
