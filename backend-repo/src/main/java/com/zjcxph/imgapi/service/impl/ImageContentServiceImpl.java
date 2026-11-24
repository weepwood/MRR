package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.exception.OssErrorType;
import com.zjcxph.imgapi.exception.OssOperationException;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.service.ImageContentService;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.ImageReadStream;
import com.zjcxph.imgapi.storage.InvalidImagePathException;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Service
public class ImageContentServiceImpl implements ImageContentService {

    private static final Logger logger = LoggerFactory.getLogger(ImageContentServiceImpl.class);
    private static final int MAX_FILENAME_LENGTH = 180;

    private final ImageContentMapper imageContentMapper;
    private final ImageStorage imageStorage;
    private final ArchiveImageSourceProperties properties;

    public ImageContentServiceImpl(ImageContentMapper imageContentMapper, ImageStorage imageStorage) {
        this(imageContentMapper, imageStorage, new ArchiveImageSourceProperties());
    }

    @Autowired
    public ImageContentServiceImpl(ImageContentMapper imageContentMapper, ImageStorage imageStorage,
                                   ArchiveImageSourceProperties properties) {
        this.imageContentMapper = imageContentMapper;
        this.imageStorage = imageStorage;
        this.properties = properties;
    }

    @Override
    public ImageContent open(Integer scanId) {
        if (scanId == null || scanId <= 0) {
            throw new BusinessException(400, "影像 ID 必须是正整数");
        }

        Scan scan = imageContentMapper.findActiveById(scanId);
        if (scan == null) {
            throw new BusinessException(404, "影像不存在或已删除");
        }

        return openScan(scan);
    }

    @Override
    public ImageContent openLegacy(String bah, String brxh, String folder, String filename) {
        bah = legacySegment(bah);
        brxh = legacySegment(brxh);
        folder = legacySegment(folder);
        filename = legacySegment(filename);
        if (folder.length() < 5 || MedicalRecordCodeUtils.requiresSjhForBah(bah)) {
            throw new BusinessException(400, "旧影像路径参数不完整，请使用影像 ID");
        }
        var matches = imageContentMapper.findActiveLegacyCandidates(bah, brxh, folder, filename);
        if (matches.isEmpty()) {
            throw new BusinessException(404, "影像不存在或已删除");
        }
        if (matches.size() != 1) {
            throw new BusinessException(409, "旧影像路径匹配多个有效记录，请使用影像 ID");
        }
        return openScan(matches.getFirst());
    }

    private static String legacySegment(String value) {
        String normalized = MedicalRecordCodeUtils.normalize(value);
        if (normalized == null || normalized.length() > 255 || normalized.equals(".") || normalized.equals("..")
                || normalized.contains("/") || normalized.contains("\\") || normalized.contains(":")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(400, "影像路径参数无效");
        }
        return normalized;
    }

    private ImageContent openScan(Scan scan) {
        Integer scanId = scan.getId();
        PathDO path = PathDO.fromScan(scan);
        try {
            InputStream stream = imageStorage.open(path);
            String filename = safeFilename(scan);
            MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            Long contentLength = stream instanceof ImageReadStream opened && opened.length() >= 0
                    ? opened.length() : null;
            return new ImageContent(stream, filename, mediaType, contentLength, auditTarget(scan), properties.getTransferBudget());
        } catch (IOException exception) {
            throw mapReadFailure(scanId, exception);
        }
    }

    private RuntimeException mapReadFailure(Integer scanId, IOException failure) {
        if (containsThrowable(failure, InvalidImagePathException.class)) {
            logger.warn("拒绝读取非法影像元数据: scanId={}", scanId);
            return new BusinessException(400, "影像元数据无法安全解析");
        }

        for (OssErrorType type : new OssErrorType[]{
                OssErrorType.OSS_UNAUTHORIZED,
                OssErrorType.OSS_UNAVAILABLE,
                OssErrorType.OSS_NOT_CONFIGURED,
                OssErrorType.OSS_OPERATION_FAILED
        }) {
            OssOperationException ossFailure = findOssFailure(failure, type);
            if (ossFailure != null) {
                return ossFailure;
            }
        }

        if (isNotFoundOnly(failure, newIdentitySet())) {
            return new BusinessException(404, "影像文件不存在");
        }

        logger.error("读取受控影像内容失败: scanId={}, errorType={}", scanId, failure.getClass().getSimpleName());
        return new BusinessException(503, "影像来源暂不可用");
    }

    /** 与 ArchiveAccessService 的档案袋列表审计口径一致：病案号，或“病案号:上架号”。 */
    private String auditTarget(Scan scan) {
        String bah = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getBah());
        String sjh = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getSjh());
        if (bah.isEmpty() && sjh.isEmpty()) {
            return null;
        }
        return sjh.isEmpty() ? bah : bah + ":" + sjh;
    }

    private String safeFilename(Scan scan) {
        String filename = scan.getFilename();
        if (filename == null || filename.isBlank()) {
            return "image-" + scan.getId();
        }

        StringBuilder sanitized = new StringBuilder(Math.min(filename.length(), MAX_FILENAME_LENGTH));
        for (int index = 0; index < filename.length() && sanitized.length() < MAX_FILENAME_LENGTH; index++) {
            char current = filename.charAt(index);
            if (current == '/' || current == '\\' || Character.isISOControl(current)) {
                sanitized.append('_');
            } else {
                sanitized.append(current);
            }
        }
        String result = sanitized.toString().trim();
        return result.isBlank() ? "image-" + scan.getId() : result;
    }

    private <T extends Throwable> boolean containsThrowable(Throwable root, Class<T> type) {
        return findThrowable(root, type, newIdentitySet()) != null;
    }

    private <T extends Throwable> T findThrowable(Throwable root,
                                                  Class<T> type,
                                                  Set<Throwable> visited) {
        if (root == null || !visited.add(root)) {
            return null;
        }
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        T inCause = findThrowable(root.getCause(), type, visited);
        if (inCause != null) {
            return inCause;
        }
        for (Throwable suppressed : root.getSuppressed()) {
            T found = findThrowable(suppressed, type, visited);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private OssOperationException findOssFailure(Throwable root, OssErrorType type) {
        return findOssFailure(root, type, newIdentitySet());
    }

    private OssOperationException findOssFailure(Throwable root,
                                                  OssErrorType type,
                                                  Set<Throwable> visited) {
        if (root == null || !visited.add(root)) {
            return null;
        }
        if (root instanceof OssOperationException exception && exception.getType() == type) {
            return exception;
        }
        OssOperationException inCause = findOssFailure(root.getCause(), type, visited);
        if (inCause != null) {
            return inCause;
        }
        for (Throwable suppressed : root.getSuppressed()) {
            OssOperationException found = findOssFailure(suppressed, type, visited);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isNotFoundOnly(Throwable failure, Set<Throwable> visited) {
        if (failure == null || !visited.add(failure)) {
            return false;
        }
        if (failure instanceof FileNotFoundException || failure instanceof NoSuchFileException) {
            return true;
        }
        if (failure instanceof OssOperationException exception) {
            return exception.getType() == OssErrorType.OSS_OBJECT_NOT_FOUND;
        }

        Throwable[] suppressed = failure.getSuppressed();
        if (suppressed.length > 0) {
            for (Throwable item : suppressed) {
                if (!isNotFoundOnly(item, visited)) {
                    return false;
                }
            }
            return true;
        }
        return failure.getCause() != null && isNotFoundOnly(failure.getCause(), visited);
    }

    private Set<Throwable> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
