package com.cloudmirror.service.recovery;

import java.util.List;
import java.util.Map;

/**
 * 单个策略的评估结果。
 *
 * @param applicable        是否适用（不适用时仍给出评分，但永不推荐）
 * @param inapplicableReason 不适用的具体原因
 * @param impactAfterCount  动作执行后仍不可用（或受影响）的节点数
 * @param score             0–100 加权评分
 * @param scoreBreakdown    分项得分（0–1），便于前端展示"为什么是这个分"
 * @param failoverDetail    failover 的冗余组与备用节点明细（其它策略为 null）
 */
public record StrategyEvaluation(StrategyType type,
                                 String name,
                                 boolean applicable,
                                 String inapplicableReason,
                                 int estimatedRecoverySeconds,
                                 int impactAfterCount,
                                 double impactRatioAfter,
                                 Level cost,
                                 Level risk,
                                 double score,
                                 Map<String, Double> scoreBreakdown,
                                 List<String> advantages,
                                 List<String> disadvantages,
                                 List<String> prerequisites,
                                 FailoverDetail failoverDetail) {

    /**
     * failover 明细。
     */
    public record FailoverDetail(Long groupId,
                                 String groupName,
                                 Long primaryNodeId,
                                 String primaryNodeName,
                                 Long selectedStandbyNodeId,
                                 String selectedStandbyName,
                                 String selectionReason,
                                 List<StandbyCandidate> candidates,
                                 List<PrerequisiteCheck> checks) {
    }
}
