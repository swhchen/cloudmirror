package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R10 入口不可达：从网关 / 外部系统出发，沿 depends_on 必须能到达每个应用服务。
 */
@Component
public class R10EntryUnreachable implements ValidateRule {

    @Override
    public String code() {
        return "R10";
    }

    @Override
    public String name() {
        return "入口不可达";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        List<ValidationModel.Node> entries = ctx.entries();
        if (entries.isEmpty()) {
            if (!ctx.services().isEmpty()) {
                // 还没有画入口 = 结构没搭完，先提醒，不当成"不可运行"的死错误
                items.add(CheckItem.of(code(), name(), Severity.WARN,
                        "还没有网关或外部系统节点，业务入口未定义",
                        "从左侧加一个『网关 / 负载均衡』或『外部系统』节点，再连到服务上",
                        ctx.services().stream().map(ValidationModel.Node::id).toList(), List.of()));
            }
            return items;
        }

        Set<Long> reachable = ctx.reachableViaDepends(entries.stream().map(ValidationModel.Node::id).toList());
        List<ValidationModel.Node> unreachable = ctx.services().stream()
                .filter(s -> !reachable.contains(s.id()))
                .toList();
        if (!unreachable.isEmpty()) {
            String names = unreachable.stream().map(ValidationModel.Node::name).collect(Collectors.joining("、"));
            items.add(CheckItem.of(this,
                    "以下服务从入口不可达：" + names,
                    "补一条从网关到这些服务的依赖连线",
                    unreachable.stream().map(ValidationModel.Node::id).toList(), List.of()));
        }
        return items;
    }
}
