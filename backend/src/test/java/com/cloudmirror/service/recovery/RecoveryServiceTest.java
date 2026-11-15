package com.cloudmirror.service.recovery;

import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.propagation.PropagationService;
import com.cloudmirror.service.rule.ValidationModel;
import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Group;
import com.cloudmirror.service.rule.ValidationModel.Member;
import com.cloudmirror.service.rule.ValidationModel.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 恢复决策单元测试：适用性边界、备用节点选择、评分与推荐、确定性。
 */
class RecoveryServiceTest {

    private final RecoveryService service = new RecoveryService();
    private final PropagationService propagation = new PropagationService();

    // ---------- 夹具 ----------

    /** 网关(1) → 订单(2) / 用户(3)；订单 → MySQL 主(4) / Redis(6)；用户 → MySQL 主(4) */
    private static List<Node> nodes() {
        return List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 1, 0.5, 2048, 256, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "healthy"),
                new Node(3L, "用户服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "healthy"),
                new Node(4L, "MySQL 主", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200, "down"),
                new Node(5L, "MySQL 备", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200, "healthy"),
                new Node(6L, "Redis", ValidationModel.MIDDLEWARE, 2, 0.5, 4096, 512, 20, "healthy"));
    }

    private static List<Edge> edges() {
        return List.of(
                edge(101, 1, 2, ValidationModel.DEPENDS_ON),
                edge(102, 1, 3, ValidationModel.DEPENDS_ON),
                edge(103, 2, 4, ValidationModel.DEPENDS_ON),
                edge(104, 2, 6, ValidationModel.DEPENDS_ON),
                edge(105, 3, 4, ValidationModel.DEPENDS_ON));
    }

    private static Edge edge(long id, long s, long t, String kind) {
        return new Edge(id, s, t, kind, 0L, 0L, true, 1.0);
    }

    private static List<Group> groups() {
        return List.of(new Group(1L, "MySQL 主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(4L, ValidationModel.PRIMARY, null),
                        new Member(5L, ValidationModel.STANDBY, 1))));
    }

    private RecoveryResult run(List<Node> nodes, List<Edge> edges, List<Group> groups, List<Fault> faults) {
        PropagationResult p = propagation.propagate(nodes, edges, faults);
        return service.recommend(nodes, edges, groups, faults, p);
    }

    private StrategyEvaluation byType(RecoveryResult r, StrategyType type) {
        return r.strategies().stream().filter(s -> s.type() == type).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("打印：把恢复推荐写到 target/recovery-demo.txt，便于人工核对")
    void printReport() throws Exception {
        RecoveryResult r = run(nodes(), edges(), groups(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        StringBuilder sb = new StringBuilder();
        sb.append("===== 场景：MySQL 主库宕机（critical） =====\n");
        sb.append("推荐：").append(r.recommendedName()).append('\n');
        sb.append("理由：").append(r.recommendationReason()).append('\n');
        sb.append(String.format("权重：%s%n", r.weights()));
        for (StrategyEvaluation s : r.strategies()) {
            sb.append(String.format("%n【%s】适用=%s 评分=%.2f 耗时=%ds 成本=%s 风险=%s%n",
                    s.name(), s.applicable(), s.score(), s.estimatedRecoverySeconds(),
                    s.cost().label(), s.risk().label()));
            if (!s.applicable()) {
                sb.append("  不适用原因：").append(s.inapplicableReason()).append('\n');
            }
            sb.append("  分项：").append(s.scoreBreakdown()).append('\n');
            if (s.failoverDetail() != null) {
                var d = s.failoverDetail();
                sb.append(String.format("  备用选择：%s（%s）%n", d.selectedStandbyName(), d.selectionReason()));
                d.candidates().forEach(c -> sb.append(String.format("    候选 %s priority=%s 合格=%s %s%n",
                        c.nodeName(), c.priority(), c.eligible(), c.detail())));
                d.checks().forEach(c -> sb.append(String.format("    检查 %s：%s（%s）%n",
                        c.checkName(), c.passed(), c.detail())));
            }
        }

        System.out.println(sb);
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("target", "recovery-demo.txt"),
                sb.toString(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("主库宕机 + 有合格备用 → failover 适用并被推荐")
    void failoverRecommended() {
        RecoveryResult r = run(nodes(), edges(), groups(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        StrategyEvaluation failover = byType(r, StrategyType.FAILOVER);
        assertTrue(failover.applicable());
        assertNotNull(failover.failoverDetail());
        assertEquals(5L, failover.failoverDetail().selectedStandbyNodeId());
        assertEquals("MySQL 备", failover.failoverDetail().selectedStandbyName());
        assertTrue(failover.failoverDetail().selectionReason().contains("priority=1"));
        assertEquals(StrategyType.FAILOVER, r.recommendedType(), "推荐理由：" + r.recommendationReason());
    }

    @Test
    @DisplayName("没有冗余组 → failover 不适用，且原因明确")
    void failoverRequiresGroup() {
        RecoveryResult r = run(nodes(), edges(), List.of(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        StrategyEvaluation failover = byType(r, StrategyType.FAILOVER);
        assertFalse(failover.applicable());
        assertTrue(failover.inapplicableReason().contains("冗余组"), failover.inapplicableReason());
        assertEquals(StrategyType.RESTART_SERVICE, r.recommendedType());
    }

    @Test
    @DisplayName("网络延迟类故障 → 重启不适用（重启修不了网络）")
    void restartNotApplicableForNetworkFault() {
        RecoveryResult r = run(nodes(), edges(), groups(),
                List.of(new Fault(7, 4, "MySQL 主", "network_latency", "high")));

        StrategyEvaluation restart = byType(r, StrategyType.RESTART_SERVICE);
        assertFalse(restart.applicable());
        assertTrue(restart.inapplicableReason().contains("node_down"));
        assertEquals(StrategyType.FAILOVER, r.recommendedType());
    }

    @Test
    @DisplayName("备用节点自身故障 → 不合格，failover 不适用")
    void standbyWithFaultIsIneligible() {
        List<Node> nodes = new ArrayList<>(nodes());
        nodes.set(4, new Node(5L, "MySQL 备", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200, "down"));

        RecoveryResult r = run(nodes, edges(), groups(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        StrategyEvaluation failover = byType(r, StrategyType.FAILOVER);
        assertFalse(failover.applicable());
        StandbyCandidate standby = failover.failoverDetail().candidates().get(0);
        assertFalse(standby.eligible());
        assertTrue(standby.detail().contains("激活故障"), standby.detail());
    }

    @Test
    @DisplayName("备用节点被传播波及 → 不合格")
    void affectedStandbyIsIneligible() {
        // 让备用(5) 也依赖故障节点(4)：备 → 4，于是 5 会被波及
        List<Edge> edges = new ArrayList<>(edges());
        edges.add(edge(106, 5, 4, ValidationModel.DEPENDS_ON));

        RecoveryResult r = run(nodes(), edges, groups(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        StrategyEvaluation failover = byType(r, StrategyType.FAILOVER);
        assertFalse(failover.applicable());
        assertTrue(failover.failoverDetail().candidates().get(0).detail().contains("波及"),
                failover.failoverDetail().candidates().get(0).detail());
    }

    @Test
    @DisplayName("隔离会切断入口 → isolate 不适用且风险高")
    void isolateBlockedByEntry() {
        List<Node> nodes = List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 1, 0.5, 2048, 256, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "down"));
        List<Edge> edges = List.of(edge(101, 1, 2, ValidationModel.DEPENDS_ON));
        List<Fault> faults = List.of(new Fault(7, 2, "订单服务", "node_down", "critical"));

        RecoveryResult r = run(nodes, edges, List.of(), faults);
        StrategyEvaluation isolate = byType(r, StrategyType.ISOLATE_NODE);
        assertFalse(isolate.applicable());
        assertEquals(Level.HIGH, isolate.risk());
        assertTrue(isolate.inapplicableReason().contains("失去全部可用依赖"));
    }

    @Test
    @DisplayName("两个备用节点 → 选 priority 更小的（数值越小越优先）")
    void selectHighestPriorityStandby() {
        List<Node> nodes = new ArrayList<>(nodes());
        nodes.add(new Node(7L, "MySQL 备2", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200, "healthy"));
        List<Group> groups = List.of(new Group(1L, "MySQL 主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(4L, ValidationModel.PRIMARY, null),
                        new Member(5L, ValidationModel.STANDBY, 2),
                        new Member(7L, ValidationModel.STANDBY, 1))));

        RecoveryResult r = run(nodes, edges(), groups,
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));

        assertEquals(7L, byType(r, StrategyType.FAILOVER).failoverDetail().selectedStandbyNodeId());
    }

    @Test
    @DisplayName("没有任何适用策略 → recommendedType 为 null，理由可读")
    void noApplicableStrategy() {
        // 故障是网络延迟（重启不适用）、没有冗余组（切备不适用）、隔离会切断入口（隔离不适用）
        List<Node> nodes = List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 1, 0.5, 2048, 256, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "down"));
        List<Edge> edges = List.of(edge(101, 1, 2, ValidationModel.DEPENDS_ON));

        RecoveryResult r = run(nodes, edges, List.of(),
                List.of(new Fault(7, 2, "订单服务", "network_latency", "high")));

        assertNull(r.recommendedType());
        assertTrue(r.recommendationReason().contains("没有任何适用策略"));
    }

    @Test
    @DisplayName("评分权重合计为 1.00，且五项齐全")
    void weightsSumToOne() {
        RecoveryResult r = run(nodes(), edges(), groups(),
                List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical")));
        assertEquals(5, r.weights().size());
        assertEquals(1.0, r.weights().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        StrategyEvaluation failover = byType(r, StrategyType.FAILOVER);
        assertEquals(5, failover.scoreBreakdown().size());
        assertTrue(failover.score() > 0 && failover.score() <= 100);
    }

    @Test
    @DisplayName("确定性：同一输入连跑 100 次，结果完全一致")
    void deterministic() {
        List<Fault> faults = List.of(new Fault(7, 4, "MySQL 主", "node_down", "critical"));
        RecoveryResult first = run(nodes(), edges(), groups(), faults);
        for (int i = 0; i < 100; i++) {
            assertEquals(first.toString(), run(nodes(), edges(), groups(), faults).toString());
        }
    }
}
