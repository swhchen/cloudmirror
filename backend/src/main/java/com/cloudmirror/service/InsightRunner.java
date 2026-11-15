package com.cloudmirror.service;

import com.cloudmirror.dto.InsightDtos;
import com.cloudmirror.service.analysis.CascadeAnalyzer;
import com.cloudmirror.service.analysis.CriticalNodeAnalyzer;
import com.cloudmirror.service.analysis.IsolationCutter;
import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.recovery.RecoveryPlanner;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 增强分析入口：把 E2（关键单点）、E3（隔离切口）、E4（恢复序列）打包给前端。
 *
 * <p>没有激活故障时只做结构性分析（E2），E3/E4 返回空。
 */
@Service
public class InsightRunner {

    private final ProjectService projectService;
    private final ValidationRunner validationRunner;
    private final RedundancyService redundancyService;
    private final FaultService faultService;
    private final CriticalNodeAnalyzer criticalNodeAnalyzer;
    private final IsolationCutter isolationCutter;
    private final RecoveryPlanner recoveryPlanner;
    private final CascadeAnalyzer cascadeAnalyzer;

    public InsightRunner(ProjectService projectService,
                         ValidationRunner validationRunner,
                         RedundancyService redundancyService,
                         FaultService faultService,
                         CriticalNodeAnalyzer criticalNodeAnalyzer,
                         IsolationCutter isolationCutter,
                         RecoveryPlanner recoveryPlanner,
                         CascadeAnalyzer cascadeAnalyzer) {
        this.projectService = projectService;
        this.validationRunner = validationRunner;
        this.redundancyService = redundancyService;
        this.faultService = faultService;
        this.criticalNodeAnalyzer = criticalNodeAnalyzer;
        this.isolationCutter = isolationCutter;
        this.recoveryPlanner = recoveryPlanner;
        this.cascadeAnalyzer = cascadeAnalyzer;
    }

    public InsightDtos.Response analyze(Long projectId) {
        projectService.require(projectId);
        ValidateContext ctx = validationRunner.loadContext(projectId);
        var criticalNodes = criticalNodeAnalyzer.analyze(ctx.nodes(), ctx.edges());
        List<ValidationModel.Group> groups = redundancyService.groupsOf(projectId);

        List<Fault> faults = faultService.activeFaults(projectId);
        var cascade = cascadeAnalyzer.analyze(ctx.nodes(), groups, faults);
        if (faults.isEmpty()) {
            return new InsightDtos.Response(criticalNodes, null, null, cascade,
                    "当前没有激活故障：只做结构性分析（关键单点）");
        }

        Set<Long> faultyIds = faults.stream().map(Fault::nodeId).collect(Collectors.toSet());
        var isolation = isolationCutter.propose(ctx.nodes(), ctx.edges(), faultyIds, 3);
        var sequence = recoveryPlanner.plan(ctx.nodes(), ctx.edges(), groups, faults);
        return new InsightDtos.Response(criticalNodes, isolation, sequence, cascade,
                "基于当前 " + faults.size() + " 个激活故障");
    }
}
