package com.cloudmirror.service.recovery;

import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 恢复决策（算法②）：三类策略的适用性判定 + 五维加权评分 + 推荐。
 *
 * <p>评分权重（合计 1.00，写死在代码里，答辩可直接指出来）：
 * 恢复时间 0.25、影响范围 0.20、资源成本 0.15、操作风险 0.20、业务恢复 0.20。
 * 参考恢复时间 600 秒。
 *
 * <p>判定顺序：先判适用性（不适用仍给分，但永不推荐），再在适用策略里取最高分；
 * 同分按 重启 → 切备 → 隔离 的固定顺序打破平局。
 *
 * <p>无随机数、不调用模型，同样输入永远同样输出。
 */
@Service
public class RecoveryService {

    /** 参考恢复时间（秒）：600 秒及以上记 0 分 */
    public static final double REFERENCE_SECONDS = 600.0;

    private static final String NODE_DOWN = "node_down";
    private static final String SERVICE_UNAVAILABLE = "service_unavailable";

    private static final Map<String, Double> WEIGHTS = weights();

    private static Map<String, Double> weights() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("时间分", 0.25);
        map.put("影响范围分", 0.20);
        map.put("资源成本分", 0.15);
        map.put("操作风险分", 0.20);
        map.put("业务恢复分", 0.20);
        return java.util.Collections.unmodifiableMap(map);
    }

    public RecoveryResult recommend(List<ValidationModel.Node> nodes,
                                    List<ValidationModel.Edge> edges,
                                    List<ValidationModel.Group> groups,
                                    List<Fault> faults,
                                    PropagationResult propagation) {
        Map<Long, ValidationModel.Node> nodeById = new LinkedHashMap<>();
        nodes.forEach(n -> nodeById.put(n.id(), n));

        List<Fault> activeFaults = faults.stream()
                .filter(f -> nodeById.containsKey(f.nodeId()))
                .sorted(Comparator.comparingLong(Fault::id))
                .toList();
        Set<Long> faultyIds = new LinkedHashSet<>();
        activeFaults.forEach(f -> faultyIds.add(f.nodeId()));

        Set<Long> affectedIds = new LinkedHashSet<>();
        if (propagation != null) {
            propagation.affectedNodes().forEach(a -> affectedIds.add(a.nodeId()));
        }

        Map<Long, List<Long>> dependsBySource = new HashMap<>();
        edges.stream()
                .filter(e -> ValidationModel.DEPENDS_ON.equals(e.kind()))
                .forEach(e -> dependsBySource
                        .computeIfAbsent(e.sourceNodeId(), k -> new ArrayList<>())
                        .add(e.targetNodeId()));

        List<ValidationModel.Node> entries = nodes.stream()
                .filter(ValidationModel.Node::isEntry)
                .toList();

        int total = nodes.size();
        int faultCount = faultyIds.size();

        List<StrategyEvaluation> strategies = new ArrayList<>();
        strategies.add(evaluateRestart(activeFaults, faultCount, total, entries, dependsBySource));
        strategies.add(evaluateFailover(nodes, groups, activeFaults, affectedIds, faultCount, total,
                entries, dependsBySource));
        strategies.add(evaluateIsolate(faultyIds, affectedIds, faultCount, total, entries, dependsBySource));

        StrategyEvaluation recommended = null;
        for (StrategyEvaluation s : strategies) {
            if (s.applicable() && (recommended == null || s.score() > recommended.score())) {
                recommended = s;
            }
        }

        String reason = buildReason(strategies, recommended);
        return new RecoveryResult(strategies,
                recommended == null ? null : recommended.type(),
                recommended == null ? null : recommended.name(),
                reason, WEIGHTS, estimationNotice());
    }

    // ---------- 三类策略 ----------

    private StrategyEvaluation evaluateRestart(List<Fault> faults, int faultCount, int total,
                                               List<ValidationModel.Node> entries,
                                               Map<Long, List<Long>> dependsBySource) {
        boolean restartable = faults.stream()
                .anyMatch(f -> NODE_DOWN.equals(f.type()) || SERVICE_UNAVAILABLE.equals(f.type()));
        int seconds = 120 + 60 * faultCount;
        double business = businessScore(entries, dependsBySource, Set.of());

        return build(StrategyType.RESTART_SERVICE, restartable,
                restartable ? null : "当前故障类型不是 node_down / service_unavailable，重启无法修复",
                seconds, 0, total, Level.LOW, Level.MEDIUM, business,
                List.of("不依赖冗余结构，任何节点都能执行", "耗时最短，影响范围最小"),
                List.of("重启期间该节点完全不可用", "若故障是网络/资源问题，重启可能无效"),
                List.of("存在 node_down / service_unavailable 类故障", "节点有自动拉起机制"),
                null);
    }

    private StrategyEvaluation evaluateFailover(List<ValidationModel.Node> nodes,
                                                List<ValidationModel.Group> groups,
                                                List<Fault> faults,
                                                Set<Long> affectedIds,
                                                int faultCount, int total,
                                                List<ValidationModel.Node> entries,
                                                Map<Long, List<Long>> dependsBySource) {
        int seconds = 60 + 30 * faultCount;
        String reason = "故障节点不在任何主备冗余组中，或组内没有合格备用节点";

        StrategyEvaluation.FailoverDetail detail = null;
        boolean applicable = false;

        for (long faultyNodeId : faults.stream().map(Fault::nodeId).distinct().sorted().toList()) {
            StrategyEvaluation.FailoverDetail candidate = evaluateFailoverFor(faultyNodeId, nodes, groups,
                    affectedIds, dependsBySource);
            if (candidate != null && candidate.selectedStandbyNodeId() != null) {
                detail = candidate;
                applicable = true;
                break;
            }
            if (detail == null && candidate != null) {
                detail = candidate;
            }
        }

        if (faults.isEmpty()) {
            reason = "当前没有激活故障源，没有可切换的主节点";
        }

        double business = businessScore(entries, dependsBySource, Set.of());
        return build(StrategyType.FAILOVER, applicable, applicable ? null : reason,
                seconds, 0, total, Level.HIGH, Level.LOW, business,
                List.of("主备切换通常秒级完成，业务中断短", "风险低，不破坏现有数据"),
                List.of("资源成本高（备用节点平时空转）", "必须依赖真实的显式冗余关系"),
                List.of("故障节点是冗余组的主节点", "存在满足全部前置条件的备用节点"),
                detail);
    }

    private StrategyEvaluation evaluateIsolate(Set<Long> faultyIds, Set<Long> affectedIds,
                                               int faultCount, int total,
                                               List<ValidationModel.Node> entries,
                                               Map<Long, List<Long>> dependsBySource) {
        Set<Long> unavailable = new LinkedHashSet<>(faultyIds);
        unavailable.addAll(affectedIds);

        int seconds = 30 + 15 * faultCount;
        double business = businessScore(entries, dependsBySource, unavailable);
        // 隔离只是止损：节点仍然下线，业务恢复要打折，否则它会靠"快"永远赢
        double adjustedBusiness = business * 0.6;
        boolean applicable = business >= 1.0;
        String reason = applicable ? null
                : "隔离后会有用户入口失去全部可用依赖，业务直接中断";

        return build(StrategyType.ISOLATE_NODE, applicable, reason,
                seconds, unavailable.size(), total, Level.LOW,
                applicable ? Level.MEDIUM : Level.HIGH, adjustedBusiness,
                List.of("最快止损，防止故障继续扩散", "资源成本低"),
                List.of("被隔离的节点不再对外提供服务", "可能让部分业务功能不可用"),
                List.of("隔离后所有用户入口仍保留至少一个可用依赖"),
                null);
    }

    // ---------- failover 前置校验 ----------

    private StrategyEvaluation.FailoverDetail evaluateFailoverFor(long faultyNodeId,
                                                                  List<ValidationModel.Node> nodes,
                                                                  List<ValidationModel.Group> groups,
                                                                  Set<Long> affectedIds,
                                                                  Map<Long, List<Long>> dependsBySource) {
        Map<Long, ValidationModel.Node> nodeById = new HashMap<>();
        nodes.forEach(n -> nodeById.put(n.id(), n));
        ValidationModel.Node primary = nodeById.get(faultyNodeId);
        if (primary == null) {
            return null;
        }

        ValidationModel.Group group = null;
        for (ValidationModel.Group g : groups) {
            boolean isPrimary = g.members().stream()
                    .anyMatch(m -> m.nodeId() != null && m.nodeId().equals(faultyNodeId) && ValidationModel.PRIMARY.equals(m.role()));
            if (isPrimary && isFailoverType(g.type())) {
                group = g;
                break;
            }
        }

        List<PrerequisiteCheck> checks = new ArrayList<>();
        checks.add(new PrerequisiteCheck("故障节点属于主备冗余组", group != null,
                group != null ? "冗余组：" + group.name() : "未找到以该节点为主节点的冗余组"));
        checks.add(new PrerequisiteCheck("冗余模式支持切换", group != null && isFailoverType(group.type()),
                group == null ? "—" : "模式：" + group.type()));

        if (group == null) {
            return new StrategyEvaluation.FailoverDetail(null, null, primary.id(), primary.name(),
                    null, null, "故障节点没有可用的主备冗余组", List.of(), checks);
        }

        List<ValidationModel.Member> standbyMembers = group.members().stream()
                .filter(m -> ValidationModel.STANDBY.equals(m.role()))
                .sorted(Comparator
                        .comparing((ValidationModel.Member m) -> m.priority() == null ? Integer.MAX_VALUE : m.priority())
                        .thenComparingLong(ValidationModel.Member::nodeId))
                .toList();

        List<StandbyCandidate> candidates = new ArrayList<>();
        for (ValidationModel.Member m : standbyMembers) {
            ValidationModel.Node standby = nodeById.get(m.nodeId());
            List<String> fails = new ArrayList<>();
            if (standby == null) {
                fails.add("节点不存在");
            } else {
                if (m.priority() == null) {
                    fails.add("未设置切换优先级");
                }
                if (!standby.kind().equals(primary.kind())) {
                    fails.add("类型与主节点不相容（" + standby.kind() + " ≠ " + primary.kind() + "）");
                }
                if (hasActiveFault(standby.id(), nodes, faultyNodeId)) {
                    fails.add("备用节点自身存在激活故障");
                }
                if (affectedIds.contains(standby.id())) {
                    fails.add("备用节点已被本次传播分析波及");
                }
                if (dependsBySource.getOrDefault(standby.id(), List.of()).stream().anyMatch(affectedIds::contains)
                        || dependsBySource.getOrDefault(standby.id(), List.of()).contains(faultyNodeId)) {
                    fails.add("备用节点依赖了故障节点或受影响节点");
                }
            }
            boolean eligible = fails.isEmpty();
            candidates.add(new StandbyCandidate(m.nodeId(),
                    standby == null ? "已删除节点" : standby.name(),
                    m.priority(), eligible,
                    eligible ? "满足切换前提" : String.join("；", fails)));
        }

        StandbyCandidate selected = candidates.stream()
                .filter(StandbyCandidate::eligible)
                .findFirst()
                .orElse(null);

        checks.add(new PrerequisiteCheck("存在备用节点", !standbyMembers.isEmpty(),
                !standbyMembers.isEmpty() ? "备用节点 " + standbyMembers.size() + " 个" : "冗余组没有备用节点"));
        checks.add(new PrerequisiteCheck("存在合格备用节点", selected != null,
                selected != null ? "选中：" + selected.nodeName() : "所有备用节点均不满足前提"));

        String selectionReason = selected == null
                ? "没有满足全部前置条件的备用节点"
                : String.format("priority=%d，数值越小越优先", selected.priority());

        return new StrategyEvaluation.FailoverDetail(group.id(), group.name(),
                primary.id(), primary.name(),
                selected == null ? null : selected.nodeId(),
                selected == null ? null : selected.nodeName(),
                selectionReason, candidates, checks);
    }

    private boolean isFailoverType(String type) {
        return ValidationModel.ACTIVE_STANDBY.equals(type) || ValidationModel.MANUAL_STANDBY.equals(type);
    }

    /** 备用节点自身是否带激活故障（当前模型用节点状态近似：down 视为有激活故障） */
    private boolean hasActiveFault(long nodeId, List<ValidationModel.Node> nodes, long faultyNodeId) {
        if (nodeId == faultyNodeId) {
            return true;
        }
        return nodes.stream()
                .filter(n -> n.id() != null && n.id().equals(nodeId))
                .anyMatch(n -> "down".equalsIgnoreCase(n.status()));
    }

    // ---------- 评分 ----------

    private StrategyEvaluation build(StrategyType type,
                                     boolean applicable,
                                     String inapplicableReason,
                                     int seconds,
                                     int impactAfter,
                                     int total,
                                     Level cost,
                                     Level risk,
                                     double business,
                                     List<String> advantages,
                                     List<String> disadvantages,
                                     List<String> prerequisites,
                                     StrategyEvaluation.FailoverDetail detail) {
        double timeScore = clamp(1 - seconds / REFERENCE_SECONDS);
        double impactScore = total == 0 ? 1.0 : clamp(1 - (double) impactAfter / total);
        double costScore = cost.score();
        double riskScore = risk.score();
        double businessScore = clamp(business);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("时间分", round(timeScore));
        breakdown.put("影响范围分", round(impactScore));
        breakdown.put("资源成本分", round(costScore));
        breakdown.put("操作风险分", round(riskScore));
        breakdown.put("业务恢复分", round(businessScore));

        double score = 100 * (0.25 * timeScore + 0.20 * impactScore + 0.15 * costScore
                + 0.20 * riskScore + 0.20 * businessScore);

        return new StrategyEvaluation(type, type.label(), applicable, inapplicableReason,
                seconds, impactAfter, round(total == 0 ? 0 : (double) impactAfter / total),
                cost, risk, round(score), breakdown, advantages, disadvantages, prerequisites, detail);
    }

    /**
     * 业务恢复度：执行动作后，仍能连上至少一个依赖的用户入口占比。
     */
    private double businessScore(List<ValidationModel.Node> entries,
                                 Map<Long, List<Long>> dependsBySource,
                                 Set<Long> unavailable) {
        if (entries.isEmpty()) {
            return 1.0;
        }
        long ok = entries.stream()
                .filter(e -> dependsBySource.getOrDefault(e.id(), List.of()).stream()
                        .anyMatch(t -> !unavailable.contains(t)))
                .count();
        return (double) ok / entries.size();
    }

    private String buildReason(List<StrategyEvaluation> strategies, StrategyEvaluation recommended) {
        if (recommended == null) {
            return "当前没有任何适用策略：故障节点既不能重启修复，也不具备切换条件，隔离还会切断业务入口";
        }
        String others = strategies.stream()
                .filter(s -> s != recommended)
                .map(s -> s.name() + " " + s.score() + (s.applicable() ? "" : "（不适用）"))
                .reduce((a, b) -> a + "、" + b).orElse("");
        return String.format("推荐【%s】：评分 %.2f%s", recommended.name(), recommended.score(),
                others.isEmpty() ? "" : "；对比 " + others);
    }

    private String estimationNotice() {
        return "恢复时间、成本、影响范围均为规则估算值（参考时间 600 秒），不是生产环境实测；"
                + "备用节点可用性仅由当前模型规则判定，未校验真实容量、网络与数据同步。";
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
