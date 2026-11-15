package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.CurrentUser;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.ProjectDtos;
import com.cloudmirror.entity.AnalysisAffectedNode;
import com.cloudmirror.entity.AnalysisFaultSource;
import com.cloudmirror.entity.AnalysisPathStep;
import com.cloudmirror.entity.AssemblyEdge;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.CheckItemRecord;
import com.cloudmirror.entity.CheckRun;
import com.cloudmirror.entity.FaultEvent;
import com.cloudmirror.entity.NodePort;
import com.cloudmirror.entity.Project;
import com.cloudmirror.entity.PropagationAnalysis;
import com.cloudmirror.entity.RecoveryPlan;
import com.cloudmirror.entity.RecoveryStrategy;
import com.cloudmirror.entity.RedundancyGroup;
import com.cloudmirror.entity.RedundancyMember;
import com.cloudmirror.mapper.AnalysisAffectedNodeMapper;
import com.cloudmirror.mapper.AnalysisFaultSourceMapper;
import com.cloudmirror.mapper.AnalysisPathStepMapper;
import com.cloudmirror.mapper.AssemblyEdgeMapper;
import com.cloudmirror.mapper.AssemblyNodeMapper;
import com.cloudmirror.mapper.CheckItemMapper;
import com.cloudmirror.mapper.CheckRunMapper;
import com.cloudmirror.mapper.FaultEventMapper;
import com.cloudmirror.mapper.NodePortMapper;
import com.cloudmirror.mapper.ProjectMapper;
import com.cloudmirror.mapper.PropagationAnalysisMapper;
import com.cloudmirror.mapper.RecoveryPlanMapper;
import com.cloudmirror.mapper.RecoveryStrategyMapper;
import com.cloudmirror.mapper.RedundancyGroupMapper;
import com.cloudmirror.mapper.RedundancyMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 推演工程：多工程并行，数据按 ownerId 隔离 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final AssemblyNodeMapper nodeMapper;
    private final AssemblyEdgeMapper edgeMapper;
    private final NodePortMapper portMapper;
    private final FaultEventMapper faultEventMapper;
    private final PropagationAnalysisMapper analysisMapper;
    private final AnalysisFaultSourceMapper faultSourceMapper;
    private final AnalysisAffectedNodeMapper affectedNodeMapper;
    private final AnalysisPathStepMapper pathStepMapper;
    private final RecoveryPlanMapper recoveryPlanMapper;
    private final RecoveryStrategyMapper recoveryStrategyMapper;
    private final CheckRunMapper checkRunMapper;
    private final CheckItemMapper checkItemMapper;
    private final RedundancyGroupMapper redundancyGroupMapper;
    private final RedundancyMemberMapper redundancyMemberMapper;

    public ProjectService(ProjectMapper projectMapper,
                          AssemblyNodeMapper nodeMapper,
                          AssemblyEdgeMapper edgeMapper,
                          NodePortMapper portMapper,
                          FaultEventMapper faultEventMapper,
                          PropagationAnalysisMapper analysisMapper,
                          AnalysisFaultSourceMapper faultSourceMapper,
                          AnalysisAffectedNodeMapper affectedNodeMapper,
                          AnalysisPathStepMapper pathStepMapper,
                          RecoveryPlanMapper recoveryPlanMapper,
                          RecoveryStrategyMapper recoveryStrategyMapper,
                          CheckRunMapper checkRunMapper,
                          CheckItemMapper checkItemMapper,
                          RedundancyGroupMapper redundancyGroupMapper,
                          RedundancyMemberMapper redundancyMemberMapper) {
        this.projectMapper = projectMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.portMapper = portMapper;
        this.faultEventMapper = faultEventMapper;
        this.analysisMapper = analysisMapper;
        this.faultSourceMapper = faultSourceMapper;
        this.affectedNodeMapper = affectedNodeMapper;
        this.pathStepMapper = pathStepMapper;
        this.recoveryPlanMapper = recoveryPlanMapper;
        this.recoveryStrategyMapper = recoveryStrategyMapper;
        this.checkRunMapper = checkRunMapper;
        this.checkItemMapper = checkItemMapper;
        this.redundancyGroupMapper = redundancyGroupMapper;
        this.redundancyMemberMapper = redundancyMemberMapper;
    }

    public List<ProjectDtos.VO> list() {
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                        .eq(Project::getOwnerId, CurrentUser.id())
                        .orderByDesc(Project::getUpdatedAt)
                        .orderByDesc(Project::getId))
                .stream().map(this::toVO).toList();
    }

    public ProjectDtos.VO get(Long id) {
        return toVO(require(id));
    }

    @Transactional
    public ProjectDtos.VO create(ProjectDtos.CreateReq req) {
        boolean exists = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getOwnerId, CurrentUser.id())
                .eq(Project::getName, req.name())) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "工程名已存在：" + req.name());
        }
        Project p = new Project();
        p.setOwnerId(CurrentUser.id());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setViewScale(BigDecimal.ONE);
        p.setViewOffsetX(0.0);
        p.setViewOffsetY(0.0);
        p.setGridSnap(true);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        return toVO(p);
    }

    @Transactional
    public ProjectDtos.VO update(Long id, ProjectDtos.UpdateReq req) {
        Project p = require(id);
        if (req.name() != null && !req.name().isBlank() && !req.name().equals(p.getName())) {
            boolean exists = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                    .eq(Project::getOwnerId, CurrentUser.id())
                    .eq(Project::getName, req.name())
                    .ne(Project::getId, id)) > 0;
            if (exists) {
                throw new BusinessException(ErrorCode.CONFLICT, "工程名已存在：" + req.name());
            }
            p.setName(req.name());
        }
        if (req.description() != null) {
            p.setDescription(req.description());
        }
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        return toVO(p);
    }

    @Transactional
    public ProjectDtos.VO updateViewport(Long id, ProjectDtos.ViewportReq req) {
        Project p = require(id);
        if (req.scale() != null) {
            p.setViewScale(BigDecimal.valueOf(req.scale()));
        }
        if (req.offsetX() != null) {
            p.setViewOffsetX(req.offsetX());
        }
        if (req.offsetY() != null) {
            p.setViewOffsetY(req.offsetY());
        }
        if (req.gridSnap() != null) {
            p.setGridSnap(req.gridSnap());
        }
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        return toVO(p);
    }

    /** 删除工程：把装配期 + 推演期的数据全部清干净（init.sql 未建外键，级联在应用层做） */
    @Transactional
    public void delete(Long id) {
        require(id);

        // 1) 节点 & 端口
        List<Long> nodeIds = nodeMapper.selectList(Wrappers.<AssemblyNode>lambdaQuery()
                        .eq(AssemblyNode::getProjectId, id))
                .stream().map(AssemblyNode::getId).toList();
        if (!nodeIds.isEmpty()) {
            portMapper.delete(Wrappers.<NodePort>lambdaQuery().in(NodePort::getNodeId, nodeIds));
        }

        // 2) 连线
        edgeMapper.delete(Wrappers.<AssemblyEdge>lambdaQuery().eq(AssemblyEdge::getProjectId, id));

        // 3) 故障事件
        faultEventMapper.delete(Wrappers.<FaultEvent>lambdaQuery().eq(FaultEvent::getProjectId, id));

        // 4) 传播分析（子表依赖 affected_node，必须按顺序删）
        List<Long> analysisIds = analysisMapper.selectList(Wrappers.<PropagationAnalysis>lambdaQuery()
                        .eq(PropagationAnalysis::getProjectId, id))
                .stream().map(PropagationAnalysis::getId).toList();
        if (!analysisIds.isEmpty()) {
            List<Long> affectedIds = affectedNodeMapper.selectList(Wrappers.<AnalysisAffectedNode>lambdaQuery()
                            .in(AnalysisAffectedNode::getAnalysisId, analysisIds))
                    .stream().map(AnalysisAffectedNode::getId).toList();
            if (!affectedIds.isEmpty()) {
                pathStepMapper.delete(Wrappers.<AnalysisPathStep>lambdaQuery()
                        .in(AnalysisPathStep::getAffectedNodeId, affectedIds));
            }
            affectedNodeMapper.delete(Wrappers.<AnalysisAffectedNode>lambdaQuery()
                    .in(AnalysisAffectedNode::getAnalysisId, analysisIds));
            faultSourceMapper.delete(Wrappers.<AnalysisFaultSource>lambdaQuery()
                    .in(AnalysisFaultSource::getAnalysisId, analysisIds));
            analysisMapper.deleteBatchIds(analysisIds);
        }

        // 5) 恢复方案与策略
        List<Long> planIds = recoveryPlanMapper.selectList(Wrappers.<RecoveryPlan>lambdaQuery()
                        .eq(RecoveryPlan::getProjectId, id))
                .stream().map(RecoveryPlan::getId).toList();
        if (!planIds.isEmpty()) {
            recoveryStrategyMapper.delete(Wrappers.<RecoveryStrategy>lambdaQuery()
                    .in(RecoveryStrategy::getPlanId, planIds));
            recoveryPlanMapper.deleteBatchIds(planIds);
        }

        // 6) 校验记录
        List<Long> runIds = checkRunMapper.selectList(Wrappers.<CheckRun>lambdaQuery()
                        .eq(CheckRun::getProjectId, id))
                .stream().map(CheckRun::getId).toList();
        if (!runIds.isEmpty()) {
            checkItemMapper.delete(Wrappers.<CheckItemRecord>lambdaQuery()
                    .in(CheckItemRecord::getRunId, runIds));
            checkRunMapper.deleteBatchIds(runIds);
        }

        // 7) 冗余组与成员
        List<Long> groupIds = redundancyGroupMapper.selectList(Wrappers.<RedundancyGroup>lambdaQuery()
                        .eq(RedundancyGroup::getProjectId, id))
                .stream().map(RedundancyGroup::getId).toList();
        if (!groupIds.isEmpty()) {
            redundancyMemberMapper.delete(Wrappers.<RedundancyMember>lambdaQuery()
                    .in(RedundancyMember::getGroupId, groupIds));
            redundancyGroupMapper.deleteBatchIds(groupIds);
        }

        // 8) 节点 & 工程
        nodeMapper.delete(Wrappers.<AssemblyNode>lambdaQuery().eq(AssemblyNode::getProjectId, id));
        projectMapper.deleteById(id);
    }

    public Project require(Long id) {
        Project p = id == null ? null : projectMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工程不存在：" + id);
        }
        return p;
    }

    private ProjectDtos.VO toVO(Project p) {
        long nodes = nodeMapper.selectCount(Wrappers.<AssemblyNode>lambdaQuery()
                .eq(AssemblyNode::getProjectId, p.getId()));
        long edges = edgeMapper.selectCount(Wrappers.<AssemblyEdge>lambdaQuery()
                .eq(AssemblyEdge::getProjectId, p.getId()));
        return new ProjectDtos.VO(p.getId(), p.getName(), p.getDescription(),
                p.getViewScale() == null ? 1.0 : p.getViewScale().doubleValue(),
                p.getViewOffsetX(), p.getViewOffsetY(), p.getGridSnap(),
                nodes, edges, p.getCreatedAt(), p.getUpdatedAt());
    }
}
