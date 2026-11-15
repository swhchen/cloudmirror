package com.cloudmirror.service.rule;

import java.util.List;

/**
 * 一条没通过的问题。规则只返回"没通过的项"，通过与否由计数量决定。
 *
 * @param ruleCode   规则编号，如 R04
 * @param ruleName   规则名，如 CPU 超配
 * @param severity   级别
 * @param message    中文说明，必须带具体数字
 * @param suggestion 修复建议
 * @param nodeIds    涉及节点（前端据此在画布上标红并定位）
 * @param edgeIds    涉及连线
 */
public record CheckItem(String ruleCode,
                        String ruleName,
                        Severity severity,
                        String message,
                        String suggestion,
                        List<Long> nodeIds,
                        List<Long> edgeIds) {

    public static CheckItem of(ValidateRule rule,
                               String message,
                               String suggestion,
                               List<Long> nodeIds,
                               List<Long> edgeIds) {
        return new CheckItem(rule.code(), rule.name(), rule.severity(),
                message, suggestion,
                nodeIds == null ? List.of() : List.copyOf(nodeIds),
                edgeIds == null ? List.of() : List.copyOf(edgeIds));
    }

    public static CheckItem of(String code, String name, Severity severity,
                               String message, String suggestion,
                               List<Long> nodeIds, List<Long> edgeIds) {
        return new CheckItem(code, name, severity,
                message, suggestion,
                nodeIds == null ? List.of() : List.copyOf(nodeIds),
                edgeIds == null ? List.of() : List.copyOf(edgeIds));
    }
}
