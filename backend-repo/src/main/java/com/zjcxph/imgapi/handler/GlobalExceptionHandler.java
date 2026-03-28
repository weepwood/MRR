package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.pojo.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolationException(ConstraintViolationException e) {
        logger.warn(String.valueOf(e));
        Result<String> result = new Result<>(400, StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "参数校验失败", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
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
