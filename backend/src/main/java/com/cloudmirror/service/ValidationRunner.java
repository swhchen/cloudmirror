package com.cloudmirror.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.dto.ValidateDtos;
import com.cloudmirror.entity.AssemblyEdge;
import com.cloudmirror.entity.AssemblyNode;
import com.cloudmirror.entity.CheckItemRecord;
import com.cloudmirror.entity.CheckRun;
import com.cloudmirror.entity.NodePort;
import com.cloudmirror.mapper.AssemblyEdgeMapper;
import com.cloudmirror.mapper.AssemblyNodeMapper;
import com.cloudmirror.mapper.CheckItemMapper;
import com.cloudmirror.mapper.CheckRunMapper;
import com.cloudmirror.mapper.NodePortMapper;
import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateResult;
import com.cloudmirror.service.rule.ValidateService;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把数据库里的装配映射成校验上下文，调算法，再把结论落库。
 *
 * <p>算法本身不依赖数据库；这里是唯一的"翻译层 + 持久化层"。
 */
@Service
public class ValidationRunner {

    private final ProjectService projectService;
    private final AssemblyNodeMapper nodeMapper;
    private final NodePortMapper portMapper;
    private final AssemblyEdgeMapper edgeMapper;
    private final CheckRunMapper checkRunMapper;
    private final CheckItemMapper checkItemMapper;
    private final ValidateService validateService;
    private final RedundancyService redundancyService;

    public ValidationRunner(ProjectService projectService,
                            AssemblyNodeMapper nodeMapper,
                            NodePortMapper portMapper,
                            AssemblyEdgeMapper edgeMapper,
                            CheckRunMapper checkRunMapper,
                            CheckItemMapper checkItemMapper,
                            ValidateService validateService,
                            RedundancyService redundancyService) {
        this.projectService = projectService;
        this.nodeMapper = nodeMapper;
        this.portMapper = portMapper;
        this.edgeMapper = edgeMapper;
        this.checkRunMapper = checkRunMapper;
        this.checkItemMapper = checkItemMapper;
        this.validateService = validateService;
        this.redundancyService = redundancyService;
    }

    @Transactional
    public ValidateDtos.Response validate(Long projectId) {
        projectService.require(projectId);
        ValidateContext ctx = loadContext(projectId);
        ValidateResult result = validateService.validate(ctx);

        CheckRun run = new CheckRun();
        run.setProjectId(projectId);
        run.setRunnable(result.runnable());
        run.setScore(result.score());
        run.setErrorCount(result.errorCount());
        run.setWarnCount(result.warnCount());
        run.setInfoCount(result.infoCount());
        run.setSummary(result.summary());
        run.setCreatedAt(LocalDateTime.now());
        checkRunMapper.insert(run);

        persistItems(run.getId(), result);
        return new ValidateDtos.Response(run.getId(), result);
    }

    /** 读取最近一次校验（不存在返回 null） */
    public ValidateDtos.LatestVO latest(Long projectId) {
        projectService.require(projectId);
        CheckRun run = checkRunMapper.selectOne(Wrappers.<CheckRun>lambdaQuery()
                .eq(CheckRun::getProjectId, projectId)
                .orderByDesc(CheckRun::getId)
                .last("limit 1"));
        if (run == null) {
            return null;
        }
        List<CheckItemRecord> items = checkItemMapper.selectList(Wrappers.<CheckItemRecord>lambdaQuery()
                .eq(CheckItemRecord::getRunId, run.getId())
                .orderByAsc(CheckItemRecord::getId));
        List<ValidateDtos.ItemVO> itemVOs = items.stream().map(this::toItemVO).toList();
        return new ValidateDtos.LatestVO(run.getId(), run.getRunnable(), run.getScore(),
                run.getErrorCount(), run.getWarnCount(), run.getInfoCount(), run.getSummary(),
                run.getCreatedAt(), itemVOs);
    }

