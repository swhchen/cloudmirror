package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.AnalysisDtos;
import com.cloudmirror.dto.PageDtos;
import com.cloudmirror.entity.AnalysisAffectedNode;
import com.cloudmirror.entity.AnalysisFaultSource;
import com.cloudmirror.entity.AnalysisPathStep;
import com.cloudmirror.entity.PropagationAnalysis;
import com.cloudmirror.mapper.AnalysisAffectedNodeMapper;
import com.cloudmirror.mapper.AnalysisFaultSourceMapper;
import com.cloudmirror.mapper.AnalysisPathStepMapper;
import com.cloudmirror.mapper.PropagationAnalysisMapper;
import com.cloudmirror.service.propagation.AffectedNode;
import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PathHop;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.propagation.PropagationService;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.analysis.CascadeAnalyzer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 传播推演：调算法 → 落库（含故障源/受影响节点/路径快照）→ 支持历史回看 */
@Service
public class AnalysisRunner {

    private final ProjectService projectService;
    private final ValidationRunner validationRunner;
    private final FaultService faultService;
    private final PropagationService propagationService;
    private final CascadeAnalyzer cascadeAnalyzer;
    private final RedundancyService redundancyService;
    private final PropagationAnalysisMapper analysisMapper;
    private final AnalysisFaultSourceMapper sourceMapper;
    private final AnalysisAffectedNodeMapper affectedMapper;
    private final AnalysisPathStepMapper pathMapper;

    public AnalysisRunner(ProjectService projectService,
                          ValidationRunner validationRunner,
                          FaultService faultService,
                          PropagationService propagationService,
                          CascadeAnalyzer cascadeAnalyzer,
                          RedundancyService redundancyService,
                          PropagationAnalysisMapper analysisMapper,
                          AnalysisFaultSourceMapper sourceMapper,
                          AnalysisAffectedNodeMapper affectedMapper,
                          AnalysisPathStepMapper pathMapper) {
        this.projectService = projectService;
        this.validationRunner = validationRunner;
        this.faultService = faultService;
        this.propagationService = propagationService;
        this.cascadeAnalyzer = cascadeAnalyzer;
        this.redundancyService = redundancyService;
        this.analysisMapper = analysisMapper;
        this.sourceMapper = sourceMapper;
        this.affectedMapper = affectedMapper;
        this.pathMapper = pathMapper;
    }

    @Transactional
    public AnalysisDtos.Response analyze(Long projectId) {
        return analyze(projectId, null);
    }

    /** @param windowSeconds 可选时间窗：只落库该秒数内的波及结果 */
    @Transactional
    public AnalysisDtos.Response analyze(Long projectId, Integer windowSeconds) {
        projectService.require(projectId);
        List<Fault> faults = faultService.activeFaults(projectId);
        if (faults.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前没有激活的故障，无法执行推演");
        }
        ValidateContext ctx = validationRunner.loadContext(projectId);
        PropagationResult result = propagationService.propagate(ctx.nodes(), ctx.edges(), faults, windowSeconds);

        PropagationAnalysis analysis = new PropagationAnalysis();
        analysis.setProjectId(projectId);
        analysis.setFaultSourceCount(result.faultSources().size());
        analysis.setDirectCount(result.directCount());
        analysis.setIndirectCount(result.indirectCount());
        analysis.setTotalAffectedCount(result.affectedNodes().size());
        analysis.setTotalNodeCount(result.totalNodeCount());
        analysis.setImpactRatio(BigDecimal.valueOf(result.impactRatio()));
        analysis.setSummary(result.summary());
        analysis.setWindowSeconds(windowSeconds);
        var cascade = cascadeAnalyzer.analyze(ctx.nodes(), redundancyService.groupsOf(projectId), faults);
        analysis.setCascadeSummary(cascade.summary());
        analysis.setAnalysisTime(LocalDateTime.now());
        analysisMapper.insert(analysis);

        persistSources(analysis.getId(), result);
        persistAffected(analysis.getId(), result);

        return new AnalysisDtos.Response(analysis.getId(), result);
    }

    public PageDtos<AnalysisDtos.HistoryItem> list(Long projectId, int page, int size) {
        projectService.require(projectId);
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        long total = analysisMapper.selectCount(Wrappers.<PropagationAnalysis>lambdaQuery()
                .eq(PropagationAnalysis::getProjectId, projectId));
        List<AnalysisDtos.HistoryItem> items = analysisMapper.selectList(
                        Wrappers.<PropagationAnalysis>lambdaQuery()
                                .eq(PropagationAnalysis::getProjectId, projectId)
                                .orderByDesc(PropagationAnalysis::getId)
                                .last("limit " + s + " offset " + ((p - 1) * s)))
                .stream()
                .map(a -> new AnalysisDtos.HistoryItem(a.getId(), a.getAnalysisTime(), a.getSummary(),
                        a.getFaultSourceCount(), a.getDirectCount(), a.getIndirectCount(),
                        a.getTotalAffectedCount(),
                        a.getImpactRatio() == null ? 0.0 : a.getImpactRatio().doubleValue(),
                        a.getWindowSeconds(), a.getCascadeSummary()))
                .toList();
        return new PageDtos<>(items, total, p, s);
    }

