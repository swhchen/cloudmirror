package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.BusinessException;
import com.cloudmirror.common.ErrorCode;
import com.cloudmirror.dto.RedundancyDtos;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.RedundancyGroup;
import com.cloudmirror.entity.RedundancyMember;
import com.cloudmirror.mapper.AssemblyNodeMapper;
import com.cloudmirror.mapper.RedundancyGroupMapper;
import com.cloudmirror.mapper.RedundancyMemberMapper;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 显式冗余组：主备 / 双活 / 手动备用 */
@Service
public class RedundancyService {

    public static final Set<String> TYPES = Set.of("active_standby", "active_active", "manual_standby");
    public static final Set<String> ROLES = Set.of("primary", "standby", "active");

    private final RedundancyGroupMapper groupMapper;
    private final RedundancyMemberMapper memberMapper;
    private final AssemblyNodeMapper nodeMapper;
    private final ProjectService projectService;

    public RedundancyService(RedundancyGroupMapper groupMapper,
                             RedundancyMemberMapper memberMapper,
                             AssemblyNodeMapper nodeMapper,
                             ProjectService projectService) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.nodeMapper = nodeMapper;
        this.projectService = projectService;
    }

    public List<RedundancyDtos.VO> list(Long projectId) {
        projectService.require(projectId);
        List<RedundancyGroup> groups = groupMapper.selectList(Wrappers.<RedundancyGroup>lambdaQuery()
                .eq(RedundancyGroup::getProjectId, projectId)
                .orderByAsc(RedundancyGroup::getId));
        return groups.stream().map(this::toVO).toList();
    }

    @Transactional
    public RedundancyDtos.VO create(Long projectId, RedundancyDtos.SaveReq req) {
        projectService.require(projectId);
        validate(projectId, req, null);

        RedundancyGroup group = new RedundancyGroup();
        group.setProjectId(projectId);
        group.setName(req.name());
        group.setRedundancyType(req.redundancyType());
        group.setDescription(req.description());
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        insertMembers(group.getId(), req.members());
        return toVO(group);
    }

    @Transactional
    public RedundancyDtos.VO update(Long projectId, Long groupId, RedundancyDtos.SaveReq req) {
        projectService.require(projectId);
        RedundancyGroup group = require(projectId, groupId);
        validate(projectId, req, groupId);

        group.setName(req.name());
        group.setRedundancyType(req.redundancyType());
        group.setDescription(req.description());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);

        memberMapper.delete(Wrappers.<RedundancyMember>lambdaQuery()
                .eq(RedundancyMember::getGroupId, groupId));
        insertMembers(groupId, req.members());
        return toVO(group);
    }

    @Transactional
    public void delete(Long projectId, Long groupId) {
        projectService.require(projectId);
        require(projectId, groupId);
        memberMapper.delete(Wrappers.<RedundancyMember>lambdaQuery()
                .eq(RedundancyMember::getGroupId, groupId));
        groupMapper.deleteById(groupId);
    }

    /** 给校验引擎用的模型（R12 主备完整性） */
    public List<ValidationModel.Group> groupsOf(Long projectId) {
        List<RedundancyGroup> groups = groupMapper.selectList(Wrappers.<RedundancyGroup>lambdaQuery()
                .eq(RedundancyGroup::getProjectId, projectId)
                .orderByAsc(RedundancyGroup::getId));
        List<ValidationModel.Group> result = new ArrayList<>();
        for (RedundancyGroup g : groups) {
            List<RedundancyMember> members = memberMapper.selectList(
                    Wrappers.<RedundancyMember>lambdaQuery().eq(RedundancyMember::getGroupId, g.getId()));
            List<ValidationModel.Member> modelMembers = members.stream()
                    .map(m -> new ValidationModel.Member(m.getNodeId(), m.getMemberRole(), m.getPriority()))
                    .toList();
            result.add(new ValidationModel.Group(g.getId(), g.getName(), g.getRedundancyType(), modelMembers));
        }
        return result;
    }

    // ---------- 内部 ----------

    private void validate(Long projectId, RedundancyDtos.SaveReq req, Long excludeGroupId) {
        if (!TYPES.contains(req.redundancyType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的冗余模式：" + req.redundancyType());
        }
        List<RedundancyDtos.MemberReq> members = req.members();
        Set<Long> seen = new java.util.HashSet<>();
        for (RedundancyDtos.MemberReq m : members) {
            if (m.nodeId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "成员节点不能为空");
            }
            if (!ROLES.contains(m.role())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的成员角色：" + m.role());
            }
            if (!seen.add(m.nodeId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "同一节点不能重复加入冗余组");
            }
            AssemblyNode node = nodeMapper.selectById(m.nodeId());
            if (node == null || !node.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "成员节点不存在或不属于该工程：" + m.nodeId());
            }
        }

        if ("active_active".equals(req.redundancyType())) {
            long actives = members.stream().filter(m -> "active".equals(m.role())).count();
            if (actives < 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "双活模式至少需要两个 active 成员");
            }
        } else {
            long primaries = members.stream().filter(m -> "primary".equals(m.role())).count();
            long standbys = members.stream().filter(m -> "standby".equals(m.role())).count();
            if (primaries != 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "主备模式必须恰好一个 primary 成员");
            }
            if (standbys < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "主备模式至少需要一个 standby 成员");
            }
            boolean missingPriority = members.stream()
                    .filter(m -> "standby".equals(m.role()))
                    .anyMatch(m -> m.priority() == null);
            if (missingPriority) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "每个 standby 成员都必须指定 priority");
            }
        }

        // 一个节点在同一工程内只能属于一个冗余组
        List<Long> groupIds = groupMapper.selectList(Wrappers.<RedundancyGroup>lambdaQuery()
                        .eq(RedundancyGroup::getProjectId, projectId))
                .stream().map(RedundancyGroup::getId).toList();
        if (!groupIds.isEmpty()) {
            var wrapper = Wrappers.<RedundancyMember>lambdaQuery()
                    .in(RedundancyMember::getGroupId, groupIds)
                    .in(RedundancyMember::getNodeId, seen);
            if (excludeGroupId != null) {
                wrapper.ne(RedundancyMember::getGroupId, excludeGroupId);
            }
            if (memberMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "节点已属于其它冗余组");
            }
        }
    }

    private void insertMembers(Long groupId, List<RedundancyDtos.MemberReq> members) {
        for (RedundancyDtos.MemberReq m : members) {
            RedundancyMember entity = new RedundancyMember();
            entity.setGroupId(groupId);
            entity.setNodeId(m.nodeId());
            entity.setMemberRole(m.role());
            entity.setPriority(m.priority());
            memberMapper.insert(entity);
        }
    }

    private RedundancyGroup require(Long projectId, Long groupId) {
        RedundancyGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "冗余组不存在或不属于该工程：" + groupId);
        }
        return group;
    }

    private RedundancyDtos.VO toVO(RedundancyGroup group) {
        List<RedundancyMember> members = memberMapper.selectList(Wrappers.<RedundancyMember>lambdaQuery()
                .eq(RedundancyMember::getGroupId, group.getId())
                .orderByAsc(RedundancyMember::getId));
        Map<Long, String> nodeNames = new HashMap<>();
        List<RedundancyDtos.MemberVO> memberVOs = new ArrayList<>();
        for (RedundancyMember m : members) {
            String name = nodeNames.computeIfAbsent(m.getNodeId(), id -> {
                AssemblyNode node = nodeMapper.selectById(id);
                return node == null ? ("节点#" + id) : node.getName();
            });
            memberVOs.add(new RedundancyDtos.MemberVO(m.getId(), m.getNodeId(), name,
                    m.getMemberRole(), m.getPriority()));
        }
        return new RedundancyDtos.VO(group.getId(), group.getName(), group.getRedundancyType(),
                group.getDescription(), memberVOs);
    }
}
