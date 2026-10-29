/* 文件作用：携带统一错误码的业务异常。 */
package com.bishe.crm.common.exception;

import com.bishe.crm.common.api.ErrorCode;

/** 携带统一错误码的业务异常。 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 根据错误码构造业务异常。 */
    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** 返回异常携带的业务错误码。 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
