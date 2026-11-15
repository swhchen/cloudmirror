package com.cloudmirror.service.analysis;

import com.cloudmirror.service.propagation.Fault;
import com.cloudmirror.service.rule.ValidationModel;
import com.cloudmirror.service.rule.ValidationModel.Group;
import com.cloudmirror.service.rule.ValidationModel.Member;
import com.cloudmirror.service.rule.ValidationModel.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 级联失效分析测试 */
class CascadeAnalyzerTest {

    private final CascadeAnalyzer analyzer = new CascadeAnalyzer();

    @Test
    @DisplayName("备用与主同规格 → 接管后过载，被拖垮")
    void sameSizeStandbyIsOverloaded() {
        List<Node> nodes = List.of(
                node(1, "MySQL 主", ValidationModel.MIDDLEWARE, 1),
                node(2, "MySQL 备", ValidationModel.MIDDLEWARE, 1));
        List<Group> groups = List.of(group(1, 1, 2, 1));

        CascadeAnalyzer.CascadeResult r = analyzer.analyze(nodes, groups,
                List.of(new Fault(7, 1, "MySQL 主", "node_down", "critical")));

        assertEquals(1, r.events().size());
        assertEquals(2L, r.events().get(0).nodeId());
        assertTrue(r.events().get(0).reason().contains("过载"), r.events().get(0).reason());
        assertTrue(r.failedNodeIds().contains(2L));
        assertTrue(r.summary().contains("级联过载"));
    }

    @Test
    @DisplayName("备用是主的 2 倍容量 → 扛得住，不级联")
    void biggerStandbySurvives() {
        List<Node> nodes = List.of(
                node(1, "MySQL 主", ValidationModel.MIDDLEWARE, 1),
                node(2, "MySQL 备", ValidationModel.MIDDLEWARE, 2));
        List<Group> groups = List.of(group(1, 1, 2, 1));

        CascadeAnalyzer.CascadeResult r = analyzer.analyze(nodes, groups,
                List.of(new Fault(7, 1, "MySQL 主", "node_down", "critical")));

        assertTrue(r.events().isEmpty());
        assertFalse(r.failedNodeIds().contains(2L));
        assertTrue(r.summary().contains("余量足够"));
    }

    @Test
    @DisplayName("没有冗余组 → 不存在接管，也就没有级联")
    void noGroupNoCascade() {
        List<Node> nodes = List.of(node(1, "MySQL", ValidationModel.MIDDLEWARE, 1));
        CascadeAnalyzer.CascadeResult r = analyzer.analyze(nodes, List.of(),
                List.of(new Fault(7, 1, "MySQL", "node_down", "critical")));
        assertTrue(r.events().isEmpty());
    }

    @Test
    @DisplayName("三级备胎：第一个扛不住挂掉，第二个接着接管")
    void cascadeThroughTwoStandbys() {
        List<Node> nodes = List.of(
                node(1, "主", ValidationModel.MIDDLEWARE, 1),
                node(2, "备1", ValidationModel.MIDDLEWARE, 1),
                node(3, "备2", ValidationModel.MIDDLEWARE, 1));
        List<Group> groups = List.of(new Group(1L, "主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(1L, ValidationModel.PRIMARY, null),
                        new Member(2L, ValidationModel.STANDBY, 1),
                        new Member(3L, ValidationModel.STANDBY, 2))));

        CascadeAnalyzer.CascadeResult r = analyzer.analyze(nodes, groups,
                List.of(new Fault(7, 1, "主", "node_down", "critical")));

        assertTrue(r.events().size() >= 1);
        assertTrue(r.failedNodeIds().contains(2L), "第一个备用应被拖垮");
    }

    private Node node(long id, String name, String kind, double request) {
        return new Node(id, name, kind, 0, request, 0, (int) (request * 1024), 0, "healthy");
    }

    private Group group(long id, long primary, long standby, int priority) {
        return new Group(id, "主备", ValidationModel.ACTIVE_STANDBY,
                List.of(new Member(primary, ValidationModel.PRIMARY, null),
                        new Member(standby, ValidationModel.STANDBY, priority)));
    }
}
