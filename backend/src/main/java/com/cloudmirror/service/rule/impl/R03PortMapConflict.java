package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * R03 端口映射冲突：两条 port_map 边把不同服务映射到同一宿主机的同一端口。
 */
@Component
public class R03PortMapConflict implements ValidateRule {

    @Override
    public String code() {
        return "R03";
    }

    @Override
    public String name() {
        return "端口映射冲突";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        Map<String, List<ValidationModel.Edge>> grouped = new LinkedHashMap<>();
        for (ValidationModel.Edge e : ctx.portMaps()) {
            ValidationModel.Port sp = ctx.port(e.sourcePortId());
            ValidationModel.Node target = ctx.node(e.targetNodeId());
            if (sp == null) {
                continue; // 缺失的端口由 R07 报
            }
            if (target == null || !target.isHostLike()) {
                items.add(CheckItem.of(this,
                        "端口映射的目标不是宿主机：" + ctx.nameOf(e.targetNodeId()),
                        "把映射边指向宿主机节点", List.of(e.sourceNodeId(), e.targetNodeId()), List.of(e.id())));
                continue;
            }
            String key = target.id() + "|" + ValidateContext.norm(sp.protocol()) + "|" + sp.effectiveHostPort();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<ValidationModel.Edge> edges = entry.getValue();
            String names = edges.stream()
                    .map(e -> ctx.nameOf(e.sourceNodeId()))
                    .distinct()
                    .collect(Collectors.joining("、"));
            ValidationModel.Port sp = ctx.port(edges.get(0).sourcePortId());
            items.add(CheckItem.of(this,
                    String.format("宿主机 %s 的 %s/%d 端口被 %d 条映射争抢：%s",
                            ctx.nameOf(edges.get(0).targetNodeId()),
                            ValidateContext.norm(sp.protocol()),
                            sp.effectiveHostPort(),
                            edges.size(), names),
                    "保留一条映射，其余改用不同宿主机端口",
                    edges.stream().map(ValidationModel.Edge::sourceNodeId).distinct().toList(),
                    edges.stream().map(ValidationModel.Edge::id).toList()));
        }
        return items;
    }
}
