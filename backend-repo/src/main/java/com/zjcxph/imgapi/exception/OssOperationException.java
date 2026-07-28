package com.zjcxph.imgapi.exception;

import lombok.Getter;

@Getter
public class OssOperationException extends BusinessException {

    private final OssErrorType type;

    public OssOperationException(OssErrorType type, Throwable cause) {
        super(type.getResultCode().getCode(), type.getResultCode().getMessage(), cause);
        this.type = type;
    }
}
