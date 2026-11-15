package com.cloudmirror.api;

import com.cloudmirror.dto.NodeDtos;
import com.cloudmirror.dto.NodeDtos.PortReq;
import com.cloudmirror.dto.ProjectDtos;
import com.cloudmirror.dto.RedundancyDtos;
import com.cloudmirror.mapper.AnalysisAffectedNodeMapper;
import com.cloudmirror.mapper.AnalysisFaultSourceMapper;
import com.cloudmirror.mapper.AnalysisPathStepMapper;
import com.cloudmirror.mapper.CheckItemMapper;
import com.cloudmirror.mapper.CheckRunMapper;
import com.cloudmirror.mapper.FaultEventMapper;
import com.cloudmirror.mapper.PropagationAnalysisMapper;
import com.cloudmirror.mapper.RecoveryPlanMapper;
import com.cloudmirror.mapper.RecoveryStrategyMapper;
import com.cloudmirror.mapper.RedundancyGroupMapper;
import com.cloudmirror.mapper.RedundancyMemberMapper;
import com.cloudmirror.entity.AnalysisAffectedNode;
import com.cloudmirror.entity.AnalysisPathStep;
import com.cloudmirror.entity.AnalysisFaultSource;
import com.cloudmirror.entity.CheckItemRecord;
import com.cloudmirror.entity.CheckRun;
import com.cloudmirror.entity.FaultEvent;
import com.cloudmirror.entity.PropagationAnalysis;
import com.cloudmirror.entity.RecoveryPlan;
import com.cloudmirror.entity.RecoveryStrategy;
import com.cloudmirror.entity.RedundancyGroup;
import com.cloudmirror.entity.RedundancyMember;
import com.cloudmirror.service.AnalysisRunner;
import com.cloudmirror.service.FaultService;
import com.cloudmirror.service.NodeService;
import com.cloudmirror.service.ProjectService;
import com.cloudmirror.service.RecoveryRunner;
import com.cloudmirror.service.RedundancyService;
import com.cloudmirror.service.ValidationRunner;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据一致性测试：删除工程/节点后，推演期数据不能残留。
 *
 * <p>这些是"接口看不到、但会在数据库里堆垃圾"的问题，所以直接查 Mapper。
 */
@SpringBootTest
@ActiveProfiles("test")
class DataIntegrityTest {

    @Autowired private ProjectService projectService;
    @Autowired private NodeService nodeService;
    @Autowired private RedundancyService redundancyService;
    @Autowired private FaultService faultService;
    @Autowired private ValidationRunner validationRunner;
    @Autowired private AnalysisRunner analysisRunner;
    @Autowired private RecoveryRunner recoveryRunner;

    @Autowired private FaultEventMapper faultMapper;
    @Autowired private PropagationAnalysisMapper analysisMapper;
    @Autowired private AnalysisFaultSourceMapper sourceMapper;
    @Autowired private AnalysisAffectedNodeMapper affectedMapper;
    @Autowired private AnalysisPathStepMapper pathMapper;
    @Autowired private RecoveryPlanMapper planMapper;
    @Autowired private RecoveryStrategyMapper strategyMapper;
    @Autowired private CheckRunMapper checkRunMapper;
    @Autowired private CheckItemMapper checkItemMapper;
    @Autowired private RedundancyGroupMapper groupMapper;
    @Autowired private RedundancyMemberMapper memberMapper;

