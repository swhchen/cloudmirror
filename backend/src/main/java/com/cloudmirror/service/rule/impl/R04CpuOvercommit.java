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
 * R04 CPU 超配：宿主机上所有子节点的 CPU 请求之和不得超过宿主机核数。
 */
@Component
public class R04CpuOvercommit implements ValidateRule {

    @Override
    public String code() {
        return "R04";
    }

    @Override
    public String name() {
        return "CPU 超配";
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
            double used = children.stream().mapToDouble(ValidationModel.Node::cpuRequest).sum();
            double cap = host.cpuCores();
            if (used > cap + 1e-9) {
                List<Long> ids = new ArrayList<>();
                ids.add(host.id());
                children.forEach(c -> ids.add(c.id()));
                items.add(CheckItem.of(this,
                        String.format("%s CPU 超配：已分配 %.1f 核 / 可用 %.1f 核（超出 %.1f 核）",
                                host.name(), used, cap, used - cap),
                        "减少子节点 CPU 请求，或把宿主机换成更大型号",
                        ids, List.of()));
            }
        }
        return items;
    }
}
