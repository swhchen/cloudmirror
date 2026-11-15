package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 传播路径步骤 */
@TableName("analysis_path_step")
public class AnalysisPathStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long affectedNodeId;
    private Integer stepIndex;
    private Long nodeId;
    private String nodeName;
    private Long edgeId;
    private Long callerPort;
    private Long calleePort;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getAffectedNodeId() { return affectedNodeId; }
    public void setAffectedNodeId(Long v) { this.affectedNodeId = v; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer v) { this.stepIndex = v; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long v) { this.nodeId = v; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String v) { this.nodeName = v; }
    public Long getEdgeId() { return edgeId; }
    public void setEdgeId(Long v) { this.edgeId = v; }
    public Long getCallerPort() { return callerPort; }
    public void setCallerPort(Long v) { this.callerPort = v; }
    public Long getCalleePort() { return calleePort; }
    public void setCalleePort(Long v) { this.calleePort = v; }
}
