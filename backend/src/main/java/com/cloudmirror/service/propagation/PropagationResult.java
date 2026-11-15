package com.cloudmirror.service.propagation;

import java.util.List;

/**
 * 一次传播分析的结论。
 *
 * @param certainCount  必崩节点数（传播强度 ≥ 阈值）
 * @param possibleCount 可能受影响节点数（强度低于阈值）
 */
public record PropagationResult(List<Fault> faultSources,
                                List<AffectedNode> affectedNodes,
                                int directCount,
                                int indirectCount,
                                int certainCount,
                                int possibleCount,
                                int totalNodeCount,
                                double impactRatio,
                                String summary) {
}
