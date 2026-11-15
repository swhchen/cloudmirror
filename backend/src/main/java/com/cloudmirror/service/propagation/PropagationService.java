package com.cloudmirror.service.propagation;

import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 故障传播分析（算法①）。
 *
 * <p>核心约定：边 A → B 表示「A 依赖 B」，传播方向与依赖方向相反——B 故障时 A 受影响。
 *
 * <p>实现要点：
 * <ul>
 *   <li>反向邻接：dependents[B] = [A 的边...]</li>
 *   <li>多源 BFS：所有故障源距离 0 同时入队，首次发现即最短距离</li>
 *   <li>环路安全：已访问集合控制，环不会死循环</li>
 *   <li>多源去重：取最短距离；同距离按故障 ID 升序打破平局</li>
 *   <li>路径回溯：BFS 记录父边，回溯出「故障源 → … → 受影响节点」完整链路（带端口）</li>
 *   <li>容量感知（E1）：传播强度 = 边权累乘 × 依赖方容量余量放大系数</li>
 *   <li><b>依赖冗余度</b>：受影响节点还有其它健康下游时，强度按「受影响依赖数 / 总依赖数」打折</li>
 *   <li><b>时间窗</b>：按边上的延迟累加传播耗时，可只算「T 秒内」会波及的节点</li>
 *   <li>确定性：不排序的集合一律先排序，无随机数，同样输入永远同样输出</li>
 * </ul>
 */
@Service
public class PropagationService {

    /** 必崩阈值 */
    public static final double CERTAIN_THRESHOLD = 0.6;

    public PropagationResult propagate(List<ValidationModel.Node> nodes,
                                       List<ValidationModel.Edge> edges,
                                       List<Fault> faults) {
        return propagate(nodes, edges, faults, null);
    }

    /**
     * @param windowSeconds 可选时间窗：只统计该秒数内会被波及的节点（null = 不限制）
     */
    public PropagationResult propagate(List<ValidationModel.Node> nodes,
                                       List<ValidationModel.Edge> edges,
                                       List<Fault> faults,
                                       Integer windowSeconds) {
        Map<Long, ValidationModel.Node> nodeById = new LinkedHashMap<>();
        for (ValidationModel.Node n : nodes) {
            nodeById.put(n.id(), n);
        }

        // 反向邻接：B → 所有「A 依赖 B」的边（按边 ID 排序保证确定性）
        Map<Long, List<ValidationModel.Edge>> dependents = new HashMap<>();
        // 正向依赖：A → A 依赖的目标（用于算依赖冗余度）
        Map<Long, Set<Long>> depsOf = new HashMap<>();
        edges.stream()
                .filter(e -> ValidationModel.DEPENDS_ON.equals(e.kind()))
                .sorted(Comparator.comparing(ValidationModel.Edge::id))
                .forEach(e -> {
                    dependents.computeIfAbsent(e.targetNodeId(), k -> new ArrayList<>()).add(e);
                    depsOf.computeIfAbsent(e.sourceNodeId(), k -> new LinkedHashSet<>()).add(e.targetNodeId());
                });
        dependents.values().forEach(list -> list.sort(Comparator.comparing(ValidationModel.Edge::id)));

        List<Fault> sortedFaults = faults.stream()
                .filter(f -> nodeById.containsKey(f.nodeId()))
                .sorted(Comparator.comparingLong(Fault::id))
                .toList();

        Map<Long, Integer> distance = new LinkedHashMap<>();
        Map<Long, Long> parentNode = new HashMap<>();
        Map<Long, ValidationModel.Edge> parentEdge = new HashMap<>();
        Map<Long, Fault> sourceOf = new HashMap<>();
        Map<Long, Double> strength = new HashMap<>();
        Map<Long, Long> arrival = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        Map<Long, Double> saturation = saturation(nodes, edges);

        for (Fault f : sortedFaults) {
            if (!distance.containsKey(f.nodeId())) {
                distance.put(f.nodeId(), 0);
                strength.put(f.nodeId(), 1.0);
                arrival.put(f.nodeId(), 0L);
                sourceOf.put(f.nodeId(), f);
                queue.add(f.nodeId());
            }
        }

        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            for (ValidationModel.Edge e : dependents.getOrDefault(cur, List.of())) {
                Long up = e.sourceNodeId();          // 依赖方：被波及的一侧
                if (!nodeById.containsKey(up) || distance.containsKey(up)) {
                    continue;                        // 环路安全 + 只记最短
                }
                distance.put(up, distance.get(cur) + 1);
                parentNode.put(up, cur);
                parentEdge.put(up, e);
                sourceOf.put(up, sourceOf.get(cur));

                double edgeWeight = e.weight() <= 0 ? 1.0 : e.weight();
                double amplified = strength.get(cur) * edgeWeight * amplification(up, saturation);
                strength.put(up, Math.min(1.0, amplified));

                long latency = e.latencyMs() == null ? 0L : e.latencyMs();
                arrival.put(up, arrival.getOrDefault(cur, 0L) + latency);

                queue.add(up);
            }
        }

        Set<Long> impactedIds = new LinkedHashSet<>(distance.keySet());
        long windowMillis = windowSeconds == null ? Long.MAX_VALUE : Math.max(0, windowSeconds) * 1000L;