    @Test
    @DisplayName("删除工程：故障 / 分析 / 恢复 / 校验 / 冗余组全部清空")
    void deleteProjectCascadesAll() {
        long pid = projectService.create(new ProjectDtos.CreateReq("级联删除-" + UUID.randomUUID(), "")).id();
        long primary = createNode(pid, "MySQL 主", 3306);
        long standby = createNode(pid, "MySQL 备", 3306);
        redundancyService.create(pid, new RedundancyDtos.SaveReq("主备", "active_standby", "",
                List.of(new RedundancyDtos.MemberReq(primary, "primary", null),
                        new RedundancyDtos.MemberReq(standby, "standby", 1))));
        faultService.inject(pid, new com.cloudmirror.dto.FaultDtos.InjectReq(primary, "node_down", "critical", ""));
        validationRunner.validate(pid);
        var analysis = analysisRunner.analyze(pid);
        recoveryRunner.recommend(pid, analysis.analysisId());

        // 记下要检查的 ID
        List<Long> analysisIds = analysisMapper.selectList(Wrappers.<PropagationAnalysis>lambdaQuery()
                        .eq(PropagationAnalysis::getProjectId, pid))
                .stream().map(PropagationAnalysis::getId).toList();
        List<Long> affectedIds = affectedMapper.selectList(Wrappers.<AnalysisAffectedNode>lambdaQuery()
                        .in(AnalysisAffectedNode::getAnalysisId, analysisIds))
                .stream().map(AnalysisAffectedNode::getId).toList();
        List<Long> planIds = planMapper.selectList(Wrappers.<RecoveryPlan>lambdaQuery()
                        .eq(RecoveryPlan::getProjectId, pid))
                .stream().map(RecoveryPlan::getId).toList();
        List<Long> runIds = checkRunMapper.selectList(Wrappers.<CheckRun>lambdaQuery()
                        .eq(CheckRun::getProjectId, pid))
                .stream().map(CheckRun::getId).toList();
        List<Long> groupIds = groupMapper.selectList(Wrappers.<RedundancyGroup>lambdaQuery()
                        .eq(RedundancyGroup::getProjectId, pid))
                .stream().map(RedundancyGroup::getId).toList();

        assertTrue(!analysisIds.isEmpty() && !planIds.isEmpty() && !runIds.isEmpty() && !groupIds.isEmpty());

        projectService.delete(pid);

        assertEquals(0, faultMapper.selectCount(Wrappers.<FaultEvent>lambdaQuery()
                .eq(FaultEvent::getProjectId, pid)));
        assertEquals(0, analysisMapper.selectCount(Wrappers.<PropagationAnalysis>lambdaQuery()
                .eq(PropagationAnalysis::getProjectId, pid)));
        assertEquals(0, sourceMapper.selectCount(Wrappers.<AnalysisFaultSource>lambdaQuery()
                .in(AnalysisFaultSource::getAnalysisId, analysisIds)));
        assertEquals(0, affectedMapper.selectCount(Wrappers.<AnalysisAffectedNode>lambdaQuery()
                .in(AnalysisAffectedNode::getAnalysisId, analysisIds)));
        if (!affectedIds.isEmpty()) {
            assertEquals(0, pathMapper.selectCount(Wrappers.<AnalysisPathStep>lambdaQuery()
                    .in(AnalysisPathStep::getAffectedNodeId, affectedIds)));
        }
        assertEquals(0, planMapper.selectCount(Wrappers.<RecoveryPlan>lambdaQuery()
                .eq(RecoveryPlan::getProjectId, pid)));
        assertEquals(0, strategyMapper.selectCount(Wrappers.<RecoveryStrategy>lambdaQuery()
                .in(RecoveryStrategy::getPlanId, planIds)));
        assertEquals(0, checkRunMapper.selectCount(Wrappers.<CheckRun>lambdaQuery()
                .eq(CheckRun::getProjectId, pid)));
        assertEquals(0, checkItemMapper.selectCount(Wrappers.<CheckItemRecord>lambdaQuery()
                .in(CheckItemRecord::getRunId, runIds)));
        assertEquals(0, groupMapper.selectCount(Wrappers.<RedundancyGroup>lambdaQuery()
                .eq(RedundancyGroup::getProjectId, pid)));
        assertEquals(0, memberMapper.selectCount(Wrappers.<RedundancyMember>lambdaQuery()
                .in(RedundancyMember::getGroupId, groupIds)));
    }

    @Test
    @DisplayName("删除冗余组里的主节点：成员被清掉，组因失效被删除")
    void deletePrimaryCleansRedundancyGroup() {
        long pid = projectService.create(new ProjectDtos.CreateReq("节点删除-" + UUID.randomUUID(), "")).id();
        long primary = createNode(pid, "MySQL 主", 3306);
        long standby = createNode(pid, "MySQL 备", 3306);
        redundancyService.create(pid, new RedundancyDtos.SaveReq("主备", "active_standby", "",
                List.of(new RedundancyDtos.MemberReq(primary, "primary", null),
                        new RedundancyDtos.MemberReq(standby, "standby", 1))));

        nodeService.delete(pid, primary);

        assertTrue(redundancyService.list(pid).isEmpty(), "主节点被删后冗余组应整体删除");
        assertEquals(0, memberMapper.selectCount(Wrappers.<RedundancyMember>lambdaQuery()
                .eq(RedundancyMember::getNodeId, primary)));
        // 校验不应再报"引用了不存在的节点"
        var result = validationRunner.validate(pid).result();
        assertTrue(result.items().stream().noneMatch(i -> i.message().contains("不存在的节点")),
                "不应再有悬空成员： " + result.items());
    }

    private long createNode(long projectId, String name, int port) {
        return nodeService.create(projectId, new NodeDtos.SaveReq(
                name, "middleware", null,
                null, java.math.BigDecimal.ONE, null, 2048,
                java.math.BigDecimal.ZERO, 1,
                null, null, "healthy", 0d, 0d, null,
                List.of(new PortReq(port, "tcp", "internal", null, null)))).id();
    }
}
