package com.cloudmirror.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常出口：所有异常都变成统一 Result，并带上可定位的 traceId。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(ErrorCode.toHttpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity.status(422).body(Result.fail(ErrorCode.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400)
                .body(Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误：" + e.getMostSpecificCause().getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        // 路径不存在：正常 404，不当作系统异常记录，避免日志噪音
        return ResponseEntity.status(404).body(Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception e) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        log.error("未处理异常 traceId={}", traceId, e);
        return ResponseEntity.status(500)
                .body(Result.fail(ErrorCode.SYSTEM_ERROR, "系统异常，请把 traceId 提供给管理员：" + traceId));
    }
}
