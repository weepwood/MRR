package com.zjcxph.imgapi.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功", HttpStatus.OK),
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或 Token 已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "权限不足", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // 业务错误码
    USER_NOT_FOUND(1001, "用户不存在", HttpStatus.NOT_FOUND),
    USER_DISABLED(1002, "账号已被禁用", HttpStatus.FORBIDDEN),
    PASSWORD_WRONG(1003, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    OSS_UPLOAD_FAIL(2001, "OSS 上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    MIGRATION_FAIL(2002, "迁移失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    /**
     * 将响应体业务码解析为标准 HTTP 状态码。
     *
     * <p>标准 HTTP 状态码直接透传；已登记的业务码使用枚举中的映射；
     * 未登记的非标准错误码按 500 处理，避免业务失败继续以 HTTP 200 暴露。</p>
     */
    public static HttpStatus resolveHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        HttpStatus standardStatus = HttpStatus.resolve(code);
        if (standardStatus != null) {
            return standardStatus;
        }

        return Arrays.stream(values())
                .filter(resultCode -> resultCode.code == code)
                .map(ResultCode::getHttpStatus)
                .findFirst()
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
