package com.cloudmirror.dto;

import com.cloudmirror.service.rule.ValidateResult;

import java.time.LocalDateTime;
import java.util.List;

/** 校验接口的返回结构 */
public final class ValidateDtos {

    private ValidateDtos() {
    }

    /** 一次新校验：落库后的 runId + 完整结论 */
    public record Response(Long runId, ValidateResult result) {
    }

    /** 历史校验回看 */
    public record LatestVO(Long runId,
                           Boolean runnable,
                           Integer score,
                           Integer errorCount,
                           Integer warnCount,
                           Integer infoCount,
                           String summary,
                           LocalDateTime createdAt,
                           List<ItemVO> items) {
    }

    public record ItemVO(Long id,
                         String ruleCode,
                         String severity,
                         Boolean passed,
                         String message,
                         String suggestion,
                         List<Long> nodeIds,
                         List<Long> edgeIds) {
    }
}
