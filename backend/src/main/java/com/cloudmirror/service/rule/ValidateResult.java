package com.cloudmirror.service.rule;

import java.util.List;

/**
 * 一次校验的结论。
 *
 * @param runnable   是否可运行（没有 ERROR 即可运行）
 * @param score      可运行性评分，0–100
 * @param items      未通过的项，按 级别 → 规则编号 排序
 * @param summary    一句话结论
 * @param ruleStatus 每条规则的通过状态（前端问题面板按规则分组用）
 */
public record ValidateResult(boolean runnable,
                             int score,
                             int errorCount,
                             int warnCount,
                             int infoCount,
                             String summary,
                             List<CheckItem> items,
                             List<RuleStatus> ruleStatus) {

    /**
     * 单条规则的状态。
     *
     * @param issueCount 该规则报出的问题数（0 表示通过）
     */
    public record RuleStatus(String code, String name, Severity severity, boolean passed, int issueCount) {
    }
}
