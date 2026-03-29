package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.zjcxph.imgapi.exception.BusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<String>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        Result<String> result = new Result<>(e.getCode(), StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "业务处理失败", null);
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(e.getCode());
        } catch (IllegalArgumentException ex) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolationException(ConstraintViolationException e) {
        logger.warn(String.valueOf(e));
        Result<String> result = new Result<>(400, StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "参数校验失败", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn(String.valueOf(e));
        Result<String> result = new Result<>(400, StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "请求参数错误", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<String>> handleIllegalStateException(IllegalStateException e) {
        logger.warn(String.valueOf(e));
        Result<String> result = new Result<>(403, StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "无权限访问", null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e) {
        logger.error(String.valueOf(e));
        Result<String> stringResult = new Result<>();
        stringResult.message(StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "服务器异常");
        stringResult.code(500).data(null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(stringResult);
    }
}
