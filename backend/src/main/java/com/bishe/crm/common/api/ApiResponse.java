package com.bishe.crm.common.api;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "操作成功", data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode error) {
        return failure(error, error.message(), null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode error, String message, T data) {
        return new ApiResponse<>(error.code(), message, data);
    }
}
