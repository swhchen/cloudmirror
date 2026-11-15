package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** 策略评估（候选/检查明细以 JSON 存，避免过度拆表） */
@TableName("recovery_strategy")
public class RecoveryStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String strategyType;
    private String strategyName;
    private Boolean applicable;
    private String inapplicableReason;
    private Integer estimatedRecoverySeconds;
    private Integer impactAfterCount;
    private BigDecimal impactRatioAfter;
    private String costLevel;
    private String riskLevel;
    private BigDecimal score;
    private String breakdownJson;
    private Long redundancyGroupId;
    private String redundancyGroupName;
    private Long primaryNodeId;
    private Long selectedStandbyNodeId;
    private String selectedStandbyName;
    private String selectionReason;
    private String candidatesJson;
    private String checksJson;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long v) { this.planId = v; }
    public String getStrategyType() { return strategyType; }
    public void setStrategyType(String v) { this.strategyType = v; }
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String v) { this.strategyName = v; }
    public Boolean getApplicable() { return applicable; }
    public void setApplicable(Boolean v) { this.applicable = v; }
    public String getInapplicableReason() { return inapplicableReason; }
    public void setInapplicableReason(String v) { this.inapplicableReason = v; }
    public Integer getEstimatedRecoverySeconds() { return estimatedRecoverySeconds; }
    public void setEstimatedRecoverySeconds(Integer v) { this.estimatedRecoverySeconds = v; }
    public Integer getImpactAfterCount() { return impactAfterCount; }
    public void setImpactAfterCount(Integer v) { this.impactAfterCount = v; }
    public BigDecimal getImpactRatioAfter() { return impactRatioAfter; }
    public void setImpactRatioAfter(BigDecimal v) { this.impactRatioAfter = v; }
    public String getCostLevel() { return costLevel; }
    public void setCostLevel(String v) { this.costLevel = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { this.score = v; }
    public String getBreakdownJson() { return breakdownJson; }
    public void setBreakdownJson(String v) { this.breakdownJson = v; }
    public Long getRedundancyGroupId() { return redundancyGroupId; }
    public void setRedundancyGroupId(Long v) { this.redundancyGroupId = v; }
    public String getRedundancyGroupName() { return redundancyGroupName; }
    public void setRedundancyGroupName(String v) { this.redundancyGroupName = v; }
    public Long getPrimaryNodeId() { return primaryNodeId; }
    public void setPrimaryNodeId(Long v) { this.primaryNodeId = v; }
    public Long getSelectedStandbyNodeId() { return selectedStandbyNodeId; }
    public void setSelectedStandbyNodeId(Long v) { this.selectedStandbyNodeId = v; }
    public String getSelectedStandbyName() { return selectedStandbyName; }
    public void setSelectedStandbyName(String v) { this.selectedStandbyName = v; }
    public String getSelectionReason() { return selectionReason; }
    public void setSelectionReason(String v) { this.selectionReason = v; }
    public String getCandidatesJson() { return candidatesJson; }
    public void setCandidatesJson(String v) { this.candidatesJson = v; }
    public String getChecksJson() { return checksJson; }
    public void setChecksJson(String v) { this.checksJson = v; }
}
