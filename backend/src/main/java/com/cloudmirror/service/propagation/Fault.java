package com.cloudmirror.service.propagation;

/**
 * 一个激活的故障事件（传播分析的输入）。
 *
 * @param id       故障事件 ID（多源去重时用于打破平局：ID 小的优先）
 * @param nodeId   故障所在节点
 * @param nodeName 节点名（快照，节点改名后历史仍可读）
 * @param type     故障类型，如 node_down
 * @param severity 严重程度
 */
public record Fault(long id, long nodeId, String nodeName, String type, String severity) {
}
