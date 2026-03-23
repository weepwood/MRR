package com.zjcxph.imgapi.pojo;

import java.time.LocalDateTime;

public class Result<T> {
    private Integer code;
    private final LocalDateTime timestamp;
    private String message;
    private T data;
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

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}