package com.cloudmirror.service.propagation;

/**
 * 传播路径上的一跳。
 *
 * <p>路径按「故障源 → … → 受影响节点」排列，第一跳是故障源自身（edgeId = null）。
 *
 * @param nodeId     本跳节点
 * @param nodeName   节点名（快照）
 * @param edgeId     本跳指向上一跳的那条 depends_on 边
 * @param callerPort 上一跳（依赖方）使用的端口
 * @param calleePort 本跳（被依赖方）的端口
 */
public record PathHop(long nodeId, String nodeName, Long edgeId, Long callerPort, Long calleePort) {

    public static PathHop origin(long nodeId, String nodeName) {
        return new PathHop(nodeId, nodeName, null, null, null);
    }
}
