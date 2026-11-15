package com.cloudmirror.service.recovery;

/**
 * 备用节点候选。
 *
 * @param eligible 当前模型判定它满足切换前提（不代表生产环境已验证容量/网络/数据同步）
 */
public record StandbyCandidate(long nodeId,
                               String nodeName,
                               Integer priority,
                               boolean eligible,
                               String detail) {
}
