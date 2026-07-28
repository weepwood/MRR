package com.zjcxph.imgapi.exception;

import com.zjcxph.imgapi.common.ResultCode;

public enum OssErrorType {

    OSS_NOT_CONFIGURED(ResultCode.OSS_NOT_CONFIGURED),
    OSS_UNAVAILABLE(ResultCode.OSS_UNAVAILABLE),
    OSS_UNAUTHORIZED(ResultCode.OSS_UNAUTHORIZED),
    OSS_OBJECT_NOT_FOUND(ResultCode.OSS_OBJECT_NOT_FOUND),
    OSS_INTEGRITY_MISMATCH(ResultCode.OSS_INTEGRITY_MISMATCH),
    OSS_OPERATION_FAILED(ResultCode.OSS_OPERATION_FAILED);

    private final ResultCode resultCode;

    OssErrorType(ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