        List<AffectedNode> affected = new ArrayList<>();
        distance.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingLong(Map.Entry::getKey))
                .forEach(e -> {
                    Long nodeId = e.getKey();
                    int dist = e.getValue();
                    long arrivalMillis = arrival.getOrDefault(nodeId, 0L);
                    if (arrivalMillis > windowMillis) {
                        return;
                    }
                    Fault src = sourceOf.get(nodeId);
                    double ratio = dependencyRatio(nodeId, depsOf, impactedIds);
                    double adjusted = strength.getOrDefault(nodeId, 1.0) * ratio;
                    affected.add(AffectedNode.of(nodeId, nodeById.get(nodeId).name(), dist,
                            src.id(), src.nodeId(), adjusted, ratio, arrivalMillis,
                            buildPath(nodeId, nodeById, parentNode, parentEdge)));
                });

        int direct = (int) affected.stream().filter(a -> a.distance() == 1).count();
        int indirect = affected.size() - direct;
        int certain = (int) affected.stream().filter(a -> AffectedNode.CERTAIN.equals(a.impactClass())).count();
        int possible = affected.size() - certain;
        double ratio = nodes.isEmpty() ? 0.0 : (double) affected.size() / nodes.size();

        return new PropagationResult(sortedFaults, affected, direct, indirect, certain, possible,
                nodes.size(), Math.round(ratio * 10000) / 10000.0,
                buildSummary(sortedFaults, affected.size(), direct, indirect, certain, possible, ratio, windowSeconds));
    }

    // ---------- 容量感知（E1） ----------

    private Map<Long, Double> saturation(List<ValidationModel.Node> nodes,
                                         List<ValidationModel.Edge> edges) {
        Map<Long, Long> hostOf = new HashMap<>();
        for (ValidationModel.Edge e : edges) {
            if (ValidationModel.DEPLOY_ON.equals(e.kind())) {
                hostOf.put(e.sourceNodeId(), e.targetNodeId());
            }
        }
        Map<Long, Double> used = new HashMap<>();
        for (ValidationModel.Node n : nodes) {
            Long host = hostOf.get(n.id());
            if (host != null) {
                used.merge(host, n.cpuRequest(), Double::sum);
            }
        }
        Map<Long, Double> result = new HashMap<>();
        for (ValidationModel.Node n : nodes) {
            if (n.isHostLike() && n.cpuCores() > 0) {
                result.put(n.id(), Math.min(1.0, used.getOrDefault(n.id(), 0.0) / n.cpuCores()));
            }
        }
        for (ValidationModel.Node n : nodes) {
            Long host = hostOf.get(n.id());
            if (host != null) {
                result.put(n.id(), result.getOrDefault(host, 0.0));
            }
        }
        return result;
    }

    private double amplification(Long nodeId, Map<Long, Double> saturation) {
        return 1.0 + 0.5 * saturation.getOrDefault(nodeId, 0.0);
    }

    /**
     * 依赖冗余度：该节点依赖的下游里，有多少已经受影响。
     * 全挂 = 1.0（满强度），挂三分之一 = 0.33（打了折，因为它还有别的下游能撑）。
     */
    private double dependencyRatio(Long nodeId, Map<Long, Set<Long>> depsOf, Set<Long> impacted) {
        Set<Long> deps = depsOf.getOrDefault(nodeId, Set.of());
        if (deps.isEmpty()) {
            return 1.0;
        }
        long hit = deps.stream().filter(impacted::contains).count();
        return (double) hit / deps.size();
    }

    /** 从受影响节点沿父边回溯到故障源，再反转成「源 → 目标」 */
    private List<PathHop> buildPath(Long nodeId,
                                    Map<Long, ValidationModel.Node> nodeById,
                                    Map<Long, Long> parentNode,
                                    Map<Long, ValidationModel.Edge> parentEdge) {
        List<PathHop> hops = new ArrayList<>();
        Long cur = nodeId;
        while (cur != null) {
            ValidationModel.Edge edge = parentEdge.get(cur);
            ValidationModel.Node node = nodeById.get(cur);
            if (edge == null) {
                hops.add(PathHop.origin(cur, node.name()));
                break;
            }
            hops.add(new PathHop(cur, node.name(), edge.id(), edge.sourcePortId(), edge.targetPortId()));
            cur = parentNode.get(cur);
        }
        java.util.Collections.reverse(hops);
        return hops;
    }

    private String buildSummary(List<Fault> faults, int affected, int direct, int indirect,
                                int certain, int possible, double ratio, Integer windowSeconds) {
        String scope = windowSeconds == null ? "" : String.format("（%d 秒内）", windowSeconds);
        if (faults.isEmpty()) {
            return "当前没有激活故障，无需推演";
        }
        if (affected == 0) {
            return String.format("%d 个故障源%s未波及其它节点", faults.size(), scope);
        }
        return String.format("%d 个故障源%s波及 %d 个节点（直接 %d / 间接 %d；必崩 %d / 可能 %d），影响比例 %.1f%%",
                faults.size(), scope, affected, direct, indirect, certain, possible, ratio * 100);
    }
}
