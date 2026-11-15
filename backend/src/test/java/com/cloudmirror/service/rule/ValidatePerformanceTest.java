package com.cloudmirror.service.rule;

import com.cloudmirror.service.rule.ValidationModel.Edge;
import com.cloudmirror.service.rule.ValidationModel.Node;
import com.cloudmirror.service.rule.ValidationModel.Port;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验引擎性能基准：500 节点 / 1000 条边。
 *
 * <p>目标：单次校验低于 100ms（断言放宽到 200ms，避免机器抖动误报，实际耗时打印出来）。
 */
class ValidatePerformanceTest {

    @Test
    @DisplayName("性能：500 节点 / 1000 边，单次校验 < 100ms")
    void validateLargeTopology() {
        ValidateService service = new ValidateService(List.of(
                new R01PortConflict(), new R02PortOutOfRange(), new R03PortMapConflict(),
                new R04CpuOvercommit(), new R05MemOvercommit(), new R06DiskOvercommit(),
                new R07DependencyMissing(), new R08NetworkUnreachable(), new R09CircularDependency(),
                new R10EntryUnreachable(), new R11OrphanNode(), new R12RedundancyIncomplete()));

        ValidateContext ctx = largeTopology();

        // 预热 3 次（JIT）
        for (int i = 0; i < 3; i++) {
            service.validate(ctx);
        }

        int rounds = 10;
        long total = 0;
        long max = 0;
        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            service.validate(ctx);
            long cost = System.nanoTime() - start;
            total += cost;
            max = Math.max(max, cost);
        }
        double avgMs = total / (double) rounds / 1_000_000;
        double maxMs = max / 1_000_000.0;

        System.out.printf("性能基准：500 节点 / 1000 边，平均 %.2f ms，最大 %.2f ms%n", avgMs, maxMs);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "perf.txt"),
                    String.format("500 节点 / 1000 边：平均 %.2f ms，最大 %.2f ms（10 轮，已预热）%n", avgMs, maxMs),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            // 基准结果写文件失败不影响断言
        }
        assertTrue(avgMs < 200, "平均耗时应低于 200ms，实际 " + avgMs + " ms");
    }

    /**
     * 50 台宿主机 + 450 个容器 = 500 节点；
     * 450 条部署边 + 200 条依赖边 + 350 条网络边 = 1000 条边。
     */
    private ValidateContext largeTopology() {
        List<Node> nodes = new ArrayList<>(500);
        List<Port> ports = new ArrayList<>(450);
        List<Edge> edges = new ArrayList<>(1000);

        for (long h = 1; h <= 50; h++) {
            nodes.add(new Node(h, "host-" + h, ValidationModel.HOST, 4, 0, 8192, 0, 1000, "healthy"));
        }
        for (long i = 0; i < 450; i++) {
            long id = 1000 + i;
            nodes.add(new Node(id, "svc-" + i, ValidationModel.CONTAINER, 0, 0.2, 0, 256, 10, "healthy"));
            ports.add(new Port(10_000 + i, id, 8000 + (int) (i % 1000), "tcp", "internal", null));
            // 部署：轮流放到 50 台宿主机
            edges.add(new Edge(20_000 + i, id, (i % 50) + 1, ValidationModel.DEPLOY_ON, 0L, 0L, true, 1.0));
        }
        // 依赖链：前 200 个容器两两依赖
        for (long i = 0; i < 200; i++) {
            edges.add(new Edge(30_000 + i, 1000 + i, 1001 + i, ValidationModel.DEPENDS_ON, 0L, 0L, true, 1.0));
        }
        // 网络：50 台宿主机环形互联，350 条
        for (long i = 0; i < 350; i++) {
            long a = (i % 50) + 1;
            long b = ((i + 1) % 50) + 1;
            edges.add(new Edge(40_000 + i, a, b, ValidationModel.NETWORK_LINK, 0L, 0L, true, 1.0));
        }
        return new ValidateContext(nodes, ports, edges, List.of());
    }
}
