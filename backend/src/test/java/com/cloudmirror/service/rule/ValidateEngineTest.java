package com.cloudmirror.service.rule;

import com.cloudmirror.service.rule.impl.R01PortConflict;
import com.cloudmirror.service.rule.impl.R02PortOutOfRange;
import com.cloudmirror.service.rule.impl.R03PortMapConflict;
import com.cloudmirror.service.rule.impl.R04CpuOvercommit;
import com.cloudmirror.service.rule.impl.R05MemOvercommit;
import com.cloudmirror.service.rule.impl.R06DiskOvercommit;
import com.cloudmirror.service.rule.impl.R07DependencyMissing;
import com.cloudmirror.service.rule.impl.R08NetworkUnreachable;
import com.cloudmirror.service.rule.impl.R09CircularDependency;
import com.cloudmirror.service.rule.impl.R10EntryUnreachable;
import com.cloudmirror.service.rule.impl.R11OrphanNode;
import com.cloudmirror.service.rule.impl.R12RedundancyIncomplete;
import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Group;
import com.cloudmirror.service.rule.ValidationModel.Member;
import com.cloudmirror.service.rule.ValidationModel.Node;
import com.cloudmirror.service.rule.ValidationModel.Port;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验引擎单元测试：用内存里的电商 9+1 节点装配跑 12 条规则。
 *
 * <p>不依赖数据库、不依赖 Spring，纯算法断言。
 */
class ValidateEngineTest {

    // ---------- 引擎装配 ----------

    private static ValidateService engine() {
        return new ValidateService(List.of(
                new R01PortConflict(),
                new R02PortOutOfRange(),
                new R03PortMapConflict(),
                new R04CpuOvercommit(),
                new R05MemOvercommit(),
                new R06DiskOvercommit(),
                new R07DependencyMissing(),
                new R08NetworkUnreachable(),
                new R09CircularDependency(),
                new R10EntryUnreachable(),
                new R11OrphanNode(),
                new R12RedundancyIncomplete()));
    }

    // ---------- 测试夹具：电商系统 ----------

