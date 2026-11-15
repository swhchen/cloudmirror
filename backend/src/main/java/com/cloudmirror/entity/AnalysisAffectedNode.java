package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 受影响节点快照 */
@TableName("analysis_affected_node")
public class AnalysisAffectedNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisId;
    private Long nodeId;
    private String nodeName;
    private String impactLevel;
    private Integer distance;
    private Long sourceFaultId;
    private Long sourceNodeId;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long v) { this.analysisId = v; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long v) { this.nodeId = v; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String v) { this.nodeName = v; }
    public String getImpactLevel() { return impactLevel; }
    public void setImpactLevel(String v) { this.impactLevel = v; }
    public Integer getDistance() { return distance; }
    public void setDistance(Integer v) { this.distance = v; }
    public Long getSourceFaultId() { return sourceFaultId; }
    public void setSourceFaultId(Long v) { this.sourceFaultId = v; }
    public Long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(Long v) { this.sourceNodeId = v; }
}
