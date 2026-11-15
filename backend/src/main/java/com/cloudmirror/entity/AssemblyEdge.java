package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 节点连线：五类边，端口端点 0 表示该边不涉及具体端口 */
@TableName("asm_edge")
public class AssemblyEdge {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String edgeKind;
    private Long sourcePortId;
    private Long targetPortId;
    private Integer bandwidthMbps;
    private Integer latencyMs;
    private Boolean strong;
    private BigDecimal weight;
    private String description;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(Long sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public Long getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(Long targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getEdgeKind() {
        return edgeKind;
    }

    public void setEdgeKind(String edgeKind) {
        this.edgeKind = edgeKind;
    }

    public Long getSourcePortId() {
        return sourcePortId;
    }

    public void setSourcePortId(Long sourcePortId) {
        this.sourcePortId = sourcePortId;
    }

    public Long getTargetPortId() {
        return targetPortId;
    }

    public void setTargetPortId(Long targetPortId) {
        this.targetPortId = targetPortId;
    }

    public Integer getBandwidthMbps() {
        return bandwidthMbps;
    }

    public void setBandwidthMbps(Integer bandwidthMbps) {
        this.bandwidthMbps = bandwidthMbps;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Boolean getStrong() {
        return strong;
    }

    public void setStrong(Boolean strong) {
        this.strong = strong;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
