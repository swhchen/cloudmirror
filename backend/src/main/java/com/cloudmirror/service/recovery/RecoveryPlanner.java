package com.cloudmirror.service.recovery;

import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * E4 多动作恢复序列。
 *
 * <p>不再只推荐「一个动作」，而是为每个故障节点挑一个恢复动作（重启 / 切备 / 隔离），
 * 枚举所有组合，按「总耗时 + 30×成本 + 30×风险」选最优，并给出动作执行顺序与次优方案。
 *
 * <p>约束：隔离不能切断所有用户入口的依赖；每个故障节点必须被某个动作处理。
 * 组合数 ≤ 3^故障数（实际场景故障数很少），无随机数、结果确定。
 */
@Service
public class RecoveryPlanner {

    private static final int T_RESTART = 120;
    private static final int T_FAILOVER = 60;
    private static final int T_ISOLATE = 30;

    private static final int COST_RESTART = 1;
    private static final int COST_FAILOVER = 3;
    private static final int COST_ISOLATE = 1;

    private static final int RISK_RESTART = 1;
    private static final int RISK_FAILOVER = 0;
    /** 隔离会把节点永久下线（不恢复服务），风险权重必须显著高于重启/切备 */
    private static final int RISK_ISOLATE = 8;

    public record SequenceAction(long faultId,
                                 long nodeId,
                                 String nodeName,
                                 StrategyType type,
                                 int seconds,
                                 int costUnits,
                                 int riskUnits) {
    }

    public record SequencePlan(List<SequenceAction> actions,
                               int totalSeconds,
                               int totalCostUnits,
                               int totalRiskUnits,
                               int score,
                               String description) {
    }

    public record PlannerResult(SequencePlan best,
                                List<SequencePlan> alternatives,
                                String summary) {
    }

