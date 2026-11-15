package com.cloudmirror.service.rule.impl;

import com.cloudmirror.service.rule.CheckItem;
import com.cloudmirror.service.rule.Severity;
import com.cloudmirror.service.rule.ValidateContext;
import com.cloudmirror.service.rule.ValidateRule;
import com.cloudmirror.service.rule.ValidationModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * R01 端口冲突：同一宿主机（或同一节点）上，同协议同端口被两个以上节点占用。
 */
@Component
public class R01PortConflict implements ValidateRule {

    @Override
    public String code() {
        return "R01";
    }

    @Override
    public String name() {
        return "端口冲突";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        for (var entry : ctx.occupancy().entrySet()) {
            List<Long> owners = entry.getValue();
            List<Long> distinct = owners.stream().distinct().toList();
            if (distinct.size() > 1) {
                ValidationModel.PortKey key = entry.getKey();
                String names = distinct.stream().map(ctx::nameOf).collect(Collectors.joining("、"));
                items.add(CheckItem.of(this,
                        String.format("%s 上 %s/%d 端口被 %d 个节点同时占用：%s",
                                ctx.nameOf(key.ownerId()), key.protocol(), key.port(), distinct.size(), names),
                        "改掉其中一个节点的端口，或把它们部署到不同宿主机",
                        distinct, List.of()));
            } else if (owners.size() > distinct.size()) {
                // 同一个节点把同一端口声明了多次（distinct 只有 1 个，但声明了多次）
                ValidationModel.PortKey key = entry.getKey();
                items.add(CheckItem.of(this,
                        String.format("节点 %s 重复声明了 %s/%d 端口 %d 次",
                                ctx.nameOf(distinct.get(0)), key.protocol(), key.port(), owners.size()),
                        "删掉重复的端口声明",
                        distinct, List.of()));
            }
        }
        return items;
    }
}
