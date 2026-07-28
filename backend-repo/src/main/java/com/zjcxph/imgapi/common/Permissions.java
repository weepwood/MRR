package com.zjcxph.imgapi.common;

import java.util.List;

/**
 * 系统权限代码的唯一后端目录。
 */
public final class Permissions {

    public static final String RECORD_MANAGE = "record:manage";
    public static final String RECORD_EDIT = "record:edit";
    public static final String RECORD_READ = "record:read";
    public static final String RECORD_DOWNLOAD = "record:download";
    public static final String RECORD_PDF_EXPORT = "record:pdf:export";
    public static final String OCR_READ = "ocr:read";
    public static final String OCR_RUN = "ocr:run";
    public static final String OCR_CONFIG = "ocr:config";
    public static final String CLASSIFICATION_READ = "classification:read";
    public static final String CLASSIFICATION_REVIEW = "classification:review";
    public static final String CLASSIFICATION_BATCH_REVIEW = "classification:batch-review";
    public static final String CLASSIFICATION_OVERRIDE = "classification:override";
    public static final String SEARCH_READ = "search:read";
    public static final String STATISTICS_READ = "statistics:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_MANAGE = "role:manage";
    public static final String ROLE_READ = "role:read";
    public static final String LOG_READ = "log:read";
    public static final String SYSTEM_ERROR_MANAGE = "system:error:manage";
    public static final String SYSTEM_ERROR_READ = "system:error:read";
    public static final String SYSTEM_MANAGE = "system:manage";
    public static final String SYSTEM_READ = "system:read";
    public static final String TEST_READ = "test:read";

    public static final List<String> ALL_PERMISSIONS = List.of(
            RECORD_MANAGE,
            RECORD_EDIT,
            RECORD_READ,
            RECORD_DOWNLOAD,
            RECORD_PDF_EXPORT,
            OCR_READ,
            OCR_RUN,
            OCR_CONFIG,
            CLASSIFICATION_READ,
            CLASSIFICATION_REVIEW,
            CLASSIFICATION_BATCH_REVIEW,
            CLASSIFICATION_OVERRIDE,
            SEARCH_READ,
            STATISTICS_READ,
            USER_MANAGE,
            ROLE_MANAGE,
            ROLE_READ,
            LOG_READ,
            SYSTEM_ERROR_MANAGE,
            SYSTEM_ERROR_READ,
            SYSTEM_MANAGE,
            SYSTEM_READ,
            TEST_READ
    );

    private Permissions() {
    }
}
