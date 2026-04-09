package com.zjcxph.imgapi.common;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
public class Result<T> {
    private Integer code;
    private final LocalDateTime timestamp;
    private String message;
    private T data;
    @Setter
    private Integer total; // 用于分页时返回总记录数

    public static <E> Result<E> success(String message){
        return new Result<>(200, message,null);
    }

    public static <E> Result<E> fail(String message){
        return new Result<>(400, message, null);
    }

    public Result() {
        this.timestamp = LocalDateTime.now();
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public Result(Integer code, String message, T data, Integer total) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.total = total;
        this.timestamp = LocalDateTime.now();
    }

    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }

    public  Result<T> message(String message) {
        this.message = message;
        return this;
    }

    public  Result<T> data(T data) {
        this.data = data;
        return this;
    }

}