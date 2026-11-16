package com.cloudmirror.service.analysis;

import com.cloudmirror.service.propagation.Fault;
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
 * 级联失效分析（小创新）：主节点故障后，备用节点接管它的负载；
 * 如果备用节点没有余量，接管后就过载，于是它也挂 —— 一路连锁下去。
 *
 * <p>负载与容量都用现有数据推断，不新增表：
 * <ul>
 *   <li>容量 = cpuCores（宿主机）；否则 cpuRequest（服务/中间件按自身规格）</li>
 *   <li>基线负载 = 容量 × 60%（假设平时跑到六成，留 40% 余量）</li>
 *   <li>接管负载 = 故障主节点的基线负载；接管后利用率 &gt; 100% 判过载，进入下一轮</li>
 * </ul>
 *
 * <p>于是：备用和主一样大 → 0.6 + 0.6 = 1.2 &gt; 1 → 被拖垮；
 * 备用是主的 2 倍 → 1.2 + 0.6 = 1.8 ≤ 2 → 扛得住。这就是"备用没有冗余容量"的真实风险。
 */
@Service
public class CascadeAnalyzer {

    private static final int MAX_ROUNDS = 6;
    /** 平时负载占容量的比例（可解释的假设，不是随机数） */
    private static final double BASELINE_UTILIZATION = 0.6;

    /**
     * @param reason 过载原因（含具体数字）
     */
    public record CascadeEvent(long nodeId, String nodeName, int round, String reason,
                               double load, double capacity) {
    }

    public record CascadeResult(List<CascadeEvent> events,
                                List<Long> failedNodeIds,
                                int rounds,
                                String summary) {
    }

    public CascadeResult analyze(List<ValidationModel.Node> nodes,
                                 List<ValidationModel.Group> groups,
                                 List<Fault> faults) {
        Map<Long, ValidationModel.Node> byId = new LinkedHashMap<>();
        nodes.forEach(n -> byId.put(n.id(), n));

        Set<Long> failed = new LinkedHashSet<>();
        faults.stream()
                .map(Fault::nodeId)
                .filter(byId::containsKey)
                .forEach(failed::add);

        Map<Long, Double> load = new HashMap<>();
        for (ValidationModel.Node n : nodes) {
            load.put(n.id(), capacity(n) * BASELINE_UTILIZATION);
        }

        List<CascadeEvent> events = new ArrayList<>();
        int round = 0;
        boolean progressed = true;

        while (progressed && round < MAX_ROUNDS) {
            round++;
            progressed = false;
            Set<Long> nextFailures = new LinkedHashSet<>();

            for (Long faulted : new ArrayList<>(failed)) {
                ValidationModel.Group group = failoverGroupOf(faulted, groups);
                if (group == null) {
                    continue;
                }
                // 按 priority 选第一个还活着的备用节点接管负载
                ValidationModel.Node standby = group.members().stream()
                        .filter(m -> ValidationModel.STANDBY.equals(m.role()))
                        .filter(m -> m.priority() != null)
                        .sorted(Comparator.comparingInt(ValidationModel.Member::priority)
                                .thenComparingLong(ValidationModel.Member::nodeId))
                        .map(m -> byId.get(m.nodeId()))
                        .filter(n -> n != null && !failed.contains(n.id()) && !nextFailures.contains(n.id()))
                        .findFirst()
                        .orElse(null);
                if (standby == null) {
                    continue;
                }

                double transferred = capacity(byId.get(faulted)) * BASELINE_UTILIZATION;
                double newLoad = load.getOrDefault(standby.id(), capacity(standby) * BASELINE_UTILIZATION) + transferred;
                load.put(standby.id(), newLoad);
                double cap = capacity(standby);
                if (newLoad > cap + 1e-9) {
                    nextFailures.add(standby.id());
                    events.add(new CascadeEvent(standby.id(), standby.name(), round,
                            String.format("接管 %s 的负载后过载：%.1f / %.1f（超 %.0f%%）",
                                    byId.get(faulted).name(), newLoad, cap,
                                    (newLoad / cap - 1) * 100),
                            newLoad, cap));
                }
            }

            if (!nextFailures.isEmpty()) {
                failed.addAll(nextFailures);
                progressed = true;
            }
        }

        String summary;
        if (events.isEmpty()) {
            summary = faults.isEmpty()
                    ? "当前没有激活故障，无级联风险"
                    : "未发现级联过载：备用节点余量足够";
        } else {
            summary = String.format("发生 %d 轮级联过载，共 %d 个节点被拖垮（%s）",
                    round - 1, events.size(),
                    events.stream().map(CascadeEvent::nodeName).reduce((a, b) -> a + "、" + b).orElse(""));
        }
        return new CascadeResult(events, new ArrayList<>(failed), round, summary);
    }

    /** 节点容量：宿主机按核数，其它按自身 cpuRequest（0 按 1 计） */
    private double capacity(ValidationModel.Node n) {
        if (n == null) {
            return 0;
        }
        if (n.cpuCores() > 0) {
            return n.cpuCores();
        }
        return n.cpuRequest() > 0 ? n.cpuRequest() : 1.0;
    }

    private ValidationModel.Group failoverGroupOf(Long nodeId, List<ValidationModel.Group> groups) {
        for (ValidationModel.Group g : groups) {
            boolean isPrimary = g.members().stream()
                    .anyMatch(m -> m.nodeId() != null && m.nodeId().equals(nodeId) && ValidationModel.PRIMARY.equals(m.role()));
            boolean failoverType = ValidationModel.ACTIVE_STANDBY.equals(g.type())
                    || ValidationModel.MANUAL_STANDBY.equals(g.type());
            if (isPrimary && failoverType) {
                return g;
            }
        }
        return null;
    }
}
