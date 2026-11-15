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
 * R02 端口越界：端口必须在 1–65535；对外端口必须声明宿主机映射端口。
 */
@Component
public class R02PortOutOfRange implements ValidateRule {

    @Override
    public String code() {
        return "R02";
    }

    @Override
    public String name() {
        return "端口越界";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public List<CheckItem> check(ValidateContext ctx) {
        List<CheckItem> items = new ArrayList<>();
        for (ValidationModel.Port p : ctx.ports()) {
            String node = ctx.nameOf(p.nodeId());
            if (p.port() < 1 || p.port() > 65535) {
                items.add(CheckItem.of(this,
                        String.format("%s 声明了非法端口 %d（必须在 1–65535）", node, p.port()),
                        "改成合法端口，例如 8080", List.of(p.nodeId()), List.of()));
            }
            if (p.hostPort() != null && (p.hostPort() < 1 || p.hostPort() > 65535)) {
                items.add(CheckItem.of(this,
                        String.format("%s 的宿主机映射端口 %d 非法（必须在 1–65535）", node, p.hostPort()),
                        "改成合法端口，例如 18080", List.of(p.nodeId()), List.of()));
            }
            if (p.isPublic() && p.hostPort() == null) {
                items.add(CheckItem.of(this,
                        String.format("%s 的对外端口 %d 没有指定宿主机映射端口，外部无法访问", node, p.port()),
                        "补一个宿主机映射端口，例如 18080", List.of(p.nodeId()), List.of()));
            }
        }
        return items;
    }
}
