package com.cloudmirror.common;

/**
 * 业务错误码与 HTTP 状态码映射。
 *
 * <p>约定：业务码 = HTTP 状态 × 100 + 序号，便于一眼看出该回什么 HTTP 状态。
 */
public final class ErrorCode {

    /** 参数 / 结构非法 → 400 */
    public static final int BAD_REQUEST = 40001;
    /** 资源不存在 / 跨工程引用 → 404 */
    public static final int NOT_FOUND = 40401;
    /** 冲突：重名、重复边、重复入组 → 409 */
    public static final int CONFLICT = 40901;
    /** 字段校验失败 → 422 */
    public static final int VALIDATION_FAILED = 42201;
    /** 系统异常兜底 → 500 */
    public static final int SYSTEM_ERROR = 50000;

    private ErrorCode() {
    }

    public static int toHttpStatus(int businessCode) {
        int status = businessCode / 100;
        return switch (status) {
            case 400, 404, 409, 422 -> status;
            default -> 500;
        };
    }
}
