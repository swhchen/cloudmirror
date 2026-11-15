package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 校验明细 */
@TableName("asm_check_item")
public class CheckItemRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private String ruleCode;
    private String severity;
    private Boolean passed;
    private String message;
    private String suggestion;
    private String nodeIds;
    private String edgeIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(String nodeIds) {
        this.nodeIds = nodeIds;
    }

    public String getEdgeIds() {
        return edgeIds;
    }

    public void setEdgeIds(String edgeIds) {
        this.edgeIds = edgeIds;
    }
}
