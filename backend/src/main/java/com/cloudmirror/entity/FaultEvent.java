package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 故障事件（激活 / 已恢复，不删除历史） */
@TableName("fault_event")
public class FaultEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceNodeId;
    private String faultType;
    private String severity;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime recoveredAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(Long v) { this.sourceNodeId = v; }
    public String getFaultType() { return faultType; }
    public void setFaultType(String v) { this.faultType = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(LocalDateTime v) { this.recoveredAt = v; }
}