    /** 装配 → 校验模型 */
    public ValidateContext loadContext(Long projectId) {
        List<AssemblyNode> nodes = nodeMapper.selectList(Wrappers.<AssemblyNode>lambdaQuery()
                .eq(AssemblyNode::getProjectId, projectId)
                .orderByAsc(AssemblyNode::getId));
        List<Long> nodeIds = nodes.stream().map(AssemblyNode::getId).toList();
        List<NodePort> ports = nodeIds.isEmpty() ? List.of()
                : portMapper.selectList(Wrappers.<NodePort>lambdaQuery()
                .in(NodePort::getNodeId, nodeIds)
                .orderByAsc(NodePort::getId));
        List<AssemblyEdge> edges = edgeMapper.selectList(Wrappers.<AssemblyEdge>lambdaQuery()
                .eq(AssemblyEdge::getProjectId, projectId)
                .orderByAsc(AssemblyEdge::getId));

        List<ValidationModel.Node> modelNodes = nodes.stream()
                .map(n -> new ValidationModel.Node(n.getId(), n.getName(), n.getNodeKind(),
                        d(n.getCpuCores()), d(n.getCpuRequest()),
                        i(n.getMemTotalMb()), i(n.getMemRequestMb()), d(n.getDiskGb()), n.getStatus()))
                .toList();
        List<ValidationModel.Port> modelPorts = ports.stream()
                .map(p -> new ValidationModel.Port(p.getId(), p.getNodeId(), i(p.getPort()),
                        p.getProtocol(), p.getScope(), p.getHostPort()))
                .toList();
        List<ValidationModel.Edge> modelEdges = edges.stream()
                .map(e -> new ValidationModel.Edge(e.getId(), e.getSourceNodeId(), e.getTargetNodeId(),
                        e.getEdgeKind(), l(e.getSourcePortId()), l(e.getTargetPortId()),
                        e.getStrong() == null || e.getStrong(), d(e.getWeight()),
                        e.getBandwidthMbps(), e.getLatencyMs()))
                .toList();
        // 冗余组参与 R12 校验（主备完整性）
        return new ValidateContext(modelNodes, modelPorts, modelEdges,
                redundancyService.groupsOf(projectId));
    }

    private void persistItems(Long runId, ValidateResult result) {
        for (ValidateResult.RuleStatus status : result.ruleStatus()) {
            if (status.passed()) {
                CheckItemRecord passed = new CheckItemRecord();
                passed.setRunId(runId);
                passed.setRuleCode(status.code());
                passed.setSeverity(status.severity().name());
                passed.setPassed(true);
                checkItemMapper.insert(passed);
                continue;
            }
            List<CheckItem> failures = result.items().stream()
                    .filter(i -> i.ruleCode().equals(status.code()))
                    .toList();
            for (CheckItem item : failures) {
                CheckItemRecord record = new CheckItemRecord();
                record.setRunId(runId);
                record.setRuleCode(item.ruleCode());
                record.setSeverity(item.severity().name());
                record.setPassed(false);
                record.setMessage(item.message());
                record.setSuggestion(item.suggestion());
                record.setNodeIds(join(item.nodeIds()));
                record.setEdgeIds(join(item.edgeIds()));
                checkItemMapper.insert(record);
            }
        }
    }

    private ValidateDtos.ItemVO toItemVO(CheckItemRecord r) {
        return new ValidateDtos.ItemVO(r.getId(), r.getRuleCode(), r.getSeverity(), r.getPassed(),
                r.getMessage(), r.getSuggestion(), split(r.getNodeIds()), split(r.getEdgeIds()));
    }

    private String join(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null
                : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，不影响回看
            }
        }
        return ids;
    }

    private double d(java.math.BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private int i(Integer v) {
        return v == null ? 0 : v;
    }

    private long l(Long v) {
        return v == null ? 0L : v;
    }
}
