package com.cloudmirror.service.recovery;

/**
 * 前置校验项（failover 用）：每一项都要能说清"为什么过/为什么不过"。
 */
public record PrerequisiteCheck(String checkName, boolean passed, String detail) {
}
