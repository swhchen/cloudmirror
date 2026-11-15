package com.cloudmirror.service.recovery;

import java.util.List;
import java.util.Map;

/**
 * 一次恢复推荐的结果。
 *
 * @param strategies          三类策略的完整评估（顺序固定：重启 → 切备 → 隔离）
 * @param recommendedType     推荐策略类型；没有适用策略时为 null
 * @param recommendationReason 推荐理由（含分数对比）
 * @param weights             评分权重（合计 1.00，可解释）
 * @param estimationNotice    估算口径说明（提醒这是模型估算，不是生产实测）
 */
public record RecoveryResult(List<StrategyEvaluation> strategies,
                             StrategyType recommendedType,
                             String recommendedName,
                             String recommendationReason,
                             Map<String, Double> weights,
                             String estimationNotice) {
}
