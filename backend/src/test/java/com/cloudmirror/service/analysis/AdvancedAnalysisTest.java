package com.cloudmirror.service.analysis;

import com.cloudmirror.service.propagation.AffectedNode;
import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.propagation.PropagationResult;
import com.cloudmirror.service.propagation.PropagationService;
import com.cloudmirror.service.rule.ValidationModel;
import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E1 容量感知传播、E2 割点、E3 隔离切口 的单元测试 */
class AdvancedAnalysisTest {

    private final PropagationService propagation = new PropagationService();
    private final CriticalNodeAnalyzer criticalNodes = new CriticalNodeAnalyzer();
    private final IsolationCutter cutter = new IsolationCutter();

    // ---------- E1 容量感知 ----------

    @Test
    @DisplayName("E1：弱依赖（权重 0.5）且有余量 → 判「可能受影响」")
    void weakEdgeIsPossible() {
        List<Node> nodes = List.of(
                node(1, "MySQL", ValidationModel.MIDDLEWARE, 0, 1),
                node(2, "订单服务", ValidationModel.SERVICE, 0, 1));
        List<Edge> edges = List.of(depends(10, 2, 1, 0.5));

        PropagationResult r = propagation.propagate(nodes, edges,
                List.of(new Fault(7, 1, "MySQL", "node_down", "high")));

        AffectedNode a = r.affectedNodes().get(0);
        assertEquals(0.5, a.pathStrength(), 1e-9);
        assertEquals(AffectedNode.POSSIBLE, a.impactClass());
        assertEquals(0, r.certainCount());
        assertEquals(1, r.possibleCount());
        assertTrue(r.summary().contains("必崩 0 / 可能 1"));
    }

    @Test
    @DisplayName("E1：宿主机已分配 90% → 放大传播强度，弱依赖也判「必崩」")
    void saturatedHostAmplifies() {
        List<Node> nodes = List.of(
                node(1, "MySQL", ValidationModel.MIDDLEWARE, 0, 1),
                node(2, "订单服务", ValidationModel.SERVICE, 0, 0.6),
                node(3, "host-a", ValidationModel.HOST, 4, 0),
                node(4, "批处理", ValidationModel.CONTAINER, 0, 3.0));
        List<Edge> edges = List.of(
                depends(10, 2, 1, 0.5),
                deploy(11, 2, 3),
                deploy(12, 4, 3));

        PropagationResult r = propagation.propagate(nodes, edges,
                List.of(new Fault(7, 1, "MySQL", "node_down", "high")));

        // 宿主机饱和度 = (0.6 + 3.0) / 4 = 0.9 → 放大系数 1.45；0.5 × 1.45 = 0.725
        AffectedNode a = r.affectedNodes().get(0);
        assertEquals(0.725, a.pathStrength(), 1e-6);
        assertEquals(AffectedNode.CERTAIN, a.impactClass());
        assertEquals(1, r.certainCount());
    }

    @Test
    @DisplayName("E1：强依赖（权重 1.0）+ 闲置宿主机 → 必崩")
    void strongEdgeIsCertain() {
        List<Node> nodes = List.of(
                node(1, "MySQL", ValidationModel.MIDDLEWARE, 0, 1),
                node(2, "订单服务", ValidationModel.SERVICE, 0, 1));
        PropagationResult r = propagation.propagate(nodes, List.of(depends(10, 2, 1, 1.0)),
                List.of(new Fault(7, 1, "MySQL", "node_down", "high")));

        AffectedNode a = r.affectedNodes().get(0);
        assertEquals(1.0, a.pathStrength(), 1e-9);
        assertEquals(AffectedNode.CERTAIN, a.impactClass());
    }

    // ---------- E2 割点 ----------

    @Test
    @DisplayName("E2：星型拓扑的中心是割点，叶子不是")
    void starCenterIsCritical() {
        List<Node> nodes = List.of(
                node(1, "中心服务", ValidationModel.SERVICE, 0, 1),
                node(2, "叶子A", ValidationModel.SERVICE, 0, 1),
                node(3, "叶子B", ValidationModel.SERVICE, 0, 1),
                node(4, "叶子C", ValidationModel.SERVICE, 0, 1));
        List<Edge> edges = List.of(
                depends(10, 2, 1, 1),
                depends(11, 3, 1, 1),
                depends(12, 4, 1, 1));

        List<CriticalNodeAnalyzer.CriticalNode> result = criticalNodes.analyze(nodes, edges);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).nodeId());
        assertEquals(3, result.get(0).componentsAfterRemoval());
        assertEquals(1, result.get(0).largestComponentAfterRemoval());
    }

    @Test
    @DisplayName("E2：链式拓扑 A→B→C 中，中间节点是割点，两端不是")
    void chainMiddleIsCritical() {
        List<Node> nodes = List.of(
                node(1, "C", ValidationModel.SERVICE, 0, 1),
                node(2, "B", ValidationModel.SERVICE, 0, 1),
                node(3, "A", ValidationModel.SERVICE, 0, 1));
        List<Edge> edges = List.of(
                depends(10, 2, 1, 1),
                depends(11, 3, 2, 1));

        List<CriticalNodeAnalyzer.CriticalNode> result = criticalNodes.analyze(nodes, edges);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).nodeId());
    }

    // ---------- E3 隔离切口 ----------

    @Test
    @DisplayName("E3：链式传播，隔离第一层节点即可截断全部影响")
    void cutFirstLayer() {
        List<Node> nodes = List.of(
                node(1, "MySQL", ValidationModel.MIDDLEWARE, 0, 1),
                node(2, "订单服务", ValidationModel.SERVICE, 0, 1),
                node(3, "网关", ValidationModel.GATEWAY, 0, 0.5),
                node(4, "外部用户", ValidationModel.EXTERNAL, 0, 0));
        List<Edge> edges = List.of(
                depends(10, 2, 1, 1),
                depends(11, 3, 2, 1),
                depends(12, 4, 3, 1));

        IsolationCutter.CutResult r = cutter.propose(nodes, edges, Set.of(1L), 3);
        assertEquals(3, r.beforeCount());
        assertEquals(0, r.afterCount());
        assertEquals(3, r.blockedCount());
        assertEquals(List.of(2L), r.cutNodeIds());
        assertEquals(List.of("订单服务"), r.cutNodeNames());
    }

    @Test
    @DisplayName("E3：故障源没有上游依赖时，不需要隔离")
    void nothingToCut() {
        List<Node> nodes = List.of(node(1, "孤立服务", ValidationModel.SERVICE, 0, 1));
        IsolationCutter.CutResult r = cutter.propose(nodes, List.of(), Set.of(1L), 3);
        assertEquals(0, r.beforeCount());
        assertTrue(r.cutNodeIds().isEmpty());
        assertFalse(r.blockedCount() > 0);
    }

    // ---------- 工具 ----------

    private Node node(long id, String name, String kind, double cores, double request) {
        return new Node(id, name, kind, cores, request, 4096, (int) (request * 1024), 0, "healthy");
    }

    private Edge depends(long id, long source, long target, double weight) {
        return new Edge(id, source, target, ValidationModel.DEPENDS_ON, 0L, 0L, weight >= 1.0, weight);
    }

    private Edge deploy(long id, long node, long host) {
        return new Edge(id, node, host, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0);
    }
}
