package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 故障源快照 */
@TableName("analysis_fault_source")
public class AnalysisFaultSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisId;
    private Long faultId;
    private Long nodeId;
    private String nodeName;
    private String faultType;
    private String severity;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long v) { this.analysisId = v; }
    public Long getFaultId() { return faultId; }
    public void setFaultId(Long v) { this.faultId = v; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long v) { this.nodeId = v; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String v) { this.nodeName = v; }
    public String getFaultType() { return faultType; }
    public void setFaultType(String v) { this.faultType = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
}
