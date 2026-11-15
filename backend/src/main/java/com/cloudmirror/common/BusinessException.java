package com.cloudmirror.common;

/**
 * 业务异常：Service 层只管抛，统一由 {@link GlobalExceptionHandler} 转成 Result。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(ErrorCode.BAD_REQUEST, message);
    }

    public int getCode() {
        return code;
    }
}
