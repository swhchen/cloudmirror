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
 * R08 网络不可达：有依赖关系，但两端既不在同一宿主机，也没有 network_link 通路。
 */
@Component
public class R08NetworkUnreachable implements ValidateRule {

    @Override
    public String code() {
        return "R08";
    }

    @Override
    public String name() {
        return "网络不可达";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        for (ValidationModel.Edge e : ctx.dependsEdges()) {
            ValidationModel.Node src = ctx.node(e.sourceNodeId());
            ValidationModel.Node tgt = ctx.node(e.targetNodeId());
            if (src == null || tgt == null) {
                continue;
            }
            // 外部系统 / 对象存储在拓扑之外，通过公网接入，不参与内网可达性判断
            if (src.isExternalLike() || tgt.isExternalLike()) {
                continue;
            }
            Long hostA = ctx.hostIdOf(src.id());
            Long hostB = ctx.hostIdOf(tgt.id());
            if (hostA != null && hostA.equals(hostB)) {
                continue; // 同宿主机，直连
            }
            if (hostA == null && hostB == null) {
                continue; // 两端都还没部署到宿主机：结构未完成，不做可达性判断
            }
            // 跨宿主机：看宿主机之间是否有 network_link 通路；没有宿主机的节点则按节点自身判断
            boolean reachable = (hostA != null && hostB != null)
                    ? ctx.networkPathExists(hostA, hostB)
                    : ctx.networkPathExists(src.id(), tgt.id());
            if (!reachable) {
                items.add(CheckItem.of(this,
                        String.format("%s → %s 有依赖声明，但两者不在同一宿主机且没有网络可达路径",
                                src.name(), tgt.name()),
                        "补一条 network_link 连线，或把它们部署在同一宿主机",
                        List.of(src.id(), tgt.id()), List.of(e.id())));
            }
        }
        return items;
    }
}
