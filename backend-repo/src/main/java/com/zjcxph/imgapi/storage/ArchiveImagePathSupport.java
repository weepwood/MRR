package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

final class ArchiveImagePathSupport {

    private ArchiveImagePathSupport() {
    }

    /**
     * 显式 LOCAL/NAS/HTTP 来源允许使用受控 source_ref；AUTO/OSS 的 source_ref
     * 可能是 OSS Object Key，降级到历史来源时必须忽略它并使用业务目录字段。
     */
    static Path relativePath(PathDO image) throws IOException {
        if (image == null) {
            throw new InvalidImagePathException("影像路径信息不能为空");
        }
        if (usesExplicitRelativeReference(image)
                && image.getSourceRef() != null
                && !image.getSourceRef().isBlank()) {
            return controlledRelativeReference(image.getSourceRef());
        }
        return legacyRelativePath(image);
    }

    static Path legacyRelativePath(PathDO image) throws IOException {
        if (image == null) {
            throw new InvalidImagePathException("影像路径信息不能为空");
        }
        String folder = segment(image.getFolder(), "folder");
        if (folder.length() < 5) {
            throw new InvalidImagePathException("folder 长度不足 5 位");
        }
        String bah = segment(image.getBah(), "bah");
        String directoryKey = MedicalRecordCodeUtils.requiresSjhForBah(bah)
                ? segment(image.getSjh(), "sjh")
                : segment(image.getBrxh(), "brxh");
        String filename = segment(image.getFilename(), "filename");
        return Path.of(folder.substring(0, 5), folder, directoryKey + "-" + bah, filename);
    }

    private static boolean usesExplicitRelativeReference(PathDO image) {
        String type = image.getSourceType() == null
                ? ""
                : image.getSourceType().trim().toUpperCase(Locale.ROOT);
        return "LOCAL".equals(type) || "NAS".equals(type) || "HTTP".equals(type);
    }

    private static Path controlledRelativeReference(String sourceRef) throws IOException {
        String rawReference = sourceRef.trim();
        if (rawReference.indexOf('\0') >= 0
                || rawReference.contains("\\")
                || rawReference.contains(":")
                || rawReference.startsWith("/")
                || rawReference.startsWith("//")) {
            throw new InvalidImagePathException("图片来源引用必须是受控相对路径");
        }
        Path relative = Path.of(rawReference).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new InvalidImagePathException("图片来源引用必须是受控相对路径");
        }
        return relative;
    }

    static String segment(String value, String field) throws InvalidImagePathException {
        if (value == null || value.isBlank()) {
            throw new InvalidImagePathException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.equals(".") || normalized.equals("..")
                || normalized.contains("/") || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0
                || normalized.contains(":")) {
            throw new InvalidImagePathException(field + " 包含非法路径字符");
        }
        return normalized;
    }
}
