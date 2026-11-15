package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.NodeDtos;
import com.cloudmirror.entity.AssemblyEdge;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.DeviceModel;
import com.cloudmirror.entity.NodePort;
import com.cloudmirror.entity.RedundancyGroup;
import com.cloudmirror.entity.RedundancyMember;
import com.cloudmirror.mapper.AssemblyEdgeMapper;
import com.cloudmirror.mapper.AssemblyNodeMapper;
import com.cloudmirror.mapper.DeviceModelMapper;
import com.cloudmirror.mapper.NodePortMapper;
import com.cloudmirror.mapper.RedundancyGroupMapper;
import com.cloudmirror.mapper.RedundancyMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 装配节点：型号填默认值、端口随节点一起提交、删除时级联清理 */
@Service
public class NodeService {

    private final AssemblyNodeMapper nodeMapper;
    private final NodePortMapper portMapper;
    private final AssemblyEdgeMapper edgeMapper;
    private final DeviceModelMapper modelMapper;
    private final RedundancyGroupMapper groupMapper;
    private final RedundancyMemberMapper memberMapper;
    private final ProjectService projectService;

    public NodeService(AssemblyNodeMapper nodeMapper,
                       NodePortMapper portMapper,
                       AssemblyEdgeMapper edgeMapper,
                       DeviceModelMapper modelMapper,
                       RedundancyGroupMapper groupMapper,
                       RedundancyMemberMapper memberMapper,
                       ProjectService projectService) {
        this.nodeMapper = nodeMapper;
        this.portMapper = portMapper;
        this.edgeMapper = edgeMapper;
        this.modelMapper = modelMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.projectService = projectService;
    }

    public List<NodeDtos.VO> list(Long projectId) {
        projectService.require(projectId);
        List<AssemblyNode> nodes = nodeMapper.selectList(Wrappers.<AssemblyNode>lambdaQuery()
                .eq(AssemblyNode::getProjectId, projectId)
                .orderByAsc(AssemblyNode::getId));
        return nodes.stream().map(this::toVO).toList();
    }

    @Transactional
    public NodeDtos.VO create(Long projectId, NodeDtos.SaveReq req) {
        projectService.require(projectId);
        requireNameFree(projectId, req.name(), null);

        AssemblyNode node = new AssemblyNode();
        node.setProjectId(projectId);
        node.setName(req.name());
        node.setNodeKind(req.nodeKind());
        node.setModelId(req.modelId());
        node.setIp(req.ip());
        node.setRuntime(req.runtime());
        node.setReplicas(req.replicas() == null ? 1 : req.replicas());
        node.setStatus(req.status() == null ? "healthy" : req.status());
        node.setPosX(req.posX() == null ? 0.0 : req.posX());
        node.setPosY(req.posY() == null ? 0.0 : req.posY());
        node.setDescription(req.description());
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        applyResources(node, req);
        nodeMapper.insert(node);

        savePorts(node.getId(), req.ports());
        return toVO(node);
    }

    @Transactional
    public NodeDtos.VO update(Long projectId, Long nodeId, NodeDtos.SaveReq req) {
        return update(projectId, nodeId, req, false);
    }

