package com.cloudmirror.service.analysis;

import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E2 关键单点识别：在依赖图上找「割点」（articulation point）。
 *
 * <p>割点 = 拿掉它，依赖图会被切成两半以上的节点。这类节点一旦故障，
 * 影响不是「连带着挂几个服务」，而是「整个网络断成两半」，属于结构性风险。
 *
 * <p>算法：Tarjan 求割点 O(V+E)，再对每个割点模拟删除、统计连通分量数。
 */
@Service
public class CriticalNodeAnalyzer {

    /**
     * @param componentsAfterRemoval       删除该节点后的连通分量数
     * @param largestComponentAfterRemoval 删除后最大连通分量的大小
     */
    public record CriticalNode(long nodeId,
                               String nodeName,
                               int componentsAfterRemoval,
                               int largestComponentAfterRemoval) {
    }

    public List<CriticalNode> analyze(List<ValidationModel.Node> nodes,
                                      List<ValidationModel.Edge> edges) {
        Map<Long, String> names = new LinkedHashMap<>();
        nodes.forEach(n -> names.put(n.id(), n.name()));

        Map<Long, Set<Long>> adjacency = buildUndirected(edges);

        // Tarjan：disc / low / articulation
        Map<Long, Integer> disc = new HashMap<>();
        Map<Long, Integer> low = new HashMap<>();
        Set<Long> articulation = new HashSet<>();
        int[] timer = {0};

        for (Long id : names.keySet()) {
            if (!disc.containsKey(id)) {
                dfs(id, null, adjacency, disc, low, articulation, timer);
            }
        }

        List<CriticalNode> result = new ArrayList<>();
        for (Long id : articulation) {
            int[] stats = componentsAfterRemoval(id, names.keySet(), adjacency);
            result.add(new CriticalNode(id, names.get(id), stats[0], stats[1]));
        }
        result.sort((a, b) -> Long.compare(a.nodeId(), b.nodeId()));
        return result;
    }

    private Map<Long, Set<Long>> buildUndirected(List<ValidationModel.Edge> edges) {
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        for (ValidationModel.Edge e : edges) {
            if (!ValidationModel.DEPENDS_ON.equals(e.kind())) {
                continue;
            }
            adjacency.computeIfAbsent(e.sourceNodeId(), k -> new HashSet<>()).add(e.targetNodeId());
            adjacency.computeIfAbsent(e.targetNodeId(), k -> new HashSet<>()).add(e.sourceNodeId());
        }
        return adjacency;
    }

    private void dfs(Long node, Long parent,
                     Map<Long, Set<Long>> adjacency,
                     Map<Long, Integer> disc,
                     Map<Long, Integer> low,
                     Set<Long> articulation,
                     int[] timer) {
        disc.put(node, timer[0]);
        low.put(node, timer[0]);
        timer[0]++;
        int children = 0;

        for (Long next : adjacency.getOrDefault(node, Set.of())) {
            if (!disc.containsKey(next)) {
                children++;
                dfs(next, node, adjacency, disc, low, articulation, timer);
                low.put(node, Math.min(low.get(node), low.get(next)));
                if (parent != null && low.get(next) >= disc.get(node)) {
                    articulation.add(node);
                }
            } else if (!next.equals(parent)) {
                low.put(node, Math.min(low.get(node), disc.get(next)));
            }
        }
        if (parent == null && children > 1) {
            articulation.add(node);
        }
    }

    /** @return [连通分量数, 最大分量大小] */
    private int[] componentsAfterRemoval(Long removed, Set<Long> all, Map<Long, Set<Long>> adjacency) {
        Set<Long> seen = new HashSet<>();
        int components = 0;
        int largest = 0;
        for (Long id : all) {
            if (id.equals(removed) || seen.contains(id)) {
                continue;
            }
            components++;
            int size = 0;
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(id);
            seen.add(id);
            while (!queue.isEmpty()) {
                Long cur = queue.poll();
                size++;
                for (Long next : adjacency.getOrDefault(cur, Set.of())) {
                    if (next.equals(removed) || seen.contains(next)) {
                        continue;
                    }
                    seen.add(next);
                    queue.add(next);
                }
            }
            largest = Math.max(largest, size);
        }
        return new int[]{components, largest};
    }
}
