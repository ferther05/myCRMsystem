package com.bishe.crm.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数不正确"),
    VALIDATION_ERROR(40001, HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "请先登录"),
    LOGIN_FAILED(40101, HttpStatus.UNAUTHORIZED, "账号或密码错误，或账号不可用"),
    TOKEN_INVALID(40102, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "没有权限执行此操作"),
    PASSWORD_CHANGE_REQUIRED(40301, HttpStatus.FORBIDDEN, "请先修改初始密码"),
    PASSWORD_INCORRECT(40002, HttpStatus.BAD_REQUEST, "原密码不正确"),
    PASSWORD_UNCHANGED(40003, HttpStatus.BAD_REQUEST, "新密码不能与原密码相同"),
    ACCOUNT_RULE(40004, HttpStatus.BAD_REQUEST, "账号配置不符合规则，请检查角色、团队或账号状态"),
    OWNERSHIP_PENDING(40901, HttpStatus.CONFLICT, "该账号仍关联业务数据，请完成业务交接后再调整角色或团队"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据已变化或与现有记录冲突，请刷新后重试"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理请求"),
    DEPENDENCY_UNAVAILABLE(50300, HttpStatus.SERVICE_UNAVAILABLE, "基础服务暂不可用");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
