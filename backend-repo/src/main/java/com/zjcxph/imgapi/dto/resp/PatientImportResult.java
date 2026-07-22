package com.zjcxph.imgapi.dto.resp;

import java.util.List;

/**
 * 患者文件校验或正式导入结果。
 */
public record PatientImportResult(
        String fileName,
        String encoding,
        boolean dryRun,
        boolean canImport,
        int totalRows,
        int validRows,
        int insertedRows,
        int duplicateRows,
        int errorRows,
        boolean errorsTruncated,
        List<PatientImportError> errors
) {
}
