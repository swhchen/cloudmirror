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
 * R05 内存超配：宿主机上所有子节点的内存请求之和不得超过宿主机内存。
 */
@Component
public class R05MemOvercommit implements ValidateRule {

    @Override
    public String code() {
        return "R05";
    }

    @Override
    public String name() {
        return "内存超配";
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
            int used = children.stream().mapToInt(ValidationModel.Node::memRequestMb).sum();
            int cap = host.memTotalMb();
            if (used > cap) {
                List<Long> ids = new ArrayList<>();
                ids.add(host.id());
                children.forEach(c -> ids.add(c.id()));
                items.add(CheckItem.of(this,
                        String.format("%s 内存超配：已分配 %d MB / 可用 %d MB（超出 %d MB）",
                                host.name(), used, cap, used - cap),
                        "减少子节点内存请求，或换内存更大的宿主机",
                        ids, List.of()));
            }
        }
        return items;
    }
}
