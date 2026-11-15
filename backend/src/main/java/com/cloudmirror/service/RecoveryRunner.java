package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.RecoveryDtos;
import com.cloudmirror.entity.PropagationAnalysis;
import com.cloudmirror.entity.RecoveryPlan;
import com.cloudmirror.entity.RecoveryStrategy;
import com.cloudmirror.mapper.PropagationAnalysisMapper;
import com.cloudmirror.mapper.RecoveryPlanMapper;
import com.cloudmirror.mapper.RecoveryStrategyMapper;
import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.propagation.PropagationService;
import com.cloudmirror.service.recovery.RecoveryResult;
import com.cloudmirror.service.recovery.RecoveryService;
import com.cloudmirror.service.recovery.StrategyEvaluation;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidationModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 恢复推荐：调算法 → 落库（方案 + 三策略，明细 JSON 化） */
@Service
public class RecoveryRunner {

    private final ProjectService projectService;
    private final ValidationRunner validationRunner;
    private final FaultService faultService;
    private final RedundancyService redundancyService;
    private final PropagationService propagationService;
    private final RecoveryService recoveryService;
    private final RecoveryPlanMapper planMapper;
    private final RecoveryStrategyMapper strategyMapper;
    private final PropagationAnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    public RecoveryRunner(ProjectService projectService,
                          ValidationRunner validationRunner,
                          FaultService faultService,
                          RedundancyService redundancyService,
                          PropagationService propagationService,
                          RecoveryService recoveryService,
                          RecoveryPlanMapper planMapper,
                          RecoveryStrategyMapper strategyMapper,
                          PropagationAnalysisMapper analysisMapper,
                          ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.validationRunner = validationRunner;
        this.faultService = faultService;
        this.redundancyService = redundancyService;
        this.propagationService = propagationService;
        this.recoveryService = recoveryService;
        this.planMapper = planMapper;
        this.strategyMapper = strategyMapper;
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecoveryDtos.Response recommend(Long projectId, Long analysisId) {
        projectService.require(projectId);
        List<Fault> faults = faultService.activeFaults(projectId);
        if (faults.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前没有激活的故障，无法生成恢复方案");
        }
        Long effectiveAnalysisId = analysisId;
        if (effectiveAnalysisId != null) {
            PropagationAnalysis analysis = analysisMapper.selectById(effectiveAnalysisId);
            if (analysis == null || !analysis.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "分析记录不存在或不属于该工程：" + effectiveAnalysisId);
            }
        }

        ValidateContext ctx = validationRunner.loadContext(projectId);
        PropagationResult propagation = propagationService.propagate(ctx.nodes(), ctx.edges(), faults);
        List<ValidationModel.Group> groups = redundancyService.groupsOf(projectId);
        RecoveryResult result = recoveryService.recommend(ctx.nodes(), ctx.edges(), groups, faults, propagation);

        RecoveryPlan plan = new RecoveryPlan();
        plan.setProjectId(projectId);
        plan.setAnalysisId(effectiveAnalysisId);
        plan.setRecommendedType(result.recommendedType() == null ? null : result.recommendedType().code());
        plan.setRecommendedName(result.recommendedName());
        plan.setRecommendationReason(cut(result.recommendationReason(), 600));
        plan.setWeightsJson(cut(json(result.weights()), 300));
        plan.setEstimationNotice(cut(result.estimationNotice(), 600));
        plan.setCreatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        for (StrategyEvaluation strategy : result.strategies()) {
            RecoveryStrategy row = new RecoveryStrategy();
            row.setPlanId(plan.getId());
            row.setStrategyType(strategy.type().code());
            row.setStrategyName(strategy.name());
            row.setApplicable(strategy.applicable());
            row.setInapplicableReason(cut(strategy.inapplicableReason(), 400));
            row.setEstimatedRecoverySeconds(strategy.estimatedRecoverySeconds());
            row.setImpactAfterCount(strategy.impactAfterCount());
            row.setImpactRatioAfter(BigDecimal.valueOf(strategy.impactRatioAfter()));
            row.setCostLevel(strategy.cost().name());
            row.setRiskLevel(strategy.risk().name());
            row.setScore(BigDecimal.valueOf(strategy.score()));
            row.setBreakdownJson(cut(json(strategy.scoreBreakdown()), 500));
            if (strategy.failoverDetail() != null) {
                var d = strategy.failoverDetail();
                row.setRedundancyGroupId(d.groupId());
                row.setRedundancyGroupName(cut(d.groupName(), 100));
                row.setPrimaryNodeId(d.primaryNodeId());
                row.setSelectedStandbyNodeId(d.selectedStandbyNodeId());
                row.setSelectedStandbyName(cut(d.selectedStandbyName(), 100));
                row.setSelectionReason(cut(d.selectionReason(), 300));
                row.setCandidatesJson(cut(json(d.candidates()), 1500));
                row.setChecksJson(cut(json(d.checks()), 2000));
            }
            strategyMapper.insert(row);
        }

        return new RecoveryDtos.Response(plan.getId(), result);
    }

    /** 历史恢复方案（按时间倒序，含每条策略摘要） */
    public List<RecoveryDtos.PlanVO> listPlans(Long projectId, int limit) {
        projectService.require(projectId);
        int size = Math.min(50, Math.max(1, limit));
        List<RecoveryPlan> plans = planMapper.selectList(Wrappers.<RecoveryPlan>lambdaQuery()
                .eq(RecoveryPlan::getProjectId, projectId)
                .orderByDesc(RecoveryPlan::getId)
                .last("limit " + size));
        List<RecoveryDtos.PlanVO> result = new ArrayList<>();
        for (RecoveryPlan plan : plans) {
            List<RecoveryStrategy> rows = strategyMapper.selectList(Wrappers.<RecoveryStrategy>lambdaQuery()
                    .eq(RecoveryStrategy::getPlanId, plan.getId())
                    .orderByAsc(RecoveryStrategy::getId));
            List<RecoveryDtos.StrategyRow> strategies = rows.stream()
                    .map(r -> new RecoveryDtos.StrategyRow(r.getStrategyType(), r.getStrategyName(),
                            r.getApplicable(), r.getInapplicableReason(), r.getEstimatedRecoverySeconds(),
                            r.getCostLevel(), r.getRiskLevel(),
                            r.getScore() == null ? null : r.getScore().doubleValue()))
                    .toList();
            result.add(new RecoveryDtos.PlanVO(plan.getId(), plan.getAnalysisId(),
                    plan.getRecommendedType(), plan.getRecommendedName(), plan.getRecommendationReason(),
                    plan.getCreatedAt(), strategies));
        }
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
