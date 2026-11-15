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
 * R07 依赖缺失：
 * <ol>
 *   <li>depends_on 指向的节点或端口必须真实存在</li>
 *   <li>每个 service 至少要依赖一个 middleware（数据库 / 缓存 / 消息队列）</li>
 * </ol>
 */
@Component
public class R07DependencyMissing implements ValidateRule {

    @Override
    public String code() {
        return "R07";
    }

    @Override
    public String name() {
        return "依赖缺失";
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
                items.add(CheckItem.of(this,
                        "依赖边指向不存在的节点：" + ctx.nameOf(e.sourceNodeId()) + " → " + ctx.nameOf(e.targetNodeId()),
                        "删除这条无效连线，或补回目标节点",
                        List.of(e.sourceNodeId(), e.targetNodeId()), List.of(e.id())));
                continue;
            }
            if (src.isExternalLike() || tgt.isExternalLike()) {
                continue; // 外部系统 / 对象存储不建模端口，允许无端口的依赖边
            }
            if (e.sourcePortId() == null || e.sourcePortId() <= 0
                    || e.targetPortId() == null || e.targetPortId() <= 0) {
                items.add(CheckItem.of(this,
                        String.format("依赖边 %s → %s 没有指定端口（调用依赖必须落到接口）",
                                src.name(), tgt.name()),
                        "重新连线并选择调用方端口和被调用方端口",
                        List.of(src.id(), tgt.id()), List.of(e.id())));
                continue;
            }
            if (e.sourcePortId() != null && e.sourcePortId() > 0) {
                ValidationModel.Port sp = ctx.port(e.sourcePortId());
                if (sp == null || !sp.nodeId().equals(src.id())) {
                    items.add(CheckItem.of(this,
                            String.format("依赖边 %s → %s 的源端口不存在或不属于调用方", src.name(), tgt.name()),
                            "重新选择调用方的端口", List.of(src.id(), tgt.id()), List.of(e.id())));
                }
            }
            if (e.targetPortId() != null && e.targetPortId() > 0) {
                ValidationModel.Port tp = ctx.port(e.targetPortId());
                if (tp == null || !tp.nodeId().equals(tgt.id())) {
                    items.add(CheckItem.of(this,
                            String.format("依赖边 %s → %s 的目标端口不存在或不属于被调用方", src.name(), tgt.name()),
                            "重新选择被调用方的端口", List.of(src.id(), tgt.id()), List.of(e.id())));
                }
            }
        }

        for (ValidationModel.Node svc : ctx.services()) {
            if (!reachesStorage(ctx, svc.id())) {
                items.add(CheckItem.of(this,
                        String.format("服务 %s 没有（直接或间接）依赖任何中间件/存储（数据库 / 缓存 / 消息队列 / 对象存储）", svc.name()),
                        "给它连一条到数据库或缓存的依赖边（经其它服务间接依赖也可以）", List.of(svc.id()), List.of()));
            }
        }
        return items;
    }

    /**
     * 沿 depends_on 向下找中间件/对象存储，支持 service → service → DB 这种间接依赖。
     * 用访问集合防环。
     */
    private boolean reachesStorage(ValidateContext ctx, long serviceId) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.Deque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(serviceId);
        seen.add(serviceId);
        while (!queue.isEmpty()) {
            long cur = queue.poll();
            for (ValidationModel.Edge e : ctx.dependsEdges()) {
                if (!e.sourceNodeId().equals(cur)) {
                    continue;
                }
                ValidationModel.Node t = ctx.node(e.targetNodeId());
                if (t == null) {
                    continue;
                }
                if (ValidationModel.MIDDLEWARE.equals(t.kind())
                        || ValidationModel.OBJECT_STORAGE.equals(t.kind())) {
                    return true;
                }
                if (seen.add(t.id())) {
                    queue.add(t.id());
                }
            }
        }
        return false;
    }
}
