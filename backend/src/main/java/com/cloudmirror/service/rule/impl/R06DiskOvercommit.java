package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R06 磁盘超配：宿主机上所有子节点的磁盘需求之和不得超过宿主机磁盘。
 */
@Component
public class R06DiskOvercommit implements ValidateRule {

    @Override
    public String code() {
        return "R06";
    }

    @Override
    public String name() {
        return "磁盘超配";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        for (ValidationModel.Node host : ctx.hosts()) {
            List<ValidationModel.Node> children = ctx.childrenOf(host.id());
            double used = children.stream().mapToDouble(ValidationModel.Node::diskGb).sum();
            double cap = host.diskGb();
            if (used > cap + 1e-9 && cap > 0) {
                List<Long> ids = new ArrayList<>();
                ids.add(host.id());
                children.forEach(c -> ids.add(c.id()));
                items.add(CheckItem.of(this,
                        String.format("%s 磁盘超配：已分配 %.0f GB / 可用 %.0f GB",
                                host.name(), used, cap),
                        "清理磁盘占用，或换磁盘更大的宿主机",
                        ids, List.of()));
            }
        }
        return items;
    }
}
