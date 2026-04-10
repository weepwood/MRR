package com.zjcxph.imgapi.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "账号已被禁用"),
    PASSWORD_WRONG(1003, "用户名或密码错误"),
    OSS_UPLOAD_FAIL(2001, "OSS 上传失败"),
    MIGRATION_FAIL(2002, "迁移失败");

    private final int code;
    private final String message;
}
