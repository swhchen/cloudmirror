package com.cloudmirror.service;

import com.cloudmirror.dto.AiDtos;
import com.cloudmirror.entity.Project;
import com.cloudmirror.service.propagation.AffectedNode;
import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PathHop;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.propagation.PropagationService;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 辅助诊断：把当前工程的故障与推演结果整理成 payload，交给 Python AI 服务；
 * Python 不可用时就地生成规则版诊断（source = local-fallback）。
 */
@Service
public class AiRunner {

    private final ProjectService projectService;
    private final FaultService faultService;
    private final ValidationRunner validationRunner;
    private final PropagationService propagationService;
    private final AiService aiService;

    public AiRunner(ProjectService projectService,
                    FaultService faultService,
                    ValidationRunner validationRunner,
                    PropagationService propagationService,
                    AiService aiService) {
        this.projectService = projectService;
        this.faultService = faultService;
        this.validationRunner = validationRunner;
        this.propagationService = propagationService;
        this.aiService = aiService;
    }

    public AiDtos.Response diagnose(Long projectId) {
        Project project = projectService.require(projectId);
        List<Fault> faults = faultService.activeFaults(projectId);
        ValidateContext ctx = validationRunner.loadContext(projectId);
        PropagationResult propagation = propagationService.propagate(ctx.nodes(), ctx.edges(), faults);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", project.getName());
        payload.put("nodeCount", ctx.nodes().size());
        payload.put("edgeCount", ctx.edges().size());
        payload.put("faults", faults.stream().map(f -> Map.of(
                "nodeName", f.nodeName(), "faultType", f.type(), "severity", f.severity())).toList());
        payload.put("affected", propagation.affectedNodes().stream().map(a -> Map.of(
                "nodeName", a.nodeName(),
                "impactLevel", a.impactLevel(),
                "distance", a.distance(),
                "path", pathText(a))).toList());
        payload.put("topology", ctx.nodes().stream()
                .map(n -> Map.of("name", n.name(), "kind", n.kind())).toList());

        Optional<AiService.AiResult> remote = aiService.diagnose(payload);
        if (remote.isPresent()) {
            return new AiDtos.Response(remote.get().source(), remote.get().content(), true);
        }
        return new AiDtos.Response("local-fallback", localText(faults, propagation), false);
    }

    private String pathText(AffectedNode node) {
        return node.path().stream().map(PathHop::nodeName).collect(Collectors.joining(" → "));
    }

    private String localText(List<Fault> faults, PropagationResult propagation) {
        if (faults.isEmpty()) {
            return "【根因判断】当前没有激活故障，无法诊断。\n"
                    + "【处置建议】先在节点上注入故障，再执行推演分析。\n"
                    + "【风险提示】AI 服务未连接，本结论由本地规则生成。";
        }
        String source = faults.stream()
                .map(f -> f.nodeName() + "（" + f.type() + "/" + f.severity() + "）")
                .collect(Collectors.joining("、"));

        List<String> direct = new ArrayList<>();
        List<String> indirect = new ArrayList<>();
        for (AffectedNode a : propagation.affectedNodes()) {
            (AffectedNode.DIRECT.equals(a.impactLevel()) ? direct : indirect).add(a.nodeName());
        }
        StringBuilder impact = new StringBuilder();
        if (!direct.isEmpty()) {
            impact.append("直接受影响：").append(String.join("、", direct)).append('\n');
        }
        if (!indirect.isEmpty()) {
            impact.append("间接受影响：").append(String.join("、", indirect)).append('\n');
        }
        if (impact.isEmpty()) {
            impact.append("无其它节点受影响\n");
        }

        return "【根因判断】" + source + " 发生异常，沿「被依赖 → 依赖方」方向扩散。\n\n"
                + "【影响范围】\n" + impact
                + "\n【处置建议】\n"
                + "1. 若该节点有主备冗余组，优先切备（恢复最快）。\n"
                + "2. 无冗余且故障为宕机/服务不可用时，尝试重启。\n"
                + "3. 若影响持续扩散且隔离不会切断全部入口，可先隔离止损。\n\n"
                + "【风险提示】AI 服务未连接（8010 端口），本结论由本地规则生成，非大模型输出。";
    }
}
