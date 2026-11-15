package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.EdgeDtos;
import com.cloudmirror.entity.AssemblyEdge;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.NodePort;
import com.cloudmirror.mapper.AssemblyEdgeMapper;
import com.cloudmirror.mapper.NodePortMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 连线：五类边，端口端点可选；唯一约束按（源节点, 目标节点, 边类型, 源端口, 目标端口） */
@Service
public class EdgeService {

    public static final Set<String> KINDS = Set.of(
            "deploy_on", "network_link", "port_map", "depends_on", "replicate");

    /** 这些边是「接口级」的，必须指定两端端口 */
    public static final Set<String> KINDS_REQUIRE_PORTS = Set.of(
            "port_map", "depends_on", "replicate");

    private final AssemblyEdgeMapper edgeMapper;
    private final NodePortMapper portMapper;
    private final NodeService nodeService;
    private final ProjectService projectService;

    public EdgeService(AssemblyEdgeMapper edgeMapper,
                       NodePortMapper portMapper,
                       NodeService nodeService,
                       ProjectService projectService) {
        this.edgeMapper = edgeMapper;
        this.portMapper = portMapper;
        this.nodeService = nodeService;
        this.projectService = projectService;
    }

    public List<EdgeDtos.VO> list(Long projectId) {
        projectService.require(projectId);
        return edgeMapper.selectList(Wrappers.<AssemblyEdge>lambdaQuery()
                        .eq(AssemblyEdge::getProjectId, projectId)
                        .orderByAsc(AssemblyEdge::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public EdgeDtos.VO create(Long projectId, EdgeDtos.SaveReq req) {
        projectService.require(projectId);
        if (!KINDS.contains(req.edgeKind())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的连线类型：" + req.edgeKind());
        }
        if (req.sourceNodeId().equals(req.targetNodeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许自依赖：源节点与目标节点相同");
        }
        AssemblyNode source = nodeService.require(projectId, req.sourceNodeId());
        AssemblyNode target = nodeService.require(projectId, req.targetNodeId());

        long sourcePortId = req.sourcePortId() == null ? 0L : req.sourcePortId();
        long targetPortId = req.targetPortId() == null ? 0L : req.targetPortId();
        if (KINDS_REQUIRE_PORTS.contains(req.edgeKind())
                && !isPortless(source) && !isPortless(target)
                && (sourcePortId <= 0 || targetPortId <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "「" + req.edgeKind() + "」是接口级连线，必须指定源端口和目标端口");
        }
        checkPort(sourcePortId, source.getId(), "源端口");
        checkPort(targetPortId, target.getId(), "目标端口");

        boolean duplicated = edgeMapper.selectCount(Wrappers.<AssemblyEdge>lambdaQuery()
                .eq(AssemblyEdge::getProjectId, projectId)
                .eq(AssemblyEdge::getSourceNodeId, source.getId())
                .eq(AssemblyEdge::getTargetNodeId, target.getId())
                .eq(AssemblyEdge::getEdgeKind, req.edgeKind())
                .eq(AssemblyEdge::getSourcePortId, sourcePortId)
                .eq(AssemblyEdge::getTargetPortId, targetPortId)) > 0;
        if (duplicated) {
            throw new BusinessException(ErrorCode.CONFLICT, "相同的连线已存在");
        }

        AssemblyEdge edge = new AssemblyEdge();
        edge.setProjectId(projectId);
        edge.setSourceNodeId(source.getId());
        edge.setTargetNodeId(target.getId());
        edge.setEdgeKind(req.edgeKind());
        edge.setSourcePortId(sourcePortId);
        edge.setTargetPortId(targetPortId);
        edge.setBandwidthMbps(req.bandwidthMbps());
        edge.setLatencyMs(req.latencyMs());
        edge.setStrong(req.strong() == null || req.strong());
        edge.setWeight(req.weight() == null ? BigDecimal.ONE : req.weight());
        edge.setDescription(req.description());
        edge.setCreatedAt(LocalDateTime.now());
        edgeMapper.insert(edge);
        return toVO(edge);
    }

    @Transactional
    public void delete(Long projectId, Long edgeId) {
        projectService.require(projectId);
        AssemblyEdge edge = edgeMapper.selectById(edgeId);
        if (edge == null || !edge.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "连线不存在或不属于该工程：" + edgeId);
        }
        edgeMapper.deleteById(edgeId);
    }

    /** 外部系统 / 对象存储不建模端口 */
    private boolean isPortless(AssemblyNode node) {
        return "external".equals(node.getNodeKind()) || "object_storage".equals(node.getNodeKind());
    }

    private void checkPort(long portId, long nodeId, String label) {
        if (portId <= 0) {
            return;
        }
        NodePort port = portMapper.selectById(portId);
        if (port == null || port.getNodeId() != nodeId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不存在或不属于该节点：" + portId);
        }
    }

    private EdgeDtos.VO toVO(AssemblyEdge e) {
        return new EdgeDtos.VO(e.getId(), e.getEdgeKind(), e.getSourceNodeId(), e.getTargetNodeId(),
                e.getSourcePortId(), e.getTargetPortId(), e.getBandwidthMbps(), e.getLatencyMs(),
                e.getStrong(), e.getWeight(), e.getDescription());
    }
}
