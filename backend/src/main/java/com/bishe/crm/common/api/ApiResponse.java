/* 文件作用：统一接口响应，封装业务码、提示和返回数据。 */
package com.bishe.crm.common.api;

/** 统一接口响应，封装业务码、提示和返回数据。 */
public record ApiResponse<T>(int code, String message, T data) {
    /** 封装成功响应。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "操作成功", data);
    }

    /** 封装指定错误码的失败响应。 */
    public static <T> ApiResponse<T> failure(ErrorCode error) {
        return failure(error, error.message(), null);
    }

    /** 封装指定错误码的失败响应。 */
    public static <T> ApiResponse<T> failure(ErrorCode error, String message, T data) {
        return new ApiResponse<>(error.code(), message, data);
    }
}