    /**
     * @param force 端口被连线引用时是否强制删除（默认 false：保护用户不误删连线）
     */
    @Transactional
    public NodeDtos.VO update(Long projectId, Long nodeId, NodeDtos.SaveReq req, boolean force) {
        projectService.require(projectId);
        AssemblyNode node = require(projectId, nodeId);
        requireNameFree(projectId, req.name(), nodeId);

        node.setName(req.name());
        node.setNodeKind(req.nodeKind());
        node.setModelId(req.modelId());
        node.setIp(req.ip());
        node.setRuntime(req.runtime());
        node.setReplicas(req.replicas() == null ? 1 : req.replicas());
        if (req.status() != null) {
            node.setStatus(req.status());
        }
        if (req.posX() != null) {
            node.setPosX(req.posX());
        }
        if (req.posY() != null) {
            node.setPosY(req.posY());
        }
        node.setDescription(req.description());
        node.setUpdatedAt(LocalDateTime.now());
        applyResources(node, req);
        nodeMapper.updateById(node);

        // 端口合并更新：按 (protocol, port) 匹配旧端口并复用 ID，
        // 只删除真正被移除的端口（连带它们的连线），避免"改个 CPU 就把连线全删了"
        if (req.ports() != null) {
            validatePorts(req.ports());
            List<NodePort> oldPorts = portMapper.selectList(Wrappers.<NodePort>lambdaQuery()
                    .eq(NodePort::getNodeId, nodeId));
            Map<String, NodePort> existingByKey = new LinkedHashMap<>();
            for (NodePort p : oldPorts) {
                existingByKey.putIfAbsent(portKey(p.getPort(), p.getProtocol()), p);
            }

            Set<Long> keepIds = new LinkedHashSet<>();
            List<NodeDtos.PortReq> toInsert = new ArrayList<>();
            for (NodeDtos.PortReq r : req.ports()) {
                NodePort match = existingByKey.get(portKey(r.port(), r.protocol()));
                if (match != null && !keepIds.contains(match.getId())) {
                    match.setScope(r.scope() == null ? "internal" : r.scope());
                    match.setHostPort(r.hostPort());
                    match.setPurpose(r.purpose());
                    portMapper.updateById(match);
                    keepIds.add(match.getId());
                } else {
                    toInsert.add(r);
                }
            }

            List<Long> removedIds = oldPorts.stream()
                    .map(NodePort::getId)
                    .filter(id -> !keepIds.contains(id))
                    .toList();
            if (!removedIds.isEmpty()) {
                long referenced = edgeMapper.selectCount(Wrappers.<AssemblyEdge>lambdaQuery()
                        .eq(AssemblyEdge::getProjectId, projectId)
                        .and(w -> w.in(AssemblyEdge::getSourcePortId, removedIds)
                                .or().in(AssemblyEdge::getTargetPortId, removedIds)));
                if (referenced > 0 && !force) {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "要删除的端口仍被 " + referenced + " 条连线引用；请先删除连线，或传 force=true 强制删除");
                }
                edgeMapper.delete(Wrappers.<AssemblyEdge>lambdaQuery()
                        .eq(AssemblyEdge::getProjectId, projectId)
                        .and(w -> w.in(AssemblyEdge::getSourcePortId, removedIds)
                                .or().in(AssemblyEdge::getTargetPortId, removedIds)));
                portMapper.deleteBatchIds(removedIds);
            }
            for (NodeDtos.PortReq r : toInsert) {
                insertPort(nodeId, r);
            }
        }
        return toVO(node);
    }

    /** 删除节点：端口 → 相关连线 → 冗余组成员（组失效则整组删）→ 节点 */
    @Transactional
    public void delete(Long projectId, Long nodeId) {
        projectService.require(projectId);
        require(projectId, nodeId);

        portMapper.delete(Wrappers.<NodePort>lambdaQuery().eq(NodePort::getNodeId, nodeId));
        edgeMapper.delete(Wrappers.<AssemblyEdge>lambdaQuery()
                .eq(AssemblyEdge::getProjectId, projectId)
                .and(w -> w.eq(AssemblyEdge::getSourceNodeId, nodeId)
                        .or().eq(AssemblyEdge::getTargetNodeId, nodeId)));

        // 清理冗余组：删掉该成员；组结构不再合法（没主/没备/双活不足）就整组删除
        List<RedundancyMember> memberships = memberMapper.selectList(
                Wrappers.<RedundancyMember>lambdaQuery().eq(RedundancyMember::getNodeId, nodeId));
        for (RedundancyMember membership : memberships) {
            memberMapper.delete(Wrappers.<RedundancyMember>lambdaQuery()
                    .eq(RedundancyMember::getGroupId, membership.getGroupId())
                    .eq(RedundancyMember::getNodeId, nodeId));
            RedundancyGroup group = groupMapper.selectById(membership.getGroupId());
            if (group == null) {
                continue;
            }
            List<RedundancyMember> rest = memberMapper.selectList(
                    Wrappers.<RedundancyMember>lambdaQuery().eq(RedundancyMember::getGroupId, group.getId()));
            if (!stillValid(group, rest)) {
                memberMapper.delete(Wrappers.<RedundancyMember>lambdaQuery()
                        .eq(RedundancyMember::getGroupId, group.getId()));
                groupMapper.deleteById(group.getId());
            }
        }

        nodeMapper.deleteById(nodeId);
    }

    /** 组结构是否仍然合法 */
    private boolean stillValid(RedundancyGroup group, List<RedundancyMember> members) {
        if (members.isEmpty()) {
            return false;
        }
        if ("active_active".equals(group.getRedundancyType())) {
            return members.stream().filter(m -> "active".equals(m.getMemberRole())).count() >= 2;
        }
        long primaries = members.stream().filter(m -> "primary".equals(m.getMemberRole())).count();
        long standbys = members.stream().filter(m -> "standby".equals(m.getMemberRole())).count();
        return primaries == 1 && standbys >= 1;
    }

    /** 批量保存坐标（拖动结束时一次提交） */
    @Transactional
    public void savePositions(Long projectId, NodeDtos.PositionsReq req) {
        projectService.require(projectId);
        for (NodeDtos.Position p : req.positions()) {
            AssemblyNode node = require(projectId, p.id());
            if (p.x() != null) {
                node.setPosX(p.x());
            }
            if (p.y() != null) {
                node.setPosY(p.y());
            }
            node.setUpdatedAt(LocalDateTime.now());
            nodeMapper.updateById(node);
        }
    }

    public AssemblyNode require(Long projectId, Long nodeId) {
        AssemblyNode node = nodeMapper.selectById(nodeId);
        if (node == null || !node.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点不存在或不属于该工程：" + nodeId);
        }
        return node;
    }

    // ---------- 内部 ----------

    private void requireNameFree(Long projectId, String name, Long excludeNodeId) {
        var wrapper = Wrappers.<AssemblyNode>lambdaQuery()
                .eq(AssemblyNode::getProjectId, projectId)
                .eq(AssemblyNode::getName, name);
        if (excludeNodeId != null) {
            wrapper.ne(AssemblyNode::getId, excludeNodeId);
        }
        if (nodeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "节点名已存在：" + name);
        }
    }

    /** 型号填默认值，手填字段优先 */
    private void applyResources(AssemblyNode node, NodeDtos.SaveReq req) {
        DeviceModel model = req.modelId() == null ? null : modelMapper.selectById(req.modelId());
        boolean hostLike = "host".equals(req.nodeKind()) || "vm".equals(req.nodeKind())
                || "k8s_cluster".equals(req.nodeKind());
        BigDecimal zero = BigDecimal.ZERO;

        node.setCpuCores(req.cpuCores() != null ? req.cpuCores()
                : (hostLike && model != null ? model.getCpuCores() : zero));
        node.setCpuRequest(req.cpuRequest() != null ? req.cpuRequest()
                : (!hostLike && model != null ? model.getCpuCores() : zero));
        node.setMemTotalMb(req.memTotalMb() != null ? req.memTotalMb()
                : (hostLike && model != null ? model.getMemMb() : 0));
        node.setMemRequestMb(req.memRequestMb() != null ? req.memRequestMb()
                : (!hostLike && model != null ? model.getMemMb() : 0));
        node.setDiskGb(req.diskGb() != null ? req.diskGb()
                : (model != null ? model.getDiskGb() : zero));
    }

    private void savePorts(Long nodeId, List<NodeDtos.PortReq> ports) {
        if (ports == null || ports.isEmpty()) {
            return;
        }
        validatePorts(ports);
        for (NodeDtos.PortReq p : ports) {
            insertPort(nodeId, p);
        }
    }

    /** 端口范围 + 同节点重复校验（不动库） */
    private void validatePorts(List<NodeDtos.PortReq> ports) {
        Set<String> seen = new HashSet<>();
        for (NodeDtos.PortReq p : ports) {
            if (p.port() == null || p.port() < 1 || p.port() > 65535) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "端口必须在 1–65535：" + p.port());
            }
            if (!seen.add(portKey(p.port(), p.protocol()))) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "同一节点端口重复：" + p.port() + "/" + (p.protocol() == null ? "tcp" : p.protocol()));
            }
        }
    }

    private void insertPort(Long nodeId, NodeDtos.PortReq p) {
        NodePort entity = new NodePort();
        entity.setNodeId(nodeId);
        entity.setPort(p.port());
        entity.setProtocol(p.protocol() == null ? "tcp" : p.protocol());
        entity.setScope(p.scope() == null ? "internal" : p.scope());
        entity.setHostPort(p.hostPort());
        entity.setPurpose(p.purpose());
        portMapper.insert(entity);
    }

    private String portKey(Integer port, String protocol) {
        return (protocol == null ? "tcp" : protocol.toLowerCase()) + ":" + port;
    }

    private NodeDtos.VO toVO(AssemblyNode node) {
        List<NodePort> ports = portMapper.selectList(Wrappers.<NodePort>lambdaQuery()
                .eq(NodePort::getNodeId, node.getId())
                .orderByAsc(NodePort::getId));
        String modelName = null;
        if (node.getModelId() != null) {
            DeviceModel model = modelMapper.selectById(node.getModelId());
            modelName = model == null ? null : model.getModelName();
        }
        List<NodeDtos.PortVO> portVOs = new ArrayList<>();
        ports.forEach(p -> portVOs.add(new NodeDtos.PortVO(p.getId(), p.getPort(), p.getProtocol(),
                p.getScope(), p.getHostPort(), p.getPurpose())));
        return new NodeDtos.VO(node.getId(), node.getProjectId(), node.getName(), node.getNodeKind(),
                node.getModelId(), modelName, node.getCpuCores(), node.getCpuRequest(),
                node.getMemTotalMb(), node.getMemRequestMb(), node.getDiskGb(), node.getReplicas(),
                node.getIp(), node.getRuntime(), node.getStatus(), node.getPosX(), node.getPosY(),
                node.getDescription(), portVOs);
    }
}