    /**
     * 电商示例：
     * <pre>
     * external → gateway(8080/public) → order-svc(8081) / user-svc(8082)
     * order-svc → MySQL 主(3306) / Redis(6379) / MQ(5672)
     * user-svc  → MySQL 主
     * MySQL 主 --replicate--> MySQL 备(3307)，并组成主备冗余组
     * host-a(4C8G)：external? 否；gateway / order / user / redis
     * host-b(4C8G)：MySQL 主 / MySQL 备 / MQ
     * host-a --network_link-- host-b
     * </pre>
     * CPU：host-a = 0.5 + 1 + 1 + 0.5 = 3 ≤ 4；host-b = 1 + 1 + 1 = 3 ≤ 4
     * 内存：host-a = 256+2048+2048+512 = 4864 ≤ 8192；host-b = 2048×3 = 6144 ≤ 8192
     */
    private static Fixture ecommerce() {
        Fixture f = new Fixture();
        f.node(1, "网关", ValidationModel.GATEWAY, 1, 0.5, 2048, 256, 0);
        f.node(2, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0);
        f.node(3, "用户服务", ValidationModel.SERVICE, 0, 1, 0, 2048, 0);
        f.node(4, "MySQL 主", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200);
        f.node(5, "MySQL 备", ValidationModel.MIDDLEWARE, 4, 1, 8192, 2048, 200);
        f.node(6, "Redis", ValidationModel.MIDDLEWARE, 2, 0.5, 4096, 512, 20);
        f.node(7, "MQ", ValidationModel.MIDDLEWARE, 2, 1, 4096, 2048, 100);
        f.node(8, "host-a", ValidationModel.HOST, 4, 0, 8192, 0, 1000);
        f.node(9, "host-b", ValidationModel.HOST, 4, 0, 8192, 0, 1000);
        f.node(10, "外部用户", ValidationModel.EXTERNAL, 0, 0, 0, 0, 0);

        // 端口
        f.port(101, 1, 8080, "tcp", "public", 18080);
        f.port(102, 2, 8081, "tcp", "internal", null);
        f.port(103, 3, 8082, "tcp", "internal", null);
        f.port(104, 4, 3306, "tcp", "internal", null);
        f.port(105, 5, 3306, "tcp", "internal", null); // 备用与主同端口、不同节点（真实主备做法）
        f.port(106, 6, 6379, "tcp", "internal", null);
        f.port(107, 7, 5672, "tcp", "internal", null);

        // 部署
        f.edge(201, 1, 8, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(202, 2, 8, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(203, 3, 8, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(204, 6, 8, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(205, 4, 9, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(206, 5, 9, ValidationModel.DEPLOY_ON, 0, 0);
        f.edge(207, 7, 9, ValidationModel.DEPLOY_ON, 0, 0);

        // 网络
        f.edge(208, 8, 9, ValidationModel.NETWORK_LINK, 0, 0);

        // 依赖（源端口 → 目标端口）
        f.edge(210, 10, 1, ValidationModel.DEPENDS_ON, 0, 101);
        f.edge(211, 1, 2, ValidationModel.DEPENDS_ON, 101, 102);
        f.edge(212, 1, 3, ValidationModel.DEPENDS_ON, 101, 103);
        f.edge(213, 2, 4, ValidationModel.DEPENDS_ON, 102, 104);
        f.edge(214, 2, 6, ValidationModel.DEPENDS_ON, 102, 106);
        f.edge(215, 2, 7, ValidationModel.DEPENDS_ON, 102, 107);
        f.edge(216, 3, 4, ValidationModel.DEPENDS_ON, 103, 104);

        // 主备复制 + 冗余组
        f.edge(217, 4, 5, ValidationModel.REPLICATE, 104, 105);
        f.group(1, "MySQL 主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(4L, ValidationModel.PRIMARY, null),
                        new Member(5L, ValidationModel.STANDBY, 1)));
        return f;
    }

    // ---------- 测试 ----------

    @Test
    @DisplayName("空工程：不判「可运行 100 分」，而是提示先装配")
    void emptyProjectIsNotRunnable() {
        ValidateResult r = engine().validate(new ValidateContext(List.of(), List.of(), List.of(), List.of()));
        assertFalse(r.runnable());
        assertEquals(0, r.score());
        assertTrue(r.summary().contains("还没有节点"), r.summary());
    }

    @Test
    @DisplayName("R07：服务经另一个服务间接依赖中间件 → 不算缺失")
    void transitiveDependencySatisfiesR07() {
        List<ValidationModel.Node> nodes = List.of(
                new ValidationModel.Node(1L, "网关", ValidationModel.GATEWAY, 0, 1, 0, 512, 0, "healthy"),
                new ValidationModel.Node(2L, "订单服务", ValidationModel.SERVICE, 0, 1, 0, 1024, 0, "healthy"),
                new ValidationModel.Node(3L, "用户服务", ValidationModel.SERVICE, 0, 1, 0, 1024, 0, "healthy"),
                new ValidationModel.Node(4L, "MySQL", ValidationModel.MIDDLEWARE, 0, 1, 0, 2048, 0, "healthy"),
                new ValidationModel.Node(5L, "宿主", ValidationModel.HOST, 4, 0, 8192, 0, 100, "healthy"));
        List<ValidationModel.Edge> edges = List.of(
                new ValidationModel.Edge(301L, 1L, 2L, ValidationModel.DEPENDS_ON, 10L, 20L, true, 1.0),
                new ValidationModel.Edge(302L, 2L, 3L, ValidationModel.DEPENDS_ON, 21L, 30L, true, 1.0),
                new ValidationModel.Edge(303L, 3L, 4L, ValidationModel.DEPENDS_ON, 31L, 40L, true, 1.0),
                new ValidationModel.Edge(304L, 1L, 5L, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0),
                new ValidationModel.Edge(305L, 2L, 5L, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0),
                new ValidationModel.Edge(306L, 3L, 5L, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0),
                new ValidationModel.Edge(307L, 4L, 5L, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0));

        List<ValidationModel.Port> ports = List.of(
                new ValidationModel.Port(10L, 1L, 8080, "tcp", "internal", null),
                new ValidationModel.Port(20L, 2L, 8081, "tcp", "internal", null),
                new ValidationModel.Port(21L, 2L, 8082, "tcp", "internal", null),
                new ValidationModel.Port(30L, 3L, 8083, "tcp", "internal", null),
                new ValidationModel.Port(31L, 3L, 8084, "tcp", "internal", null),
                new ValidationModel.Port(40L, 4L, 3306, "tcp", "internal", null));

        ValidateResult r = engine().validate(new ValidateContext(nodes, ports, edges, List.of()));
        assertTrue(r.items().stream().noneMatch(i -> i.ruleCode().equals("R07")),
                "间接依赖不应报 R07：" + r.items());
    }

    @Test
    @DisplayName("基线：电商 10 节点装配全部通过，评分 100")
    void baselineShouldBeRunnable() {
        ValidateResult r = engine().validate(ecommerce().ctx());
        assertTrue(r.runnable(), "基线应可运行，实际：" + r.summary() + " " + r.items());
        assertEquals(100, r.score());
        assertEquals(0, r.items().size());
    }

    @Test
    @DisplayName("R04：host-a 再加一个 2C 容器 → CPU 超配，不可运行，评分 70")
    void cpuOvercommitShouldFail() {
        Fixture f = ecommerce();
        f.node(11, "批处理容器", ValidationModel.CONTAINER, 0, 2, 0, 1024, 0);
        f.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertEquals(70, r.score());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R04")
                        && i.message().contains("host-a") && i.message().contains("5.0")),
                "应报 host-a CPU 5.0/4.0，实际：" + r.items());
    }

    @Test
    @DisplayName("R01：两个节点在同一宿主机抢同一端口 → 冲突")
    void portConflictShouldFail() {
        Fixture f = ecommerce();
        // 订单服务额外暴露 18081 到 host-a，用户服务也映射到 18081
        f.port(120, 2, 9001, "tcp", "public", 18081);
        f.port(121, 3, 9002, "tcp", "public", 18081);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R01")
                        && i.message().contains("18081")),
                "应报 18081 端口冲突，实际：" + r.items());
    }

