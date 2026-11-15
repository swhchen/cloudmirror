package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.FaultDtos;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.FaultEvent;
import com.cloudmirror.mapper.AssemblyNodeMapper;
import com.cloudmirror.mapper.FaultEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 故障注入 / 查询 / 恢复 */
@Service
public class FaultService {

    public static final Set<String> TYPES = Set.of(
            "node_down", "service_unavailable", "network_latency",
            "network_unreachable", "port_blocked", "cpu_saturation");
    public static final Set<String> SEVERITIES = Set.of("low", "medium", "high", "critical");

    private final FaultEventMapper faultMapper;
    private final AssemblyNodeMapper nodeMapper;
    private final ProjectService projectService;

    public FaultService(FaultEventMapper faultMapper,
                        AssemblyNodeMapper nodeMapper,
                        ProjectService projectService) {
        this.faultMapper = faultMapper;
        this.nodeMapper = nodeMapper;
        this.projectService = projectService;
    }

    @Transactional
    public FaultDtos.VO inject(Long projectId, FaultDtos.InjectReq req) {
        projectService.require(projectId);
        AssemblyNode node = nodeMapper.selectById(req.nodeId());
        if (node == null || !node.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点不存在或不属于该工程：" + req.nodeId());
        }
        if (!TYPES.contains(req.faultType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的故障类型：" + req.faultType());
        }
        if (!SEVERITIES.contains(req.severity())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的严重程度：" + req.severity());
        }
        boolean duplicated = faultMapper.selectCount(Wrappers.<FaultEvent>lambdaQuery()
                .eq(FaultEvent::getProjectId, projectId)
                .eq(FaultEvent::getSourceNodeId, req.nodeId())
                .eq(FaultEvent::getFaultType, req.faultType())
                .eq(FaultEvent::getStatus, "active")) > 0;
        if (duplicated) {
            throw new BusinessException(ErrorCode.CONFLICT, "该节点已存在同类型的激活故障");
        }

        FaultEvent event = new FaultEvent();
        event.setProjectId(projectId);
        event.setSourceNodeId(req.nodeId());
        event.setFaultType(req.faultType());
        event.setSeverity(req.severity());
        event.setDescription(req.description());
        event.setStatus("active");
        event.setCreatedAt(LocalDateTime.now());
        faultMapper.insert(event);
        return toVO(event, node.getName());
    }

    public List<FaultDtos.VO> list(Long projectId) {
        projectService.require(projectId);
        return faultMapper.selectList(Wrappers.<FaultEvent>lambdaQuery()
                        .eq(FaultEvent::getProjectId, projectId)
                        .orderByDesc(FaultEvent::getId))
                .stream().map(f -> toVO(f, nodeName(f.getSourceNodeId()))).toList();
    }

    @Transactional
    public FaultDtos.VO recover(Long projectId, Long faultId) {
        projectService.require(projectId);
        FaultEvent event = require(projectId, faultId);
        if ("recovered".equals(event.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该故障已恢复，无需重复操作");
        }
        event.setStatus("recovered");
        event.setRecoveredAt(LocalDateTime.now());
        faultMapper.updateById(event);
        return toVO(event, nodeName(event.getSourceNodeId()));
    }

    /** 当前激活的故障（传播算法输入） */
    public List<com.cloudmirror.service.propagation.Fault> activeFaults(Long projectId) {
        return faultMapper.selectList(Wrappers.<FaultEvent>lambdaQuery()
                        .eq(FaultEvent::getProjectId, projectId)
                        .eq(FaultEvent::getStatus, "active")
                        .orderByAsc(FaultEvent::getId))
                .stream()
                .map(f -> new com.cloudmirror.service.propagation.Fault(f.getId(), f.getSourceNodeId(),
                        nodeName(f.getSourceNodeId()), f.getFaultType(), f.getSeverity()))
                .toList();
    }

    private FaultEvent require(Long projectId, Long faultId) {
        FaultEvent event = faultMapper.selectById(faultId);
        if (event == null || !event.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "故障事件不存在或不属于该工程：" + faultId);
        }
        return event;
    }

    private String nodeName(Long nodeId) {
        AssemblyNode node = nodeId == null ? null : nodeMapper.selectById(nodeId);
        return node == null ? ("节点#" + nodeId) : node.getName();
    }

    private FaultDtos.VO toVO(FaultEvent e, String nodeName) {
        return new FaultDtos.VO(e.getId(), e.getSourceNodeId(), nodeName, e.getFaultType(),
                e.getSeverity(), e.getDescription(), e.getStatus(), e.getCreatedAt(), e.getRecoveredAt());
    }
}
