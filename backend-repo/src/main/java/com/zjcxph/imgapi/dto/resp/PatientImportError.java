package com.zjcxph.imgapi.dto.resp;

/**
 * 患者导入单行校验错误。
 * value 在服务层已经完成脱敏，禁止放入完整身份证号。
 */
public record PatientImportError(
        int rowNumber,
        String field,
        String message,
        String value
) {
}
