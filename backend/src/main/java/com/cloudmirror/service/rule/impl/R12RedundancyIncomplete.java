package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * R12 主备不完整：
 * <ul>
 *   <li>主备 / 手动备用：恰好 1 个 primary、至少 1 个 standby，备用必须写 priority</li>
 *   <li>双活：至少 2 个 active</li>
 *   <li>所有成员节点必须存在，且类型与主节点相容</li>
 * </ul>
 */
@Component
public class R12RedundancyIncomplete implements ValidateRule {

    @Override
    public String code() {
        return "R12";
    }

    @Override
    public String name() {
        return "主备不完整";
    }

    @Override
    public Severity severity() {
        return Severity.WARN;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        for (ValidationModel.Group g : ctx.groups()) {
            List<Long> memberIds = g.members().stream().map(ValidationModel.Member::nodeId).toList();

            List<Long> missing = memberIds.stream().filter(id -> ctx.node(id) == null).toList();
            if (!missing.isEmpty()) {
                items.add(CheckItem.of(this,
                        String.format("冗余组「%s」引用了不存在的节点：%s", g.name(), missing),
                        "删掉这些成员，或补回节点", missing, List.of()));
            }

            if (ValidationModel.ACTIVE_ACTIVE.equals(g.type())) {
                long actives = g.members().stream()
                        .filter(m -> ValidationModel.ACTIVE.equals(m.role()))
                        .count();
                if (actives < 2) {
                    items.add(CheckItem.of(this,
                            String.format("冗余组「%s」是双活模式，但只有 %d 个 active 成员", g.name(), actives),
                            "至少添加两个 active 成员，或改成主备模式", memberIds, List.of()));
                }
                continue;
            }

            List<ValidationModel.Member> primaries = g.members().stream()
                    .filter(m -> ValidationModel.PRIMARY.equals(m.role())).toList();
            List<ValidationModel.Member> standbys = g.members().stream()
                    .filter(m -> ValidationModel.STANDBY.equals(m.role())).toList();

            if (primaries.size() != 1) {
                items.add(CheckItem.of(this,
                        String.format("冗余组「%s」必须恰好 1 个主节点，当前 %d 个", g.name(), primaries.size()),
                        "指定唯一的 primary 成员", memberIds, List.of()));
            }
            if (standbys.isEmpty()) {
                items.add(CheckItem.of(this,
                        String.format("冗余组「%s」没有备用节点，故障时无法切换", g.name()),
                        "添加至少一个 standby 成员", memberIds, List.of()));
            }
            if (standbys.stream().anyMatch(m -> m.priority() == null)) {
                items.add(CheckItem.of(this,
                        String.format("冗余组「%s」有备用节点未设置切换优先级", g.name()),
                        "给每个备用节点填写 priority（数值越小越优先）", memberIds, List.of()));
            }
            if (primaries.size() == 1) {
                ValidationModel.Node primary = ctx.node(primaries.get(0).nodeId());
                if (primary != null) {
                    boolean incompatible = g.members().stream()
                            .map(m -> ctx.node(m.nodeId()))
                            .filter(Objects::nonNull)
                            .anyMatch(n -> !n.kind().equals(primary.kind()));
                    if (incompatible) {
                        items.add(CheckItem.of(this,
                                String.format("冗余组「%s」的成员类型不一致，主节点是 %s", g.name(), primary.kind()),
                                "主备节点应使用相同类型（例如都是 service）", memberIds, List.of()));
                    }
                    // 主备端口必须一致，否则切换后调用方连不上
                    Set<String> primaryPorts = portSet(ctx, primary.id());
                    for (ValidationModel.Member s : standbys) {
                        ValidationModel.Node standby = ctx.node(s.nodeId());
                        if (standby == null) {
                            continue;
                        }
                        Set<String> standbyPorts = portSet(ctx, standby.id());
                        if (standbyPorts.isEmpty()) {
                            items.add(CheckItem.of(this,
                                    String.format("冗余组「%s」的备用节点 %s 没有声明端口，切换后无法建立连接",
                                            g.name(), standby.name()),
                                    "给备用节点补上与主节点一致的端口", List.of(primary.id(), standby.id()), List.of()));
                        } else if (!standbyPorts.equals(primaryPorts)) {
                            items.add(CheckItem.of(this,
                                    String.format("冗余组「%s」主备端口不一致：主 %s / 备 %s，切换后调用方可能连不上",
                                            g.name(), primaryPorts, standbyPorts),
                                    "让备用节点使用与主节点相同的端口（通常在不同宿主机上）",
                                    List.of(primary.id(), standby.id()), List.of()));
                        }
                    }
                }
            }
        }
        return items;
    }

    /** 节点的端口集合，形如 ["tcp/3306"]，用于主备一致性比较 */
    private Set<String> portSet(ValidateContext ctx, Long nodeId) {
        return ctx.portsOf(nodeId).stream()
                .map(p -> ValidateContext.norm(p.protocol()) + "/" + p.port())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
