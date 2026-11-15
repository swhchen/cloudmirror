package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R09 循环依赖：depends_on 图里存在环。能跑，但故障会来回震荡，给警告。
 */
@Component
public class R09CircularDependency implements ValidateRule {

    @Override
    public String code() {
        return "R09";
    }

    @Override
    public String name() {
        return "循环依赖";
    }

    @Override
    public Severity severity() {
        return Severity.WARN;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        Map<Long, Integer> color = new HashMap<>(); // 0 未访问 / 1 栈中 / 2 完成
        Deque<Long> stack = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        List<CheckItem> items = new ArrayList<>();

        for (ValidationModel.Node n : ctx.nodes()) {
            if (color.getOrDefault(n.id(), 0) == 0) {
                dfs(n.id(), ctx, color, stack, seen, items);
            }
        }
        return items;
    }

    private void dfs(Long node, ValidateContext ctx, Map<Long, Integer> color,
                     Deque<Long> stack, Set<String> seen, List<CheckItem> items) {
        color.put(node, 1);
        stack.push(node);
        for (Long next : ctx.dependsTargetsOf(node)) {
            int c = color.getOrDefault(next, 0);
            if (c == 0) {
                dfs(next, ctx, color, stack, seen, items);
            } else if (c == 1) {
                List<Long> cycle = new ArrayList<>();
                for (Long n : stack) {
                    cycle.add(n);
                    if (n.equals(next)) {
                        break;
                    }
                }
                cycle.add(next);
                Set<Long> key = new LinkedHashSet<>(cycle);
                String sig = key.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
                if (seen.add(sig)) {
                    String names = cycle.stream().map(ctx::nameOf).collect(Collectors.joining(" → "));
                    items.add(CheckItem.of(this,
                            "检测到循环依赖：" + names,
                            "让其中一个调用改成异步或去掉反向依赖",
                            cycle.stream().distinct().toList(), List.of()));
                }
            }
        }
        color.put(node, 2);
        stack.pop();
    }
}