    public AnalysisDtos.Detail detail(Long projectId, Long analysisId) {
        projectService.require(projectId);
        PropagationAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null || !analysis.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分析记录不存在或不属于该工程：" + analysisId);
        }

        List<AnalysisDtos.SourceVO> sources = sourceMapper.selectList(
                        Wrappers.<AnalysisFaultSource>lambdaQuery()
                                .eq(AnalysisFaultSource::getAnalysisId, analysisId)
                                .orderByAsc(AnalysisFaultSource::getId))
                .stream()
                .map(s -> new AnalysisDtos.SourceVO(s.getFaultId(), s.getNodeId(), s.getNodeName(),
                        s.getFaultType(), s.getSeverity()))
                .toList();

        List<AnalysisAffectedNode> affectedRows = affectedMapper.selectList(
                Wrappers.<AnalysisAffectedNode>lambdaQuery()
                        .eq(AnalysisAffectedNode::getAnalysisId, analysisId)
                        .orderByAsc(AnalysisAffectedNode::getId));
        List<Long> affectedIds = affectedRows.stream().map(AnalysisAffectedNode::getId).toList();
        Map<Long, List<AnalysisPathStep>> stepsByAffected = new LinkedHashMap<>();
        if (!affectedIds.isEmpty()) {
            pathMapper.selectList(Wrappers.<AnalysisPathStep>lambdaQuery()
                            .in(AnalysisPathStep::getAffectedNodeId, affectedIds)
                            .orderByAsc(AnalysisPathStep::getStepIndex))
                    .forEach(s -> stepsByAffected
                            .computeIfAbsent(s.getAffectedNodeId(), k -> new ArrayList<>()).add(s));
        }
        List<AnalysisDtos.AffectedVO> affected = affectedRows.stream().map(a -> {
            List<AnalysisDtos.StepVO> path = stepsByAffected.getOrDefault(a.getId(), List.of()).stream()
                    .map(s -> new AnalysisDtos.StepVO(s.getStepIndex(), s.getNodeId(), s.getNodeName(),
                            s.getEdgeId(), s.getCallerPort(), s.getCalleePort()))
                    .toList();
            return new AnalysisDtos.AffectedVO(a.getNodeId(), a.getNodeName(), a.getImpactLevel(),
                    a.getDistance(), a.getSourceFaultId(), a.getSourceNodeId(), path);
        }).toList();

        return new AnalysisDtos.Detail(analysis.getId(), analysis.getAnalysisTime(), analysis.getSummary(),
                analysis.getFaultSourceCount(), analysis.getDirectCount(), analysis.getIndirectCount(),
                analysis.getTotalNodeCount(),
                analysis.getImpactRatio() == null ? 0.0 : analysis.getImpactRatio().doubleValue(),
                analysis.getWindowSeconds(), analysis.getCascadeSummary(),
                sources, affected);
    }

    private void persistSources(Long analysisId, PropagationResult result) {
        for (Fault f : result.faultSources()) {
            AnalysisFaultSource source = new AnalysisFaultSource();
            source.setAnalysisId(analysisId);
            source.setFaultId(f.id());
            source.setNodeId(f.nodeId());
            source.setNodeName(f.nodeName());
            source.setFaultType(f.type());
            source.setSeverity(f.severity());
            sourceMapper.insert(source);
        }
    }

    private void persistAffected(Long analysisId, PropagationResult result) {
        for (AffectedNode node : result.affectedNodes()) {
            AnalysisAffectedNode row = new AnalysisAffectedNode();
            row.setAnalysisId(analysisId);
            row.setNodeId(node.nodeId());
            row.setNodeName(node.nodeName());
            row.setImpactLevel(node.impactLevel());
            row.setDistance(node.distance());
            row.setSourceFaultId(node.sourceFaultId());
            row.setSourceNodeId(node.sourceNodeId());
            affectedMapper.insert(row);

            int index = 0;
            for (PathHop hop : node.path()) {
                AnalysisPathStep step = new AnalysisPathStep();
                step.setAffectedNodeId(row.getId());
                step.setStepIndex(index++);
                step.setNodeId(hop.nodeId());
                step.setNodeName(hop.nodeName());
                step.setEdgeId(hop.edgeId());
                step.setCallerPort(hop.callerPort());
                step.setCalleePort(hop.calleePort());
                pathMapper.insert(step);
            }
        }
    }
}
