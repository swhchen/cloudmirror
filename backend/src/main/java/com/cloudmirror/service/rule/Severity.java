package com.cloudmirror.service.rule;

/**
 * 问题级别。
 *
 * <p>ERROR = 确定跑不起来；WARN = 能跑但有隐患；INFO = 收拾一下更好。
 */
public enum Severity {
    ERROR, WARN, INFO
}
