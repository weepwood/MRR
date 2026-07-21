package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;

import java.io.IOException;
import java.nio.file.Path;

final class ArchiveImagePathSupport {

    private ArchiveImagePathSupport() {
    }

    static Path relativePath(PathDO image) throws IOException {
        if (image == null) {
            throw new InvalidImagePathException("影像路径信息不能为空");
        }
        if (image.getSourceRef() != null && !image.getSourceRef().isBlank()) {
            Path relative = Path.of(image.getSourceRef().trim()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new InvalidImagePathException("图片来源引用必须是受控相对路径");
            }
            return relative;
        }
        String folder = segment(image.getFolder(), "folder");
        if (folder.length() < 5) {
            throw new InvalidImagePathException("folder 长度不足 5 位");
        }
        String brxh = segment(image.getBrxh(), "brxh");
        String bah = segment(image.getBah(), "bah");
        String filename = segment(image.getFilename(), "filename");
        return Path.of(folder.substring(0, 5), folder, brxh + "-" + bah, filename);
    }

    static String segment(String value, String field) throws InvalidImagePathException {
        if (value == null || value.isBlank()) {
            throw new InvalidImagePathException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.equals(".") || normalized.equals("..")
                || normalized.contains("/") || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0) {
            throw new InvalidImagePathException(field + " 包含非法路径字符");
        }
        return normalized;
    }
}
