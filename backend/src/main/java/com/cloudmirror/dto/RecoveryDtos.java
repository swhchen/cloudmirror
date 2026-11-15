package com.cloudmirror.dto;

import com.cloudmirror.service.recovery.RecoveryResult;

import java.time.LocalDateTime;
import java.util.List;

/** 恢复推荐 DTO */
public final class RecoveryDtos {

    private RecoveryDtos() {
    }

    public record Response(Long planId, RecoveryResult result) {
    }

    /** 历史方案（回看用，从库里重建） */
    public record PlanVO(Long id,
                         Long analysisId,
                         String recommendedType,
                         String recommendedName,
                         String recommendationReason,
                         LocalDateTime createdAt,
                         List<StrategyRow> strategies) {
    }

    public record StrategyRow(String type,
                              String name,
                              Boolean applicable,
                              String inapplicableReason,
                              Integer estimatedRecoverySeconds,
                              String costLevel,
                              String riskLevel,
                              Double score) {
    }
}