    @Test
    @DisplayName("R02：端口 70000 越界 → 报错")
    void portOutOfRangeShouldFail() {
        Fixture f = ecommerce();
        f.port(130, 6, 70000, "tcp", "internal", null);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R02")),
                "应报端口越界，实际：" + r.items());
    }

    @Test
    @DisplayName("R09：订单与用户互相依赖 → 循环依赖警告，但仍可运行")
    void circularDependencyShouldWarn() {
        Fixture f = ecommerce();
        f.edge(230, 2, 3, ValidationModel.DEPENDS_ON, 102, 103); // order → user
        f.edge(231, 3, 2, ValidationModel.DEPENDS_ON, 103, 102); // user → order（成环）

        ValidateResult r = engine().validate(f.ctx());
        assertTrue(r.runnable(), "循环依赖只是警告，不应判不可运行");
        assertEquals(1, r.warnCount());
        assertEquals(90, r.score());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R09")),
                "应报循环依赖，实际：" + r.items());
    }

    @Test
    @DisplayName("R10：断开网关到订单服务的依赖 → 入口不可达")
    void entryUnreachableShouldFail() {
        Fixture f = ecommerce();
        f.removeEdge(211);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R10")
                        && i.message().contains("订单服务")),
                "应报订单服务不可达，实际：" + r.items());
    }

    @Test
    @DisplayName("R12：主备组缺少备用节点 → 警告")
    void redundancyIncompleteShouldWarn() {
        Fixture f = ecommerce();
        f.groups.clear();
        f.group(2, "只有主的冗余组", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(4L, ValidationModel.PRIMARY, null)));

        ValidateResult r = engine().validate(f.ctx());
        assertTrue(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R12")
                        && i.message().contains("没有备用节点")),
                "应报缺备用，实际：" + r.items());
    }

    @Test
    @DisplayName("确定性：同一输入连跑 100 次，结论与明细完全一致")
    void shouldBeDeterministic() {
        Fixture f = ecommerce();
        f.node(11, "批处理容器", ValidationModel.CONTAINER, 0, 2, 0, 1024, 0);
        f.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);
        ValidateService svc = engine();

        ValidateResult first = svc.validate(f.ctx());
        for (int i = 0; i < 100; i++) {
            ValidateResult r = svc.validate(f.ctx());
            assertEquals(first.score(), r.score());
            assertEquals(first.runnable(), r.runnable());
            assertEquals(first.items().toString(), r.items().toString());
        }
    }

    @Test
    @DisplayName("R07：调用依赖没有端口 → 报错（接口级连线必须落到端口）")
    void dependencyWithoutPortsShouldFail() {
        Fixture f = ecommerce();
        f.edge(250, 1, 2, ValidationModel.DEPENDS_ON, 0, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R07")
                        && i.message().contains("没有指定端口")),
                "应报依赖边缺端口，实际：" + r.items());
    }

    @Test
    @DisplayName("R03：两条端口映射抢同一宿主机端口 → 冲突")
    void portMapConflictShouldFail() {
        Fixture f = ecommerce();
        // 订单/用户各有一个内部端口 8080，都通过 port_map 映射到 host-a 的 8080
        f.port(120, 2, 8080, "tcp", "internal", null);
        f.port(121, 3, 8080, "tcp", "internal", null);
        f.edge(240, 2, 8, ValidationModel.PORT_MAP, 120, 0);
        f.edge(241, 3, 8, ValidationModel.PORT_MAP, 121, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R03")
                        && i.message().contains("8080")),
                "应报端口映射冲突，实际：" + r.items());
    }

    @Test
    @DisplayName("R05：host-a 加一个 8G 内存的容器 → 内存超配")
    void memOvercommitShouldFail() {
        Fixture f = ecommerce();
        f.node(11, "大内存容器", ValidationModel.CONTAINER, 0, 0.5, 0, 8192, 0);
        f.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertEquals(70, r.score());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R05")
                        && i.message().contains("内存超配")),
                "应报内存超配，实际：" + r.items());
    }

    @Test
    @DisplayName("R06：host-a 加一个 2000G 磁盘的容器 → 磁盘超配")
    void diskOvercommitShouldFail() {
        Fixture f = ecommerce();
        f.node(11, "大磁盘容器", ValidationModel.CONTAINER, 0, 0.5, 0, 512, 2000);
        f.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R06")
                        && i.message().contains("磁盘超配")),
                "应报磁盘超配，实际：" + r.items());
    }

    @Test
    @DisplayName("R11：孤立节点只是提示，不影响可运行")
    void orphanNodeShouldBeInfoOnly() {
        Fixture f = ecommerce();
        f.node(12, "孤立容器", ValidationModel.CONTAINER, 0, 0.5, 0, 512, 0);

        ValidateResult r = engine().validate(f.ctx());
        assertTrue(r.runnable());
        assertEquals(1, r.infoCount());
        assertEquals(98, r.score());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R11")
                        && i.message().contains("孤立容器")),
                "应报孤儿节点，实际：" + r.items());
    }

    @Test
    @DisplayName("R12：主备端口不一致 → 警告（切换后连不上）")
    void redundancyPortMismatchShouldWarn() {
        Fixture f = ecommerce();
        f.removePort(105);
        f.port(125, 5, 3307, "tcp", "internal", null); // 备库换端口

        ValidateResult r = engine().validate(f.ctx());
        assertTrue(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R12")
                        && i.message().contains("主备端口不一致")),
                "应报主备端口不一致，实际：" + r.items());
    }

    @Test
    @DisplayName("R01：同一节点重复声明同一端口 → 也算冲突")
    void duplicatePortOnSameNodeShouldFail() {
        Fixture f = ecommerce();
        f.port(126, 6, 6379, "tcp", "internal", null); // Redis 又声明了一次 6379

        ValidateResult r = engine().validate(f.ctx());
        assertFalse(r.runnable());
        assertTrue(r.items().stream().anyMatch(i -> i.ruleCode().equals("R01")
                        && i.message().contains("重复声明")),
                "应报重复声明，实际：" + r.items());
    }

    @Test
    @DisplayName("规则级汇总：12 条规则全在，基线全通过；超配后 R04 变未通过")
    void ruleStatusShouldCoverAllRules() {
        ValidateResult ok = engine().validate(ecommerce().ctx());
        assertEquals(12, ok.ruleStatus().size());
        assertTrue(ok.ruleStatus().stream().allMatch(ValidateResult.RuleStatus::passed));

        Fixture bad = ecommerce();
        bad.node(11, "批处理容器", ValidationModel.CONTAINER, 0, 2, 0, 1024, 0);
        bad.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);
        ValidateResult r = engine().validate(bad.ctx());
        ValidateResult.RuleStatus r04 = r.ruleStatus().stream()
                .filter(s -> s.code().equals("R04")).findFirst().orElseThrow();
        assertFalse(r04.passed());
        assertEquals(1, r04.issueCount());
    }

    @Test
    @DisplayName("打印：把判定结果写到 target/validate-demo.txt，便于人工核对")
    void printReport() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 场景 1：电商装配（基线） =====\n");
        append(sb, engine().validate(ecommerce().ctx()));

        Fixture bad = ecommerce();
        bad.node(11, "批处理容器", ValidationModel.CONTAINER, 0, 2, 0, 1024, 0);
        bad.edge(220, 11, 8, ValidationModel.DEPLOY_ON, 0, 0);
        bad.port(120, 2, 9001, "tcp", "public", 18081);
        bad.port(121, 3, 9002, "tcp", "public", 18081);
        sb.append("\n===== 场景 2：加 2C 容器 + 端口撞车 =====\n");
        append(sb, engine().validate(bad.ctx()));

        System.out.println(sb);
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("target", "validate-demo.txt"),
                sb.toString(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private void append(StringBuilder sb, ValidateResult r) {
        sb.append(String.format("可运行=%s 评分=%d 错误=%d 警告=%d 提示=%d%n 结论：%s%n",
                r.runnable(), r.score(), r.errorCount(), r.warnCount(), r.infoCount(), r.summary()));
        r.items().forEach(i -> sb.append(String.format("  [%s] %s %s%n      建议：%s  节点=%s%n",
                i.severity(), i.ruleCode(), i.message(), i.suggestion(), i.nodeIds())));
    }

    // ---------- 夹具构造器 ----------

    private static final class Fixture {
        final List<Node> nodes = new ArrayList<>();
        final List<Port> ports = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();

        void node(long id, String name, String kind, double cpuCores, double cpuRequest,
                  int memTotalMb, int memRequestMb, double diskGb) {
            nodes.add(new Node(id, name, kind, cpuCores, cpuRequest, memTotalMb, memRequestMb, diskGb, "healthy"));
        }

        void port(long id, long nodeId, int port, String protocol, String scope, Integer hostPort) {
            ports.add(new Port(id, nodeId, port, protocol, scope, hostPort));
        }

        void edge(long id, long source, long target, String kind, long sourcePortId, long targetPortId) {
            edges.add(new Edge(id, source, target, kind, sourcePortId, targetPortId, true, 1.0));
        }

        void group(long id, String name, String type, List<Member> members) {
            groups.add(new Group(id, name, type, members));
        }

        void removeEdge(long id) {
            edges.removeIf(e -> e.id() == id);
        }

        void removePort(long id) {
            ports.removeIf(p -> p.id() == id);
        }

        ValidateContext ctx() {
            return new ValidateContext(nodes, ports, edges, groups);
        }
    }
}