    public PlannerResult plan(List<ValidationModel.Node> nodes,
                              List<ValidationModel.Edge> edges,
                              List<ValidationModel.Group> groups,
                              List<Fault> faults) {
        Map<Long, String> names = new HashMap<>();
        nodes.forEach(n -> names.put(n.id(), n.name()));
        if (faults.isEmpty()) {
            return new PlannerResult(null, List.of(), "当前没有激活故障，无需恢复序列");
        }

        Set<Long> faulty = faults.stream().map(Fault::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<Long>> dependsBySource = new HashMap<>();
        for (ValidationModel.Edge e : edges) {
            if (ValidationModel.DEPENDS_ON.equals(e.kind())) {
                dependsBySource.computeIfAbsent(e.sourceNodeId(), k -> new ArrayList<>()).add(e.targetNodeId());
            }
        }
        List<ValidationModel.Node> entries = nodes.stream()
                .filter(ValidationModel.Node::isEntry)
                .toList();

        List<List<SequenceAction>> options = new ArrayList<>();
        for (Fault f : faults) {
            List<SequenceAction> list = new ArrayList<>();
            if (isRestartable(f.type())) {
                list.add(new SequenceAction(f.id(), f.nodeId(), names.getOrDefault(f.nodeId(), "?"),
                        StrategyType.RESTART_SERVICE, T_RESTART, COST_RESTART, RISK_RESTART));
            }
            if (hasStandby(f.nodeId(), groups, faulty)) {
                list.add(new SequenceAction(f.id(), f.nodeId(), names.getOrDefault(f.nodeId(), "?"),
                        StrategyType.FAILOVER, T_FAILOVER, COST_FAILOVER, RISK_FAILOVER));
            }
            list.add(new SequenceAction(f.id(), f.nodeId(), names.getOrDefault(f.nodeId(), "?"),
                    StrategyType.ISOLATE_NODE, T_ISOLATE, COST_ISOLATE, RISK_ISOLATE));
            options.add(list);
        }

        List<SequencePlan> plans = new ArrayList<>();
        enumerate(options, 0, new ArrayList<>(), plans, entries, dependsBySource);

        plans.sort(Comparator.comparingInt(SequencePlan::score)
                .thenComparingInt(p -> p.actions().size())
                .thenComparing(SequencePlan::description));

        if (plans.isEmpty()) {
            return new PlannerResult(null, List.of(),
                    "没有可行的恢复动作组合：故障类型不可重启、无合格备用节点，隔离又会切断业务入口");
        }
        SequencePlan best = plans.get(0);
        List<SequencePlan> alternatives = plans.stream().skip(1).limit(2).toList();
        String summary = String.format("推荐 %d 步恢复：%s（预计 %d 秒，成本 %d，风险 %d）",
                best.actions().size(), best.description(), best.totalSeconds(),
                best.totalCostUnits(), best.totalRiskUnits());
        return new PlannerResult(best, alternatives, summary);
    }

    // ---------- 内部 ----------

    private boolean isRestartable(String faultType) {
        return "node_down".equals(faultType) || "service_unavailable".equals(faultType);
    }

    private boolean hasStandby(Long nodeId, List<ValidationModel.Group> groups, Set<Long> faulty) {
        for (ValidationModel.Group g : groups) {
            boolean isPrimary = g.members().stream()
                    .anyMatch(m -> m.nodeId() != null && m.nodeId().equals(nodeId) && ValidationModel.PRIMARY.equals(m.role()));
            if (!isPrimary) {
                continue;
            }
            boolean failoverType = ValidationModel.ACTIVE_STANDBY.equals(g.type())
                    || ValidationModel.MANUAL_STANDBY.equals(g.type());
            if (!failoverType) {
                continue;
            }
            boolean standbyOk = g.members().stream()
                    .anyMatch(m -> ValidationModel.STANDBY.equals(m.role()) && !faulty.contains(m.nodeId()));
            if (standbyOk) {
                return true;
            }
        }
        return false;
    }

    private void enumerate(List<List<SequenceAction>> options, int index,
                           List<SequenceAction> picked, List<SequencePlan> out,
                           List<ValidationModel.Node> entries,
                           Map<Long, List<Long>> dependsBySource) {
        if (index == options.size()) {
            buildPlan(picked, out, entries, dependsBySource);
            return;
        }
        for (SequenceAction action : options.get(index)) {
            picked.add(action);
            enumerate(options, index + 1, picked, out, entries, dependsBySource);
            picked.remove(picked.size() - 1);
        }
    }

    private void buildPlan(List<SequenceAction> actions, List<SequencePlan> out,
                           List<ValidationModel.Node> entries,
                           Map<Long, List<Long>> dependsBySource) {
        Set<Long> isolated = actions.stream()
                .filter(a -> a.type() == StrategyType.ISOLATE_NODE)
                .map(SequenceAction::nodeId)
                .collect(Collectors.toCollection(HashSet::new));

        // 隔离不能把某个入口的依赖全切断
        for (ValidationModel.Node entry : entries) {
            if (isolated.contains(entry.id())) {
                return;
            }
            List<Long> deps = dependsBySource.getOrDefault(entry.id(), List.of());
            if (!deps.isEmpty() && deps.stream().allMatch(isolated::contains)) {
                return;
            }
        }

        List<SequenceAction> ordered = actions.stream()
                .sorted(Comparator.comparingInt(SequenceAction::seconds)
                        .thenComparingLong(SequenceAction::nodeId))
                .toList();

        int seconds = ordered.stream().mapToInt(SequenceAction::seconds).sum();
        int cost = ordered.stream().mapToInt(SequenceAction::costUnits).sum();
        int risk = ordered.stream().mapToInt(SequenceAction::riskUnits).sum();
        int score = seconds + 30 * cost + 30 * risk;

        String description = ordered.stream()
                .map(a -> String.format("%s %s(%ds)", verb(a.type()), a.nodeName(), a.seconds()))
                .collect(Collectors.joining(" → "));

        out.add(new SequencePlan(List.copyOf(ordered), seconds, cost, risk, score, description));
    }

    private String verb(StrategyType type) {
        return switch (type) {
            case RESTART_SERVICE -> "重启";
            case FAILOVER -> "切备";
            case ISOLATE_NODE -> "隔离";
        };
    }
}
