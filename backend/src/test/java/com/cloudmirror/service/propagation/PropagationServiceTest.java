package com.cloudmirror.service.propagation;

import com.cloudmirror.service.rule.ValidationModel;
import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 传播算法单元测试：不依赖数据库与 Spring，纯算法断言。
 */
class PropagationServiceTest {

    private final PropagationService service = new PropagationService();

    // 拓扑：网关(1) → 订单(2) / 用户(3)；订单 → MySQL(4) / Redis(5)；用户 → MySQL(4)
    // 边语义：source 依赖 target
    private static List<Node> nodes() {
        return List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 1, 0.5, 2048, 256, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "healthy"),
                new Node(3L, "用户服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0, "healthy"),
                new Node(4L, "MySQL", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200, "healthy"),
                new Node(5L, "Redis", ValidationModel.MIDDLEWARE, 2, 0.5, 4096, 512, 20, "healthy"));
    }

    private static List<Edge> edges() {
        return List.of(
                edge(101, 1, 2, ValidationModel.DEPENDS_ON, 1001, 1002),
                edge(102, 1, 3, ValidationModel.DEPENDS_ON, 1001, 1003),
                edge(103, 2, 4, ValidationModel.DEPENDS_ON, 1002, 1004),
                edge(104, 2, 5, ValidationModel.DEPENDS_ON, 1002, 1005),
                edge(105, 3, 4, ValidationModel.DEPENDS_ON, 1003, 1004));
    }

    private static Edge edge(long id, long source, long target, String kind, long sourcePort, long targetPort) {
        return new Edge(id, source, target, kind, sourcePort, targetPort, true, 1.0);
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("单源：MySQL 故障 → 订单/用户直接受影响，网关间接")
    void singleSource() {
        PropagationResult r = service.propagate(nodes(), edges(),
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")));

        assertEquals(3, r.affectedNodes().size());
        assertEquals(2, r.directCount());
        assertEquals(1, r.indirectCount());
        assertEquals(0.6, r.impactRatio(), 0.0001);

        AffectedNode gw = byId(r, 1);
        assertEquals(AffectedNode.INDIRECT, gw.impactLevel());
        assertEquals(2, gw.distance());
        assertEquals(7, gw.sourceFaultId());
        assertEquals(4, gw.sourceNodeId());

        // 路径：MySQL(4) → 订单(2) → 网关(1)
        assertEquals(List.of("MySQL", "订单服务", "网关"), names(gw.path()));
        assertEquals(103L, gw.path().get(1).edgeId());
        assertEquals(1002L, gw.path().get(1).callerPort());
        assertEquals(1004L, gw.path().get(1).calleePort());
    }

    @Test
    @DisplayName("故障源自身不计入受影响节点")
    void faultSourceNotAffected() {
        PropagationResult r = service.propagate(nodes(), edges(),
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")));
        assertTrue(r.affectedNodes().stream().noneMatch(a -> a.nodeId() == 4));
    }

    @Test
    @DisplayName("多源：两个故障同时存在，取最近来源；同距离按故障 ID 打破平局")
    void multiSourceTieBreak() {
        // 网关(1) 同时依赖 订单(2) 和 用户(3)；两边各注入故障
        PropagationResult r = service.propagate(nodes(), edges(), List.of(
                new Fault(7, 2, "订单服务", "service_unavailable", "high"),
                new Fault(8, 3, "用户服务", "service_unavailable", "high")));

        AffectedNode gw = byId(r, 1);
        assertEquals(1, gw.distance());
        assertEquals(7, gw.sourceFaultId(), "同距离应取故障 ID 更小的（7）");
        assertEquals(2, gw.sourceNodeId());
    }

    @Test
    @DisplayName("多源：距离更近的来源优先于距离更远的")
    void nearestSourceWins() {
        // MySQL(4) 距离订单 1 跳，Redis(5) 也是 1 跳；网关距离两者都是 2 跳
        PropagationResult r = service.propagate(nodes(), edges(), List.of(
                new Fault(9, 5, "Redis", "node_down", "medium"),
                new Fault(7, 4, "MySQL", "node_down", "critical")));
        // 订单同时依赖 4 和 5：距离都是 1，取故障 ID 小的 7
        assertEquals(7, byId(r, 2).sourceFaultId());
    }

    @Test
    @DisplayName("环路：A↔B 互相依赖不会死循环，也不重复统计")
    void cycleIsSafe() {
        List<Edge> cyclic = List.of(
                edge(201, 2, 3, ValidationModel.DEPENDS_ON, 0, 0),
                edge(202, 3, 2, ValidationModel.DEPENDS_ON, 0, 0));
        PropagationResult r = service.propagate(nodes(), cyclic,
                List.of(new Fault(7, 2, "订单服务", "node_down", "high")));

        assertEquals(1, r.affectedNodes().size());
        assertEquals(3, r.affectedNodes().get(0).nodeId());
        assertEquals(1, r.affectedNodes().get(0).distance());
    }

    @Test
    @DisplayName("空故障：返回空结果，影响比例 0")
    void noFault() {
        PropagationResult r = service.propagate(nodes(), edges(), List.of());
        assertTrue(r.affectedNodes().isEmpty());
        assertEquals(0.0, r.impactRatio());
        assertEquals(0, r.directCount());
        assertTrue(r.summary().contains("没有激活故障"));
    }

    @Test
    @DisplayName("故障节点已不存在：忽略该故障，不报错")
    void faultOnMissingNode() {
        PropagationResult r = service.propagate(nodes(), edges(),
                List.of(new Fault(7, 99, "已删除节点", "node_down", "high")));
        assertTrue(r.affectedNodes().isEmpty());
        assertTrue(r.faultSources().isEmpty());
    }

    @Test
    @DisplayName("非依赖边不参与传播（部署/网络边不影响结果）")
    void onlyDependsEdgesPropagate() {
        List<Edge> mixed = new ArrayList<>(edges());
        mixed.add(edge(301, 2, 1, ValidationModel.DEPLOY_ON, 0, 0));
        mixed.add(edge(302, 1, 4, ValidationModel.NETWORK_LINK, 0, 0));

        PropagationResult r = service.propagate(nodes(), mixed,
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")));

        // 只有 depends_on 能传播：网关仍是 2 跳（经订单），不会因为 network_link 变成 1 跳
        assertEquals(2, byId(r, 1).distance());
        assertEquals(3, r.affectedNodes().size());
    }

    @Test
    @DisplayName("确定性：同一输入连跑 100 次，结果完全一致")
    void deterministic() {
        List<Fault> faults = List.of(
                new Fault(8, 3, "用户服务", "service_unavailable", "high"),
                new Fault(7, 4, "MySQL", "node_down", "critical"));
        PropagationResult first = service.propagate(nodes(), edges(), faults);
        for (int i = 0; i < 100; i++) {
            PropagationResult r = service.propagate(nodes(), edges(), faults);
            assertEquals(first.toString(), r.toString());
        }
        assertFalse(first.affectedNodes().isEmpty());
    }

    @Test
    @DisplayName("打印：把推演结果写到 target/propagation-demo.txt，便于人工核对")
    void printReport() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 场景：MySQL 宕机（critical） =====\n");
        PropagationResult r = service.propagate(nodes(), edges(),
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")));
        sb.append(r.summary()).append('\n');
        sb.append(String.format("直接 %d / 间接 %d / 节点总数 %d / 影响比例 %.0f%%%n",
                r.directCount(), r.indirectCount(), r.totalNodeCount(), r.impactRatio() * 100));
        for (AffectedNode a : r.affectedNodes()) {
            sb.append(String.format("  %-6s 距离 %d  来源故障 #%d（%s）%n",
                    a.nodeName(), a.distance(), a.sourceFaultId(), a.impactLevel()));
            sb.append("        路径：").append(String.join(" → ", names(a.path()))).append('\n');
        }

        sb.append("\n===== 场景：MySQL 与 用户服务 同时故障 =====\n");
        PropagationResult multi = service.propagate(nodes(), edges(), List.of(
                new Fault(7, 4, "MySQL", "node_down", "critical"),
                new Fault(8, 3, "用户服务", "service_unavailable", "high")));
        sb.append(multi.summary()).append('\n');
        for (AffectedNode a : multi.affectedNodes()) {
            sb.append(String.format("  %-6s 距离 %d  来源故障 #%d%n",
                    a.nodeName(), a.distance(), a.sourceFaultId()));
        }

        System.out.println(sb);
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("target", "propagation-demo.txt"),
                sb.toString(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("依赖冗余度：3 个下游挂 1 个 → 强度打折为 1/3，判可能受影响")
    void dependencyRedundancyDiscountsStrength() {
        List<Node> nodes = List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 0, 1, 0, 1024, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 1024, 0, "healthy"),
                new Node(3L, "MySQL", ValidationModel.MIDDLEWARE, 0, 1, 0, 1024, 0, "healthy"),
                new Node(4L, "Redis", ValidationModel.MIDDLEWARE, 0, 1, 0, 1024, 0, "healthy"));
        List<Edge> edges = List.of(
                edge(101, 1, 2, ValidationModel.DEPENDS_ON, 0, 0),
                edge(102, 2, 3, ValidationModel.DEPENDS_ON, 0, 0),
                edge(103, 2, 4, ValidationModel.DEPENDS_ON, 0, 0));

        PropagationResult r = service.propagate(nodes, edges,
                List.of(new Fault(7, 3, "MySQL", "node_down", "critical")));

        AffectedNode order = byId(r, 2);
        assertEquals(0.5, order.dependencyRatio(), 1e-9, "2 个下游挂 1 个，冗余度应为 0.5");
        assertEquals(0.5, order.pathStrength(), 1e-9);
        assertEquals(AffectedNode.POSSIBLE, order.impactClass());
    }

    @Test
    @DisplayName("时间窗：按边延迟累加传播耗时，可只看 1 秒内的影响")
    void windowFiltersByArrivalTime() {
        List<Node> nodes = List.of(
                new Node(1L, "网关", ValidationModel.GATEWAY, 0, 1, 0, 1024, 0, "healthy"),
                new Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 1024, 0, "healthy"),
                new Node(3L, "用户服务", ValidationModel.SERVICE, 0, 1, 0, 1024, 0, "healthy"),
                new Node(4L, "MySQL", ValidationModel.MIDDLEWARE, 0, 1, 0, 1024, 0, "healthy"));
        List<Edge> edges = List.of(
                edgeWithLatency(101, 2, 4, 500),
                edgeWithLatency(102, 3, 2, 500),
                edgeWithLatency(103, 1, 3, 500));

        PropagationResult all = service.propagate(nodes, edges,
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")));
        assertEquals(3, all.affectedNodes().size());
        assertEquals(500, byId(all, 2).arrivalMillis());
        assertEquals(1000, byId(all, 3).arrivalMillis());
        assertEquals(1500, byId(all, 1).arrivalMillis());

        PropagationResult inOneSecond = service.propagate(nodes, edges,
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")), 1);
        assertEquals(2, inOneSecond.affectedNodes().size(), "1 秒内只波及订单、用户");
        assertTrue(inOneSecond.summary().contains("1 秒内"));

        PropagationResult inHalfSecond = service.propagate(nodes, edges,
                List.of(new Fault(7, 4, "MySQL", "node_down", "critical")), 0);
        assertEquals(0, inHalfSecond.affectedNodes().size());
    }

    // ---------- 工具 ----------

    private static Edge edgeWithLatency(long id, long source, long target, int latencyMs) {
        return new Edge(id, source, target, ValidationModel.DEPENDS_ON, 0L, 0L, true, 1.0,
                null, latencyMs);
    }

    private AffectedNode byId(PropagationResult r, long id) {
        return r.affectedNodes().stream()
                .filter(a -> a.nodeId() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有找到受影响节点 " + id));
    }

    private List<String> names(List<PathHop> path) {
        return path.stream().map(PathHop::nodeName).toList();
    }
}
