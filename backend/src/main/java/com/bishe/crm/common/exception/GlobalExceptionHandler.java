/* 文件作用：将业务异常与框架异常转换为统一接口响应。 */
package com.bishe.crm.common.exception;

import com.bishe.crm.common.api.ApiResponse;
import com.bishe.crm.common.api.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将业务异常与框架异常转换为统一接口响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 保留业务异常的错误码与 HTTP 状态。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> business(BizException exception) {
        return error(exception.getErrorCode());
    }

    /** 将参数校验失败转换为统一错误响应。 */
    @ExceptionHandler(
        {
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
        }
    )
    public ResponseEntity<ApiResponse<Void>> validation(Exception exception) {
        return error(ErrorCode.VALIDATION_ERROR);
    }

    /** 处理参数解析或类型转换失败。 */
    @ExceptionHandler(
        {
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
        }
    )
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception exception) {
        return error(ErrorCode.BAD_REQUEST);
    }

    /** 返回统一的资源不存在响应。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound() {
        return error(ErrorCode.NOT_FOUND);
    }

    /** 返回不支持的 HTTP 方法响应。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed() {
        return error(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 返回不支持的请求媒体类型响应。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMediaType() {
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /** 隐藏数据库约束细节并返回数据冲突。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> dataConflict() {
        return error(ErrorCode.CONFLICT);
    }

    /** 将并发写入失败转换为冲突响应。 */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> concurrentWrite() {
        return error(ErrorCode.CONFLICT);
    }

    /** 隐藏连接细节并返回依赖不可用响应。 */
    @ExceptionHandler({ DataAccessResourceFailureException.class, QueryTimeoutException.class })
    public ResponseEntity<ApiResponse<Void>> databaseUnavailable() {
        return error(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    /** 返回统一的无权限响应。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden() {
        return error(ErrorCode.FORBIDDEN);
    }

    /** 返回统一的未登录响应。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized() {
        return error(ErrorCode.UNAUTHORIZED);
    }

    /** 记录未预期异常并返回通用服务错误。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(ErrorCode.INTERNAL_ERROR);
    }

    /** 按错误码构造 HTTP 响应。 */
    private ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ApiResponse.failure(code));
    }
}
