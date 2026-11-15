package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次恢复推荐 */
@TableName("recovery_plan")
public class RecoveryPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long analysisId;
    private String recommendedType;
    private String recommendedName;
    private String recommendationReason;
    private String weightsJson;
    private String estimationNotice;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long v) { this.analysisId = v; }
    public String getRecommendedType() { return recommendedType; }
    public void setRecommendedType(String v) { this.recommendedType = v; }
    public String getRecommendedName() { return recommendedName; }
    public void setRecommendedName(String v) { this.recommendedName = v; }
    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String v) { this.recommendationReason = v; }
    public String getWeightsJson() { return weightsJson; }
    public void setWeightsJson(String v) { this.weightsJson = v; }
    public String getEstimationNotice() { return estimationNotice; }
    public void setEstimationNotice(String v) { this.estimationNotice = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
