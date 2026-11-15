package com.cloudmirror.common;

/**
 * 统一返回结构。
 *
 * <pre>
 * { "code": 0, "message": "ok", "data": {...} }
 * </pre>
 *
 * <p>code = 0 表示成功；非 0 为业务错误码（见 {@link ErrorCode}）。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
