package com.cloudmirror.service.analysis;

import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E3 隔离切口：给一组故障源，贪心找出「最少隔离哪几个节点，能最大程度截断传播」。
 *
 * <p>做法：在受影响集合里逐个试删节点，选择能让可达集缩小最多的那个，重复至多 maxCut 次。
 * 贪心不保证全局最优（点割集是 NP-hard），但结果可解释、可复现、毫秒级。
 */
@Service
public class IsolationCutter {

    /**
     * @param beforeCount  不隔离时从故障源可达的节点数
     * @param afterCount   按建议隔离后的可达节点数
     * @param blockedCount 被截断的节点数 = before - after
     */
    public record CutResult(List<Long> cutNodeIds,
                            List<String> cutNodeNames,
                            int beforeCount,
                            int afterCount,
                            int blockedCount) {
    }

    public CutResult propose(List<ValidationModel.Node> nodes,
                             List<ValidationModel.Edge> edges,
                             Set<Long> faultyIds,
                             int maxCut) {
        Map<Long, String> names = new HashMap<>();
        nodes.forEach(n -> names.put(n.id(), n.name()));
        Map<Long, List<Long>> dependents = buildDependents(edges);

        Set<Long> current = reach(faultyIds, dependents, Set.of());
        int before = current.size();
        List<Long> cuts = new ArrayList<>();

        for (int i = 0; i < maxCut; i++) {
            Long best = null;
            Set<Long> bestReach = current;
            for (Long candidate : current) {
                if (faultyIds.contains(candidate) || cuts.contains(candidate)) {
                    continue;
                }
                Set<Long> excluded = new HashSet<>(cuts);
                excluded.add(candidate);
                Set<Long> after = reach(faultyIds, dependents, excluded);
                if (after.size() < bestReach.size()) {
                    best = candidate;
                    bestReach = after;
                }
            }
            if (best == null) {
                break;
            }
            cuts.add(best);
            current = bestReach;
        }

        List<String> cutNames = cuts.stream().map(id -> names.getOrDefault(id, "节点#" + id)).toList();
        return new CutResult(List.copyOf(cuts), cutNames, before, current.size(), before - current.size());
    }

    private Map<Long, List<Long>> buildDependents(List<ValidationModel.Edge> edges) {
        Map<Long, List<Long>> dependents = new HashMap<>();
        edges.stream()
                .filter(e -> ValidationModel.DEPENDS_ON.equals(e.kind()))
                .sorted(Comparator.comparing(ValidationModel.Edge::id))
                .forEach(e -> dependents
                        .computeIfAbsent(e.targetNodeId(), k -> new ArrayList<>())
                        .add(e.sourceNodeId()));
        return dependents;
    }

    /** 从故障源出发、沿依赖反方向扩散的可达集合（不含故障源自身，不含被隔离节点） */
    private Set<Long> reach(Set<Long> seeds, Map<Long, List<Long>> dependents, Set<Long> excluded) {
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (Long seed : seeds) {
            queue.add(seed);
            seen.add(seed);
        }
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            for (Long next : dependents.getOrDefault(cur, List.of())) {
                if (excluded.contains(next) || !seen.add(next)) {
                    continue;
                }
                queue.add(next);
            }
        }
        seen.removeAll(seeds);
        return seen;
    }
}
