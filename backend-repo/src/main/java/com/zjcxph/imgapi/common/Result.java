package com.zjcxph.imgapi.common;



import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一响应结果封装类
 * @param <T> 数据类型
 */
@Data
public class Result<T> {
    
    // ==================== 字段定义 ====================
    private Integer code;
    private String message;
    private T data;
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



    // ==================== 静态工厂方法 - 成功响应 ====================
        
    /**
     * 成功响应(无数据)
     * 使用示例: Result.success()
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }
    
    /**
     * 成功响应(带自定义消息)
     * 使用示例: Result.success("操作成功")
     */
    public static <T> Result<T> success(String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, null);
    }
    
    /**
     * 成功响应(带数据) - 自动推断泛型类型
     * 使用示例: Result.success(user) -> Result<User>
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }
    
    /**
     * 成功响应(带消息和数据) - 自动推断泛型类型
     * 使用示例: Result.success("查询成功", user) -> Result<User>
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }
    


    // ==================== 静态工厂方法 - 失败响应 ====================
        
    /**
     * 失败响应(默认 400)
     * 使用示例: Result.fail()
     */
    public static <T> Result<T> fail() {
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), ResultCode.BAD_REQUEST.getMessage(), null);
    }
    
    /**
     * 失败响应(带消息)
     * 使用示例: Result.fail("参数错误")
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }
    
    /**
     * 失败响应(带状态码和消息)
     * 使用示例: Result.fail(422, "验证失败")
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
    
    /**
     * 未授权响应
     * 使用示例: Result.unauthorized("请先登录")
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(ResultCode.UNAUTHORIZED.getCode(), message, null);
    }
    
    /**
     * 禁止访问响应
     * 使用示例: Result.forbidden("权限不足")
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(ResultCode.FORBIDDEN.getCode(), message, null);
    }
    
    /**
     * 资源不存在响应
     * 使用示例: Result.notFound("用户不存在")
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(ResultCode.NOT_FOUND.getCode(), message, null);
    }
    
    /**
     * 服务器内部错误响应
     * 使用示例: Result.error("系统异常")
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
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
        return ResultCode.SUCCESS.getCode() == (this.code != null ? this.code : -1);
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}