package com.zjcxph.imgapi.dto.resp;

public record DataExchangeImportError(
        int rowNumber,
        String field,
        String message,
        String value
) {
}
