package com.cloudmirror.service.rule;

import java.util.List;

/**
 * 校验引擎的领域模型。
 *
 * <p>与数据库实体解耦：实体由 Service 层映射成这里的 record，校验引擎只认这些不可变对象。
 * 好处是引擎可以脱离数据库、脱离 Spring 容器单测。
 */
public final class ValidationModel {

    // ---- 节点类型 ----
    public static final String HOST = "host";
    public static final String VM = "vm";
    public static final String K8S_CLUSTER = "k8s_cluster";
    public static final String POD = "pod";
    public static final String CONTAINER = "container";
    public static final String SERVICE = "service";
    public static final String MIDDLEWARE = "middleware";
    public static final String GATEWAY = "gateway";
    public static final String INGRESS = "ingress";
    public static final String OBJECT_STORAGE = "object_storage";
    public static final String EXTERNAL = "external";

    // ---- 连线类型 ----
    public static final String DEPLOY_ON = "deploy_on";
    public static final String NETWORK_LINK = "network_link";
    public static final String PORT_MAP = "port_map";
    public static final String DEPENDS_ON = "depends_on";
    public static final String REPLICATE = "replicate";

    // ---- 冗余模式 ----
    public static final String ACTIVE_STANDBY = "active_standby";
    public static final String ACTIVE_ACTIVE = "active_active";
    public static final String MANUAL_STANDBY = "manual_standby";

    // ---- 成员角色 ----
    public static final String PRIMARY = "primary";
    public static final String STANDBY = "standby";
    public static final String ACTIVE = "active";

    private ValidationModel() {
    }

    /**
     * 节点。
     *
     * @param cpuCores     宿主机总核数（host/vm）
     * @param cpuRequest   容器/服务的请求核数
     * @param memTotalMb   宿主机内存
     * @param memRequestMb 容器/服务请求内存
     */
    public record Node(Long id,
                       String name,
                       String kind,
                       double cpuCores,
                       double cpuRequest,
                       int memTotalMb,
                       int memRequestMb,
                       double diskGb,
                       String status) {

        public boolean isHostLike() {
            return HOST.equals(kind) || VM.equals(kind) || K8S_CLUSTER.equals(kind);
        }

        public boolean isWorkload() {
            return CONTAINER.equals(kind) || SERVICE.equals(kind) || POD.equals(kind);
        }

        /** 外部系统（不参与内网可达性判断） */
        public boolean isExternalLike() {
            return EXTERNAL.equals(kind) || OBJECT_STORAGE.equals(kind);
        }

        /** 业务入口 */
        public boolean isEntry() {
            return EXTERNAL.equals(kind) || GATEWAY.equals(kind) || INGRESS.equals(kind);
        }
    }

    /** 端口：scope = container / internal / public */
    public record Port(Long id, Long nodeId, int port, String protocol, String scope, Integer hostPort) {

        public boolean isPublic() {
            return "public".equals(scope);
        }

        /** 对外暴露时真正占用的宿主端口 */
        public int effectiveHostPort() {
            return hostPort != null ? hostPort : port;
        }
    }

    /** 连线（带宽/延迟可为空，时间窗传播会用到 latencyMs） */
    public record Edge(Long id,
                       Long sourceNodeId,
                       Long targetNodeId,
                       String kind,
                       Long sourcePortId,
                       Long targetPortId,
                       boolean strong,
                       double weight,
                       Integer bandwidthMbps,
                       Integer latencyMs) {

        /** 便捷构造：不带带宽/延迟 */
        public Edge(Long id, Long sourceNodeId, Long targetNodeId, String kind,
                    Long sourcePortId, Long targetPortId, boolean strong, double weight) {
            this(id, sourceNodeId, targetNodeId, kind, sourcePortId, targetPortId, strong, weight, null, null);
        }
    }

    /** 冗余组成员 */
    public record Member(Long nodeId, String role, Integer priority) {
    }

    /** 冗余组 */
    public record Group(Long id, String name, String type, List<Member> members) {
    }

    /** 端口占用键：ownerId 是宿主机 ID（对外端口）或节点自身 ID（内部端口） */
    public record PortKey(long ownerId, String protocol, int port) {
    }
}
