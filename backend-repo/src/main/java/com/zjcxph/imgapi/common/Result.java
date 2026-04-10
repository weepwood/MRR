package com.zjcxph.imgapi.common;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 统一响应结果封装类
 * @param <T> 数据类型
 */
@Getter
public class Result<T> {
    
    // ==================== 常用状态码常量 ====================
    public static final int SUCCESS_CODE = 200;
    public static final int BAD_REQUEST_CODE = 400;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FORBIDDEN_CODE = 403;
    public static final int NOT_FOUND_CODE = 404;
    public static final int INTERNAL_ERROR_CODE = 500;
    
    // ==================== 字段定义 ====================
    private Integer code;
    private String message;
    private T data;
    @Setter
    private Integer total; // 分页总记录数
    private final LocalDateTime timestamp;

    // ==================== 构造方法 ====================
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

    // ==================== 静态工厂方法 - 成功响应 ====================
        
    /**
     * 成功响应(无数据)
     * 使用示例: Result.success()
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> success() {
        return (Result<T>) new Result<>(SUCCESS_CODE, "操作成功", null);
    }
    
    /**
     * 成功响应(带消息)
     * 使用示例: Result.success("操作成功")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> success(String message) {
        return (Result<T>) new Result<>(SUCCESS_CODE, message, null);
    }
    
    /**
     * 成功响应(带数据) - 自动推断泛型类型
     * 使用示例: Result.success(user) -> Result<User>
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data);
    }
    
    /**
     * 成功响应(带消息和数据) - 自动推断泛型类型
     * 使用示例: Result.success("查询成功", user) -> Result<User>
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }
    
    /**
     * 成功响应(分页数据) - 自动推断泛型类型
     * 使用示例: Result.successPage(users, total) -> Result<List<User>>
     */
    public static <T> Result<T> successPage(T data, Integer total) {
        Result<T> result = new Result<>(SUCCESS_CODE, "查询成功", data);
        result.setTotal(total);
        return result;
    }

    // ==================== 静态工厂方法 - 失败响应 ====================
        
    /**
     * 失败响应(默认 400)
     * 使用示例: Result.fail()
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail() {
        return (Result<T>) new Result<>(BAD_REQUEST_CODE, "操作失败", null);
    }
    
    /**
     * 失败响应(带消息)
     * 使用示例: Result.fail("参数错误")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(String message) {
        return (Result<T>) new Result<>(BAD_REQUEST_CODE, message, null);
    }
    
    /**
     * 失败响应(带状态码和消息)
     * 使用示例: Result.fail(422, "验证失败")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(Integer code, String message) {
        return (Result<T>) new Result<>(code, message, null);
    }
    
    /**
     * 未授权响应
     * 使用示例: Result.unauthorized("请先登录")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> unauthorized(String message) {
        return (Result<T>) new Result<>(UNAUTHORIZED_CODE, message, null);
    }
    
    /**
     * 禁止访问响应
     * 使用示例: Result.forbidden("权限不足")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> forbidden(String message) {
        return (Result<T>) new Result<>(FORBIDDEN_CODE, message, null);
    }
    
    /**
     * 资源不存在响应
     * 使用示例: Result.notFound("用户不存在")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> notFound(String message) {
        return (Result<T>) new Result<>(NOT_FOUND_CODE, message, null);
    }
    
    /**
     * 服务器内部错误响应
     * 使用示例: Result.error("系统异常")
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(String message) {
        return (Result<T>) new Result<>(INTERNAL_ERROR_CODE, message, null);
    }

    // ==================== 链式调用方法 ====================
    
    /**
     * 设置状态码（链式调用）
     */
    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }

    /**
     * 设置消息（链式调用）
     */
    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    /**
     * 设置数据（链式调用）
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    // ==================== 工具方法 ====================
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return SUCCESS_CODE == (this.code != null ? this.code : -1);
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}