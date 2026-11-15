package com.cloudmirror.service.recovery;

import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.rule.ValidationModel;
import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Group;
import com.cloudmirror.service.rule.ValidationModel.Member;
import com.cloudmirror.service.rule.ValidationModel.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E4 多动作恢复序列的单元测试 */
class RecoveryPlannerTest {

    private final RecoveryPlanner planner = new RecoveryPlanner();

    @Test
    @DisplayName("E4：两个宕机故障 → 推荐重启两步，总耗时 240 秒")
    void twoRestarts() {
        List<Node> nodes = List.of(
                node(1, "订单服务", ValidationModel.SERVICE),
                node(2, "用户服务", ValidationModel.SERVICE));
        List<Fault> faults = List.of(
                new Fault(11, 1, "订单服务", "node_down", "high"),
                new Fault(12, 2, "用户服务", "node_down", "high"));

        RecoveryPlanner.PlannerResult r = planner.plan(nodes, List.of(), List.of(), faults);
        assertNotNull(r.best());
        assertEquals(2, r.best().actions().size());
        assertEquals(240, r.best().totalSeconds());
        assertTrue(r.best().actions().stream()
                .allMatch(a -> a.type() == StrategyType.RESTART_SERVICE));
        assertTrue(r.summary().contains("2 步恢复"));
    }

    @Test
    @DisplayName("E4：主库宕机且有备用 → 切备（快且成本可控）优于重启")
    void failoverBeatsRestart() {
        List<Node> nodes = List.of(
                node(1, "MySQL主", ValidationModel.MIDDLEWARE),
                node(2, "MySQL备", ValidationModel.MIDDLEWARE));
        List<Group> groups = List.of(new Group(1L, "MySQL 主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(1L, ValidationModel.PRIMARY, null),
                        new Member(2L, ValidationModel.STANDBY, 1))));
        List<Fault> faults = List.of(new Fault(11, 1, "MySQL主", "node_down", "critical"));

        RecoveryPlanner.PlannerResult r = planner.plan(nodes, List.of(), groups, faults);
        assertNotNull(r.best());
        assertEquals(1, r.best().actions().size());
        assertEquals(StrategyType.FAILOVER, r.best().actions().get(0).type());
        assertEquals(60, r.best().totalSeconds());
        assertTrue(r.alternatives().stream()
                .anyMatch(p -> p.actions().get(0).type() == StrategyType.RESTART_SERVICE));
    }

    @Test
    @DisplayName("E4：隔离会切断入口依赖 → 该组合被排除")
    void isolateBlockedByEntry() {
        List<Node> nodes = List.of(
                node(1, "网关", ValidationModel.GATEWAY),
                node(2, "订单服务", ValidationModel.SERVICE));
        List<Edge> edges = List.of(new Edge(10L, 1L, 2L, ValidationModel.DEPENDS_ON, 0L, 0L, true, 1.0));
        List<Fault> faults = List.of(new Fault(11, 2, "订单服务", "network_latency", "high"));

        RecoveryPlanner.PlannerResult r = planner.plan(nodes, edges, List.of(), faults);
        // network_latency 不能重启、没有备用、隔离会切断网关唯一依赖 → 无解
        assertNull(r.best());
        assertTrue(r.summary().contains("没有可行"));
    }

    @Test
    @DisplayName("E4：没有故障 → 返回空方案")
    void noFaults() {
        RecoveryPlanner.PlannerResult r = planner.plan(
                List.of(node(1, "服务", ValidationModel.SERVICE)), List.of(), List.of(), List.of());
        assertNull(r.best());
        assertTrue(r.summary().contains("没有激活故障"));
    }

    @Test
    @DisplayName("E4：确定性 —— 同一输入连跑 50 次结果一致")
    void deterministic() {
        List<Node> nodes = List.of(
                node(1, "订单服务", ValidationModel.SERVICE),
                node(2, "用户服务", ValidationModel.SERVICE));
        List<Fault> faults = List.of(
                new Fault(11, 1, "订单服务", "node_down", "high"),
                new Fault(12, 2, "用户服务", "node_down", "high"));
        RecoveryPlanner.PlannerResult first = planner.plan(nodes, List.of(), List.of(), faults);
        for (int i = 0; i < 50; i++) {
            assertEquals(first.toString(), planner.plan(nodes, List.of(), List.of(), faults).toString());
        }
    }

    private Node node(long id, String name, String kind) {
        return new Node(id, name, kind, 0, 1, 0, 1024, 0, "healthy");
    }
}
