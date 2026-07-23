package com.zjcxph.imgapi.dto.resp;

import java.util.List;

public record DataExchangeImportResult(
        String dataset,
        String fileName,
        String encoding,
        boolean dryRun,
        boolean canImport,
        int totalRows,
        int validRows,
        int insertedRows,
        int updatedRows,
        int duplicateRows,
        int errorRows,
        boolean errorsTruncated,
        List<DataExchangeImportError> errors
) {
}
