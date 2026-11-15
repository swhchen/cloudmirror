export const NODE_KINDS = [
  { kind: 'host', label: '物理机 / 宿主机', color: '#38bdf8', hint: '承载容器与服务', category: '计算' },
  { kind: 'vm', label: '虚拟机', color: '#60a5fa', hint: '与宿主机同类', category: '计算' },
  { kind: 'k8s_cluster', label: 'K8s 集群', color: '#22d3ee', hint: '承载 Pod 与负载', category: '计算' },
  { kind: 'pod', label: 'Pod / 工作负载', color: '#2dd4bf', hint: 'K8s 最小调度单元', category: '计算' },
  { kind: 'container', label: '容器', color: '#34d399', hint: '请求 CPU / 内存', category: '计算' },
  { kind: 'service', label: '应用服务', color: '#fbbf24', hint: '必须依赖中间件', category: '应用' },
  { kind: 'middleware', label: '中间件', color: '#f472b6', hint: 'MySQL / Redis / MQ', category: '存储与中间件' },
  { kind: 'object_storage', label: '对象存储', color: '#fb923c', hint: 'OSS / S3，外部依赖', category: '存储与中间件' },
  { kind: 'gateway', label: '网关 / 负载均衡', color: '#a78bfa', hint: '业务入口', category: '网络入口' },
  { kind: 'ingress', label: 'Ingress', color: '#c084fc', hint: '集群入口，等同网关', category: '网络入口' },
  { kind: 'external', label: '外部系统', color: '#94a3b8', hint: '拓扑之外', category: '网络入口' }
]

/** 节点图标（画布上直接显示，减少"太素"的感觉） */
export const KIND_ICON = {
  host: '🖥',
  vm: '💻',
  k8s_cluster: '☸',
  pod: '📦',
  container: '🧊',
  service: '⚙',
  middleware: '🗄',
  object_storage: '🪣',
  gateway: '🚪',
  ingress: '🌐',
  external: '☁'
}

export const EDGE_KINDS = [
  { kind: 'depends_on', label: '调用依赖', color: '#38bdf8', needsPort: true, dash: '', hint: '接口级·需端口' },
  { kind: 'deploy_on', label: '部署于', color: '#64748b', needsPort: false, dash: '6 4', hint: '节点级' },
  { kind: 'network_link', label: '网络可达', color: '#a78bfa', needsPort: false, dash: '3 3', hint: '节点级·主机之间' },
  { kind: 'port_map', label: '端口映射', color: '#f59e0b', needsPort: true, dash: '2 3', hint: '接口级·需端口' },
  { kind: 'replicate', label: '主备复制', color: '#f472b6', needsPort: true, dash: '10 4', hint: '接口级·需端口' }
]

export function kindMeta(kind) {
  return NODE_KINDS.find((k) => k.kind === kind) || NODE_KINDS[0]
}

export function edgeMeta(kind) {
  return EDGE_KINDS.find((k) => k.kind === kind) || EDGE_KINDS[0]
}

export const SEVERITY_COLOR = {
  ERROR: '#ef4444',
  WARN: '#f59e0b',
  INFO: '#38bdf8'
}
