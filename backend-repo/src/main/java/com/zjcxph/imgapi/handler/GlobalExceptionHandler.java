package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.pojo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e){
        logger.error(String.valueOf(e));
        Result<String> stringResult = new Result<>();
        // 判断是否有错误信息，如果没有返回默认 "服务器异常"
        stringResult.message(StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "服务器异常");
        stringResult.code(500).data(null);
        return stringResult;
    }
}
