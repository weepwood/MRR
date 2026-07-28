package com.zjcxph.imgapi.validation;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class ScanRequestValidator implements ConstraintValidator<ValidScanRequest, ScanRequest> {

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    @Override
    public boolean isValid(ScanRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        valid &= validateSafeSegment(context, "brxh", request.getBrxh(), false);
        valid &= validateSafeSegment(context, "bah", request.getBah(), false);
        valid &= validateSafeSegment(context, "sjh", request.getSjh(), true);
        valid &= validateSafeSegment(context, "folder", request.getFolder(), false);
        valid &= validateSafeSegment(context, "filename", request.getFilename(), false);

        if (MedicalRecordCodeUtils.requiresSjhForBah(request.getBah())
                && (request.getSjh() == null || request.getSjh().isBlank())) {
            addViolation(context, "sjh", "病案号大于或等于 10000000 时必须提供上架号");
            valid = false;
        }

        return valid;
    }

    private boolean validateSafeSegment(ConstraintValidatorContext context,
                                        String field,
                                        String value,
                                        boolean optional) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        if (!value.equals(value.trim())) {
            addViolation(context, field, field + " 不能包含首尾空白");
            return false;
        }
        if (".".equals(value) || "..".equals(value)) {
            addViolation(context, field, field + " 不能是相对路径片段");
            return false;
        }
        if (value.endsWith(".") || value.endsWith(" ")) {
            addViolation(context, field, field + " 不能以点或空格结尾");
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '/' || current == '\\' || current == ':'
                    || current == '\0' || Character.isISOControl(current)) {
                addViolation(context, field, field + " 包含非法路径字符");
                return false;
            }
        }

        String baseName = value;
        int dotIndex = value.indexOf('.');
        if (dotIndex >= 0) {
            baseName = value.substring(0, dotIndex);
        }
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            addViolation(context, field, field + " 使用了 Windows 保留名称");
            return false;
        }
        return true;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
