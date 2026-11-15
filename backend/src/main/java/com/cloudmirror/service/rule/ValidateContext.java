package com.cloudmirror.service.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验上下文：把散落的节点/端口/连线整理成规则能直接用的索引。
 *
 * <p>12 条规则共用这一份索引，避免每条规则各扫一遍全表。
 */
public final class ValidateContext {

    private final List<ValidationModel.Node> nodes;
    private final List<ValidationModel.Port> ports;
    private final List<ValidationModel.Edge> edges;
    private final List<ValidationModel.Group> groups;

    private final Map<Long, ValidationModel.Node> nodeById = new LinkedHashMap<>();
    private final Map<Long, ValidationModel.Port> portById = new LinkedHashMap<>();
    private final Map<Long, List<ValidationModel.Port>> portsByNode = new HashMap<>();
    /** 宿主机 ID → 部署在它上面的节点 */
    private final Map<Long, List<ValidationModel.Node>> childrenByHost = new HashMap<>();
    /** 节点 ID → 宿主机 ID */
    private final Map<Long, Long> hostOf = new HashMap<>();
    private final Map<ValidationModel.PortKey, List<Long>> occupancy = new LinkedHashMap<>();
    private final List<ValidationModel.Edge> dependsEdges = new ArrayList<>();
    private final List<ValidationModel.Edge> networkLinks = new ArrayList<>();
    private final List<ValidationModel.Edge> portMaps = new ArrayList<>();
    private final Map<Long, List<Long>> networkAdj = new HashMap<>();
    private final Map<Long, List<Long>> forwardDepends = new HashMap<>();

    public ValidateContext(List<ValidationModel.Node> nodes,
                           List<ValidationModel.Port> ports,
                           List<ValidationModel.Edge> edges,
                           List<ValidationModel.Group> groups) {
        this.nodes = List.copyOf(nodes);
        this.ports = List.copyOf(ports);
        this.edges = List.copyOf(edges);
        this.groups = List.copyOf(groups);
        index();
    }

    private void index() {
        for (ValidationModel.Node n : nodes) {
            nodeById.put(n.id(), n);
        }
        for (ValidationModel.Port p : ports) {
            portById.put(p.id(), p);
            portsByNode.computeIfAbsent(p.nodeId(), k -> new ArrayList<>()).add(p);
        }
        for (ValidationModel.Edge e : edges) {
            switch (e.kind()) {
                case ValidationModel.DEPLOY_ON -> {
                    hostOf.put(e.sourceNodeId(), e.targetNodeId());
                    childrenByHost.computeIfAbsent(e.targetNodeId(), k -> new ArrayList<>())
                            .add(nodeById.get(e.sourceNodeId()));
                }
                case ValidationModel.DEPENDS_ON -> {
                    dependsEdges.add(e);
                    forwardDepends.computeIfAbsent(e.sourceNodeId(), k -> new ArrayList<>())
                            .add(e.targetNodeId());
                }
                case ValidationModel.NETWORK_LINK -> {
                    networkLinks.add(e);
                    networkAdj.computeIfAbsent(e.sourceNodeId(), k -> new ArrayList<>()).add(e.targetNodeId());
                    networkAdj.computeIfAbsent(e.targetNodeId(), k -> new ArrayList<>()).add(e.sourceNodeId());
                }
                case ValidationModel.PORT_MAP -> portMaps.add(e);
                default -> {
                    // replicate 等：暂不参与校验索引
                }
            }
        }
        // 端口占用：对外端口算到宿主机头上，内部端口算到节点自己头上
        for (ValidationModel.Port p : ports) {
            ValidationModel.Node owner = nodeById.get(p.nodeId());
            if (owner == null) {
                continue;
            }
            long keyOwner;
            int keyPort;
            if (p.isPublic()) {
                Long hostId = hostOf.get(owner.id());
                keyOwner = hostId != null ? hostId : owner.id();
                keyPort = p.effectiveHostPort();
            } else {
                keyOwner = owner.id();
                keyPort = p.port();
            }
            ValidationModel.PortKey key =
                    new ValidationModel.PortKey(keyOwner, norm(p.protocol()), keyPort);
            occupancy.computeIfAbsent(key, k -> new ArrayList<>()).add(owner.id());
        }
    }

    public static String norm(String protocol) {
        return protocol == null || protocol.isBlank() ? "tcp" : protocol.toLowerCase();
    }

    // ---------- 基础访问 ----------

    public List<ValidationModel.Node> nodes() {
        return nodes;
    }

    public List<ValidationModel.Port> ports() {
        return ports;
    }

    public List<ValidationModel.Edge> edges() {
        return edges;
    }

    public List<ValidationModel.Group> groups() {
        return groups;
    }

    public ValidationModel.Node node(Long id) {
        return nodeById.get(id);
    }

    public ValidationModel.Port port(Long id) {
        return portById.get(id);
    }

    public List<ValidationModel.Port> portsOf(Long nodeId) {
        return portsByNode.getOrDefault(nodeId, List.of());
    }

    public List<ValidationModel.Node> hosts() {
        return nodes.stream().filter(ValidationModel.Node::isHostLike).toList();
    }

    public List<ValidationModel.Node> services() {
        return nodes.stream()
                .filter(n -> ValidationModel.SERVICE.equals(n.kind()))
                .toList();
    }

    public List<ValidationModel.Node> entries() {
        return nodes.stream()
                .filter(ValidationModel.Node::isEntry)
                .toList();
    }

    public List<ValidationModel.Node> childrenOf(Long hostId) {
        return childrenByHost.getOrDefault(hostId, List.of());
    }

    public Long hostIdOf(Long nodeId) {
        return hostOf.get(nodeId);
    }

    public List<ValidationModel.Edge> dependsEdges() {
        return dependsEdges;
    }

    public List<ValidationModel.Edge> networkLinks() {
        return networkLinks;
    }

    public List<ValidationModel.Edge> portMaps() {
        return portMaps;
    }

    public Map<ValidationModel.PortKey, List<Long>> occupancy() {
        return occupancy;
    }

    // ---------- 图算法 ----------

    /** 网络可达：沿 network_link 无向图做 BFS */
    public boolean networkPathExists(Long from, Long to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            for (Long next : networkAdj.getOrDefault(cur, List.of())) {
                if (next.equals(to)) {
                    return true;
                }
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /** 从入口沿 depends_on 正向可达的节点集合 */
    public Set<Long> reachableViaDepends(Collection<Long> seeds) {
        Set<Long> seen = new LinkedHashSet<>(seeds);
        Deque<Long> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            for (Long next : forwardDepends.getOrDefault(cur, List.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    public List<Long> dependsTargetsOf(Long nodeId) {
        return forwardDepends.getOrDefault(nodeId, List.of());
    }

    /** 名字兜底，便于拼中文消息 */
    public String nameOf(Long nodeId) {
        ValidationModel.Node n = nodeById.get(nodeId);
        return n == null ? ("节点#" + nodeId) : n.name();
    }
}
