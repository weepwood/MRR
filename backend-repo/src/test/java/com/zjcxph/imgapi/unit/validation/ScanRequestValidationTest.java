package com.zjcxph.imgapi.unit.validation;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScanRequest 扫描记录输入校验")
class ScanRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("合法短病案号不强制补零或要求上架号")
    void acceptsSupportedLegacyIdentityWithoutForcingEightDigits() {
        ScanRequest request = validRequest();
        request.setBah("123");
        request.setSjh(null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("高位病案号必须同时提供上架号")
    void requiresSjhForHighBah() {
        ScanRequest request = validRequest();
        request.setBah("10000000");
        request.setSjh(null);

        assertThat(messagesFor(validator.validate(request), "sjh"))
                .contains("病案号大于或等于 10000000 时必须提供上架号");
    }

    @Test
    @DisplayName("拒绝目录穿越、反斜杠和 Windows 保留文件名")
    void rejectsUnsafePathSegments() {
        ScanRequest request = validRequest();
        request.setFolder("../archive");
        request.setFilename("CON.jpg");
        request.setBrxh("605\\746");

        Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

        assertThat(messagesFor(violations, "folder")).anyMatch(message -> message.contains("非法路径字符"));
        assertThat(messagesFor(violations, "filename")).anyMatch(message -> message.contains("Windows 保留名称"));
        assertThat(messagesFor(violations, "brxh")).anyMatch(message -> message.contains("非法路径字符"));
    }

    @Test
    @DisplayName("拒绝空白可选编号和首尾空白")
    void rejectsWhitespaceOnlyAndUntrimmedSegments() {
        ScanRequest request = validRequest();
        request.setSjh("   ");
        request.setFilename(" image.jpg");

        Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

        assertThat(messagesFor(violations, "sjh")).anyMatch(message -> message.contains("空白"));
        assertThat(messagesFor(violations, "filename")).anyMatch(message -> message.contains("首尾空白"));
    }

    @Test
    @DisplayName("拒绝非法病案类型、负页码和未知上传状态")
    void rejectsOutOfRangeValues() {
        ScanRequest request = validRequest();
        request.setBtype(16);
        request.setPages(-1);
        request.setUploadFlag(2);

        Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

        assertThat(messagesFor(violations, "btype")).contains("病案类型不能大于 15");
        assertThat(messagesFor(violations, "pages")).contains("页码不能小于 0");
        assertThat(messagesFor(violations, "uploadFlag")).contains("上传状态只能是 0 或 1");
    }

    @Test
    @DisplayName("合法高位病案号和中文文件名可通过校验")
    void acceptsHighBahWithSjhAndUnicodeFilename() {
        ScanRequest request = validRequest();
        request.setBah("10000001");
        request.setSjh("SJH-2026-0001");
        request.setFilename("病案首页-01.jpg");

        assertThat(validator.validate(request)).isEmpty();
    }

    private static ScanRequest validRequest() {
        ScanRequest request = new ScanRequest();
        request.setBrxh("605746");
        request.setBah("00789508");
        request.setFilename("image-01.jpg");
        request.setBtype(1);
        request.setPages(1);
        request.setOpenerNo("operator-01");
        request.setUploadDate("2026-07-28");
        request.setUploadFlag(1);
        request.setFolder("BA01-2026");
        return request;
    }

    private static Set<String> messagesFor(Set<ConstraintViolation<ScanRequest>> violations, String property) {
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(property))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
