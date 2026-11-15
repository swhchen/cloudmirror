package com.cloudmirror.service.propagation;

import java.util.List;

/**
 * 一个受影响节点。
 *
 * @param impactClass      CERTAIN（必崩，强度 ≥ 阈值）/ POSSIBLE（可能受影响）
 * @param pathStrength     传播强度：沿路径累乘边权，按节点容量余量放大，再按依赖冗余度打折
 * @param dependencyRatio  依赖冗余度：该节点受影响的依赖数 / 总依赖数（0–1）
 * @param arrivalMillis    故障传播到该节点的估算耗时（毫秒，等于路径上各边延迟之和）
 */
public record AffectedNode(long nodeId,
                           String nodeName,
                           String impactLevel,
                           int distance,
                           long sourceFaultId,
                           long sourceNodeId,
                           double pathStrength,
                           double dependencyRatio,
                           String impactClass,
                           long arrivalMillis,
                           List<PathHop> path) {

    public static final String DIRECT = "DIRECT";
    public static final String INDIRECT = "INDIRECT";

    public static final String CERTAIN = "CERTAIN";
    public static final String POSSIBLE = "POSSIBLE";

    public static AffectedNode of(long nodeId, String nodeName, int distance,
                                  long sourceFaultId, long sourceNodeId,
                                  double pathStrength, double dependencyRatio,
                                  long arrivalMillis, List<PathHop> path) {
        return new AffectedNode(nodeId, nodeName,
                distance == 1 ? DIRECT : INDIRECT,
                distance, sourceFaultId, sourceNodeId,
                Math.round(pathStrength * 1000) / 1000.0,
                Math.round(dependencyRatio * 1000) / 1000.0,
                pathStrength >= 0.6 ? CERTAIN : POSSIBLE,
                arrivalMillis,
                List.copyOf(path));
    }
}
