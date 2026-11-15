package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 传播分析汇总 */
@TableName("propagation_analysis")
public class PropagationAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer faultSourceCount;
    private Integer directCount;
    private Integer indirectCount;
    private Integer totalAffectedCount;
    private Integer totalNodeCount;
    private BigDecimal impactRatio;
    private String summary;
    private Integer windowSeconds;
    private String cascadeSummary;
    private LocalDateTime analysisTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Integer getFaultSourceCount() { return faultSourceCount; }
    public void setFaultSourceCount(Integer v) { this.faultSourceCount = v; }
    public Integer getDirectCount() { return directCount; }
    public void setDirectCount(Integer v) { this.directCount = v; }
    public Integer getIndirectCount() { return indirectCount; }
    public void setIndirectCount(Integer v) { this.indirectCount = v; }
    public Integer getTotalAffectedCount() { return totalAffectedCount; }
    public void setTotalAffectedCount(Integer v) { this.totalAffectedCount = v; }
    public Integer getTotalNodeCount() { return totalNodeCount; }
    public void setTotalNodeCount(Integer v) { this.totalNodeCount = v; }
    public BigDecimal getImpactRatio() { return impactRatio; }
    public void setImpactRatio(BigDecimal v) { this.impactRatio = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public Integer getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Integer v) { this.windowSeconds = v; }
    public String getCascadeSummary() { return cascadeSummary; }
    public void setCascadeSummary(String v) { this.cascadeSummary = v; }
    public LocalDateTime getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(LocalDateTime v) { this.analysisTime = v; }
}
