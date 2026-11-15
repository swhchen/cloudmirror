package com.cloudmirror.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口集成测试：真 Spring 容器 + 真 H2 数据库 + MockMvc。
 *
 * <p>覆盖：工程 CRUD、节点（含端口）、连线、校验执行与落库回看。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // ---------- 用例 ----------

    @Test
    @DisplayName("全链路：建工程 → 建节点/端口 → 连线 → 校验通过 → 加容器后 CPU 超配")
    void fullValidateFlow() throws Exception {
        long projectId = createProject("电商-" + UUID.randomUUID());
        long hostId = createNode(projectId, node("host-a", "host")
                .put("cpuCores", 4).put("memTotalMb", 8192).put("diskGb", 1000).build());
        long gatewayId = createNode(projectId, node("网关", "gateway")
                .put("cpuRequest", 0.5).put("memRequestMb", 256)
                .ports(port(8080, "public", 18080)).build());
        long orderId = createNode(projectId, node("订单服务", "service")
                .put("cpuRequest", 1).put("memRequestMb", 2048)
                .ports(port(8081, "internal", null)).build());
        long dbId = createNode(projectId, node("MySQL", "middleware")
                .put("cpuRequest", 1).put("memRequestMb", 2048).put("diskGb", 0)
                .ports(port(3306, "internal", null)).build());

        long gatewayPort = portIdOf(projectId, gatewayId, 8080);
        long orderPort = portIdOf(projectId, orderId, 8081);
        long dbPort = portIdOf(projectId, dbId, 3306);

        createEdge(projectId, "deploy_on", gatewayId, hostId, 0, 0);
        createEdge(projectId, "deploy_on", orderId, hostId, 0, 0);
        createEdge(projectId, "deploy_on", dbId, hostId, 0, 0);
        createEdge(projectId, "depends_on", gatewayId, orderId, gatewayPort, orderPort);
        createEdge(projectId, "depends_on", orderId, dbId, orderPort, dbPort);

        JsonNode ok = postOk("/api/projects/" + projectId + "/validate", Map.of());
        assertTrue(ok.path("data").path("result").path("runnable").asBoolean(),
                "基线应可运行：" + ok.path("data").path("result").path("items"));
        assertEquals(100, ok.path("data").path("result").path("score").asInt());
        long firstRunId = ok.path("data").path("runId").asLong();

        // 再加一个 2C 容器 → host-a 3 + 2 = 5 核 > 4 核
        long extraId = createNode(projectId, node("批处理容器", "container")
                .put("cpuRequest", 2).put("memRequestMb", 1024)
                .ports(port(9000, "internal", null)).build());
        createEdge(projectId, "deploy_on", extraId, hostId, 0, 0);

        JsonNode bad = postOk("/api/projects/" + projectId + "/validate", Map.of());
        assertFalse(bad.path("data").path("result").path("runnable").asBoolean());
        assertEquals(70, bad.path("data").path("result").path("score").asInt());
        String messages = bad.path("data").path("result").path("items").toString();
        assertTrue(messages.contains("CPU 超配") && messages.contains("4.5"),
                "应报 CPU 超配 4.5/4.0，实际：" + messages);

        // 历史回看：最近一次校验应能读出来
        MvcResult latest = mvc.perform(get("/api/projects/" + projectId + "/validations/latest"))
                .andReturn();
        JsonNode latestJson = read(latest);
        assertEquals(200, latest.getResponse().getStatus());
        assertTrue(latestJson.path("data").path("runId").asLong() > firstRunId);
        assertEquals(1, latestJson.path("data").path("errorCount").asInt());
        assertTrue(latestJson.path("data").path("items").size() >= 1);
    }

    @Test
    @DisplayName("推演全链路：冗余组 → 注入故障 → 传播分析 → 恢复推荐 → 历史回看")
    void faultAnalysisRecoveryFlow() throws Exception {
        long projectId = createProject("推演-" + UUID.randomUUID());
        long hostId = createNode(projectId, node("host-a", "host")
                .put("cpuCores", 4).put("memTotalMb", 8192).put("diskGb", 1000).build());
        long gatewayId = createNode(projectId, node("网关", "gateway")
                .put("cpuRequest", 0.5).put("memRequestMb", 256)
                .ports(port(8080, "public", 18080)).build());
        long orderId = createNode(projectId, node("订单服务", "service")
                .put("cpuRequest", 1).put("memRequestMb", 2048)
                .ports(port(8081, "internal", null)).build());
        long primaryId = createNode(projectId, node("MySQL主", "middleware")
                .put("cpuRequest", 1).put("memRequestMb", 2048)
                .ports(port(3306, "internal", null)).build());
        long standbyId = createNode(projectId, node("MySQL备", "middleware")
                .put("cpuRequest", 1).put("memRequestMb", 2048)
                .ports(port(3306, "internal", null)).build());

        long gatewayPort = portIdOf(projectId, gatewayId, 8080);
        long orderPort = portIdOf(projectId, orderId, 8081);
        long dbPort = portIdOf(projectId, primaryId, 3306);

        for (long id : new long[]{gatewayId, orderId, primaryId, standbyId}) {
            createEdge(projectId, "deploy_on", id, hostId, 0, 0);
        }
        createEdge(projectId, "depends_on", gatewayId, orderId, gatewayPort, orderPort);
        createEdge(projectId, "depends_on", orderId, primaryId, orderPort, dbPort);

        JsonNode group = postOk("/api/projects/" + projectId + "/redundancy-groups", Map.of(
                "name", "MySQL 主备",
                "redundancyType", "active_standby",
                "description", "演示用主备",
                "members", List.of(
                        Map.of("nodeId", primaryId, "role", "primary"),
                        Map.of("nodeId", standbyId, "role", "standby", "priority", 1))));
        assertEquals(2, group.path("data").path("members").size());

        JsonNode validation = postOk("/api/projects/" + projectId + "/validate", Map.of());
        assertTrue(validation.path("data").path("result").path("runnable").asBoolean(),
                "带完整主备组的基线应可运行：" + validation.path("data").path("result").path("items"));

        postOk("/api/projects/" + projectId + "/faults", Map.of(
                "nodeId", primaryId, "faultType", "node_down",
                "severity", "critical", "description", "主库宕机"));

        JsonNode analysis = postOk("/api/projects/" + projectId + "/analyses", Map.of());
        JsonNode result = analysis.path("data").path("result");
        assertEquals(2, result.path("affectedNodes").size(), "订单直接 + 网关间接：" + result);
        assertTrue(result.path("summary").asText().contains("波及"));
        long analysisId = analysis.path("data").path("analysisId").asLong();

        JsonNode recovery = postOk("/api/projects/" + projectId + "/recovery/recommend", Map.of());
        JsonNode plan = recovery.path("data").path("result");
        assertEquals("failover", plan.path("recommendedType").asText(), "推荐理由：" + plan.path("recommendationReason"));
        assertEquals(3, plan.path("strategies").size());

        JsonNode history = read(mvc.perform(get("/api/projects/" + projectId + "/analyses")).andReturn());
        assertEquals(1, history.path("data").path("items").size());
        assertEquals(1, history.path("data").path("total").asInt());

        JsonNode detail = read(mvc.perform(get("/api/projects/" + projectId
                + "/analyses/" + analysisId)).andReturn());
        assertTrue(detail.path("data").path("affectedNodes").size() >= 2);
        assertTrue(detail.path("data").path("affectedNodes").get(0).path("path").size() >= 2,
                "路径快照应落库：" + detail.path("data").path("affectedNodes"));
    }

    @Test
    @DisplayName("接口级边缺端口 → 400（调用依赖必须指定两端端口）")
    void dependsOnWithoutPortsShouldFail() throws Exception {
        long projectId = createProject("端口校验-" + UUID.randomUUID());
        long a = createNode(projectId, node("调用方", "service").build());
        long b = createNode(projectId, node("被调方", "middleware").build());

        MvcResult result = mvc.perform(post("/api/projects/" + projectId + "/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "edgeKind", "depends_on",
                                "sourceNodeId", a,
                                "targetNodeId", b))))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(read(result).path("message").asText().contains("必须指定源端口和目标端口"));
    }

    @Test
    @DisplayName("保存节点（端口不变）→ 连线保留，不会被连带删除")
    void savingNodeKeepsEdges() throws Exception {
        long projectId = createProject("保存节点-" + UUID.randomUUID());
        long serviceId = createNode(projectId, node("订单", "service")
                .put("cpuRequest", 1).put("memRequestMb", 1024)
                .ports(port(8081, "internal", null)).build());
        long dbId = createNode(projectId, node("MySQL", "middleware")
                .put("cpuRequest", 1).put("memRequestMb", 2048)
                .ports(port(3306, "internal", null)).build());
        long servicePort = portIdOf(projectId, serviceId, 8081);
        long dbPort = portIdOf(projectId, dbId, 3306);
        createEdge(projectId, "depends_on", serviceId, dbId, servicePort, dbPort);

        // 只改 CPU，端口原样提交
        Map<String, Object> update = new HashMap<>();
        update.put("name", "订单");
        update.put("nodeKind", "service");
        update.put("cpuRequest", 2);
        update.put("memRequestMb", 2048);
        update.put("ports", List.of(Map.of("port", 8081, "protocol", "tcp", "scope", "internal")));
        mvc.perform(put("/api/projects/" + projectId + "/nodes/" + serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(update)))
                .andExpect(status().isOk());

        JsonNode edges = read(mvc.perform(get("/api/projects/" + projectId + "/edges")).andReturn());
        assertEquals(1, edges.path("data").size(), "保存节点后连线应保留：" + edges);
    }

    @Test
    @DisplayName("重名节点 → 409，且提示中文原因")
    void duplicateNodeNameShouldConflict() throws Exception {
        long projectId = createProject("重名测试-" + UUID.randomUUID());
        createNode(projectId, node("重复名", "container").build());

        MvcResult result = mvc.perform(post("/api/projects/" + projectId + "/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(node("重复名", "container").build())))
                .andReturn();
        assertEquals(409, result.getResponse().getStatus());
        assertTrue(read(result).path("message").asText().contains("节点名已存在"));
    }

    @Test
    @DisplayName("自依赖连线 → 400")
    void selfEdgeShouldFail() throws Exception {
        long projectId = createProject("自依赖测试-" + UUID.randomUUID());
        long nodeId = createNode(projectId, node("孤立", "container").build());

        MvcResult result = mvc.perform(post("/api/projects/" + projectId + "/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "edgeKind", "depends_on",
                                "sourceNodeId", nodeId,
                                "targetNodeId", nodeId))))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(read(result).path("message").asText().contains("自依赖"));
    }

    @Test
    @DisplayName("型号库：内置 16 个，可按类型过滤")
    void nodeModelsShouldBeSeeded() throws Exception {
        JsonNode all = read(mvc.perform(get("/api/node-models")).andReturn());
        assertTrue(all.path("data").size() >= 16, "内置型号应已灌入");

        JsonNode hosts = read(mvc.perform(get("/api/node-models").param("kind", "host")).andReturn());
        assertTrue(hosts.path("data").size() >= 4);
        assertEquals("host", hosts.path("data").get(0).path("kind").asText());
    }

    // ---------- 工具 ----------

    private JsonNode findNode(JsonNode nodes, String name) {
        for (JsonNode n : nodes) {
            if (name.equals(n.path("name").asText())) {
                return n;
            }
        }
        throw new AssertionError("没找到节点：" + name + "，实际：" + nodes);
    }

    private long createProject(String name) throws Exception {
        JsonNode r = postOk("/api/projects", Map.of("name", name));
        return r.path("data").path("id").asLong();
    }

    private long createNode(long projectId, Map<String, Object> body) throws Exception {
        JsonNode r = postOk("/api/projects/" + projectId + "/nodes", body);
        return r.path("data").path("id").asLong();
    }

    private void createEdge(long projectId, String kind, long source, long target,
                            long sourcePort, long targetPort) throws Exception {
        postOk("/api/projects/" + projectId + "/edges", Map.of(
                "edgeKind", kind,
                "sourceNodeId", source,
                "targetNodeId", target,
                "sourcePortId", sourcePort,
                "targetPortId", targetPort));
    }

    private JsonNode postOk(String url, Object body) throws Exception {
        MvcResult result = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus(),
                "请求失败：" + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return read(result);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long portIdOf(long projectId, long nodeId, int port) throws Exception {
        JsonNode r = read(mvc.perform(get("/api/projects/" + projectId + "/nodes")).andReturn());
        for (JsonNode n : r.path("data")) {
            if (n.path("id").asLong() != nodeId) {
                continue;
            }
            for (JsonNode p : n.path("ports")) {
                if (p.path("port").asInt() == port) {
                    return p.path("id").asLong();
                }
            }
        }
        throw new AssertionError("没找到端口 " + port);
    }

    private NodeBuilder node(String name, String kind) {
        return new NodeBuilder(name, kind);
    }

    private Map<String, Object> port(int port, String scope, Integer hostPort) {
        Map<String, Object> p = new HashMap<>();
        p.put("port", port);
        p.put("protocol", "tcp");
        p.put("scope", scope);
        if (hostPort != null) {
            p.put("hostPort", hostPort);
        }
        return p;
    }

    /** 节点请求构造器，避免一堆 Map.of 嵌套 */
    private static final class NodeBuilder {
        private final Map<String, Object> body = new HashMap<>();

        NodeBuilder(String name, String kind) {
            body.put("name", name);
            body.put("nodeKind", kind);
        }

        NodeBuilder put(String key, Object value) {
            body.put(key, value);
            return this;
        }

        NodeBuilder ports(Map<String, Object>... ports) {
            body.put("ports", List.of(ports));
            return this;
        }

        Map<String, Object> build() {
            return body;
        }
    }
}
