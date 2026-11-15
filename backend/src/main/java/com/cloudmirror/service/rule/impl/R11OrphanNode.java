package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R11 孤儿节点：没有参与任何连线。不影响可运行，只是提示。
 */
@Component
public class R11OrphanNode implements ValidateRule {

    @Override
    public String code() {
        return "R11";
    }

    @Override
    public String name() {
        return "孤儿节点";
    }

    @Override
    public Severity severity() {
        return Severity.INFO;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        Set<Long> connected = new HashSet<>();
        ctx.edges().forEach(e -> {
            connected.add(e.sourceNodeId());
            connected.add(e.targetNodeId());
        });
        List<ValidationModel.Node> orphans = ctx.nodes().stream()
                .filter(n -> !connected.contains(n.id()))
                .toList();
        if (orphans.isEmpty()) {
            return List.of();
        }
        String names = orphans.stream().map(ValidationModel.Node::name).collect(Collectors.joining("、"));
        return List.of(CheckItem.of(this,
                "以下节点没有连任何线：" + names,
                "把它们连进拓扑，或删掉不用",
                orphans.stream().map(ValidationModel.Node::id).toList(), List.of()));
    }
}
