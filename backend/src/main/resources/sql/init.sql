-- ============================================================
-- 云镜推演 · MySQL 建表脚本（幂等，可重复执行）
-- 库：cloudmirror（JDBC 参数 createDatabaseIfNotExist=true 自动创建）
-- 约定：所有时间字段由应用层维护；金额/资源用 DECIMAL 避免浮点误差
-- ============================================================

-- 用户表（本轮不做登录，预留）
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(50),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（预留，P5 接登录）';

-- 推演工程：一张画布 = 一个工程，支持多工程并行
CREATE TABLE IF NOT EXISTS project (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id      BIGINT NOT NULL DEFAULT 0 COMMENT '归属用户，0=本地默认用户',
  name          VARCHAR(100) NOT NULL,
  description   VARCHAR(500),
  view_scale    DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '画布缩放',
  view_offset_x DOUBLE NOT NULL DEFAULT 0,
  view_offset_y DOUBLE NOT NULL DEFAULT 0,
  grid_snap     TINYINT(1) NOT NULL DEFAULT 1 COMMENT '网格吸附开关',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_owner_name (owner_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推演工程';

-- 型号库：节点选型号 → 自动带出资源，带出后仍可手改
CREATE TABLE IF NOT EXISTS asm_device_model (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  kind       VARCHAR(30)  NOT NULL COMMENT 'host/vm/container/service/middleware/gateway/external',
  model_name VARCHAR(100) NOT NULL,
  cpu_cores  DECIMAL(6,2) NOT NULL DEFAULT 0,
  mem_mb     INT          NOT NULL DEFAULT 0,
  disk_gb    DECIMAL(8,2) NOT NULL DEFAULT 0,
  spec_json  VARCHAR(500) COMMENT '附加参数，如 {"maxConnections":500}',
  builtin    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=内置只读，0=自定义',
  UNIQUE KEY uk_kind_model (kind, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='型号库';

-- 装配节点
CREATE TABLE IF NOT EXISTS asm_node (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id      BIGINT NOT NULL,
  model_id        BIGINT COMMENT '型号（可空，空=纯手填）',
  name            VARCHAR(100) NOT NULL,
  node_kind       VARCHAR(30)  NOT NULL,
  cpu_cores       DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '宿主机总核数',
  cpu_request     DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '容器/服务请求核数',
  mem_total_mb    INT NOT NULL DEFAULT 0,
  mem_request_mb  INT NOT NULL DEFAULT 0,
  disk_gb         DECIMAL(8,2) NOT NULL DEFAULT 0,
  replicas        INT NOT NULL DEFAULT 1,
  ip              VARCHAR(45),
  runtime         VARCHAR(100) COMMENT '镜像/版本',
  status          VARCHAR(20) NOT NULL DEFAULT 'healthy' COMMENT 'healthy/degraded/down',
  pos_x           DOUBLE NOT NULL DEFAULT 0,
  pos_y           DOUBLE NOT NULL DEFAULT 0,
  description     VARCHAR(500),
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_project (project_id),
  UNIQUE KEY uk_project_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装配节点';

-- 节点端口（连线时的"接口"）
CREATE TABLE IF NOT EXISTS asm_node_port (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_id   BIGINT NOT NULL,
  port      INT NOT NULL,
  protocol  VARCHAR(10) NOT NULL DEFAULT 'tcp',
  scope     VARCHAR(20) NOT NULL DEFAULT 'internal' COMMENT 'container/internal/public',
  host_port INT COMMENT '映射到宿主机的端口，scope=public 时必填',
  purpose   VARCHAR(100),
  KEY idx_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点端口';

-- 连线：五类边，端口端点可为 0（表示该边不涉及具体端口，如 deploy_on）
CREATE TABLE IF NOT EXISTS asm_edge (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id     BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  target_node_id BIGINT NOT NULL,
  edge_kind      VARCHAR(20) NOT NULL COMMENT 'deploy_on/network_link/port_map/depends_on/replicate',
  source_port_id BIGINT NOT NULL DEFAULT 0,
  target_port_id BIGINT NOT NULL DEFAULT 0,
  bandwidth_mbps INT,
  latency_ms     INT,
  strong         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=强依赖，0=弱依赖（传播衰减用）',
  weight         DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '传播强度权重',
  description    VARCHAR(300),
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_edge (project_id, source_node_id, target_node_id, edge_kind, source_port_id, target_port_id),
  KEY idx_project (project_id),
  KEY idx_source (source_node_id),
  KEY idx_target (target_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点连线';

-- 一次可运行性校验
CREATE TABLE IF NOT EXISTS asm_check_run (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id  BIGINT NOT NULL,
  runnable    TINYINT(1) NOT NULL,
  score       INT NOT NULL,
  error_count INT NOT NULL DEFAULT 0,
  warn_count  INT NOT NULL DEFAULT 0,
  info_count  INT NOT NULL DEFAULT 0,
  summary     VARCHAR(500),
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可运行性校验记录';

-- 校验明细
CREATE TABLE IF NOT EXISTS asm_check_item (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id     BIGINT NOT NULL,
  rule_code  VARCHAR(10) NOT NULL,
  severity   VARCHAR(10) NOT NULL COMMENT 'ERROR/WARN/INFO',
  passed     TINYINT(1) NOT NULL,
  message    VARCHAR(500),
  suggestion VARCHAR(500),
  node_ids   VARCHAR(300) COMMENT '逗号分隔，前端据此定位画布元素',
  edge_ids   VARCHAR(300),
  KEY idx_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校验明细';

-- ============================================================
-- 内置型号（16 个，可重复执行）
-- ============================================================
INSERT INTO asm_device_model (id, kind, model_name, cpu_cores, mem_mb, disk_gb, spec_json, builtin) VALUES
  (1,  'host',       '入门型 2C4G',   2,   4096,  40,  NULL, 1),
  (2,  'host',       '标准型 4C8G',   4,   8192,  100, NULL, 1),
  (3,  'host',       '计算型 8C16G',  8,   16384, 200, NULL, 1),
  (4,  'host',       '内存型 16C32G', 16,  32768, 500, NULL, 1),
  (5,  'vm',         '虚拟机 2C4G',   2,   4096,  40,  NULL, 1),
  (6,  'vm',         '虚拟机 4C8G',   4,   8192,  100, NULL, 1),
  (7,  'container',  '迷你 0.5C1G',   0.5, 1024,  0,   NULL, 1),
  (8,  'container',  '标准 1C2G',     1,   2048,  0,   NULL, 1),
  (9,  'container',  '大型 2C4G',     2,   4096,  0,   NULL, 1),
  (10, 'service',    '轻量 1C2G',     1,   2048,  0,   NULL, 1),
  (11, 'service',    '标准 2C4G',     2,   4096,  0,   NULL, 1),
  (12, 'middleware', 'Redis 2C4G',    2,   4096,  20,  '{"maxConnections":10000}', 1),
  (13, 'middleware', 'MySQL 4C8G',    4,   8192,  200, '{"maxConnections":500}', 1),
  (14, 'middleware', 'MQ 2C4G',       2,   4096,  100, '{"maxQueues":1000}', 1),
  (15, 'gateway',    '网关 1C2G',     1,   2048,  0,   NULL, 1),
  (16, 'external',   '外部接口',      0,   0,     0,   NULL, 1)
ON DUPLICATE KEY UPDATE
  cpu_cores = VALUES(cpu_cores),
  mem_mb    = VALUES(mem_mb),
  disk_gb   = VALUES(disk_gb),
  spec_json = VALUES(spec_json),
  builtin   = VALUES(builtin);

-- ============================================================
-- 推演期表（故障 / 传播分析 / 恢复决策 / 冗余组）
-- ============================================================

CREATE TABLE IF NOT EXISTS redundancy_group (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id      BIGINT NOT NULL,
  name            VARCHAR(100) NOT NULL,
  redundancy_type VARCHAR(30)  NOT NULL COMMENT 'active_standby / active_active / manual_standby',
  description     VARCHAR(500),
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rg_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冗余组';

CREATE TABLE IF NOT EXISTS redundancy_member (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_id   BIGINT NOT NULL,
  node_id    BIGINT NOT NULL,
  member_role VARCHAR(20) NOT NULL COMMENT 'primary / standby / active',
  priority   INT COMMENT '数值越小越优先',
  UNIQUE KEY uk_group_node (group_id, node_id),
  KEY idx_rm_group (group_id),
  KEY idx_rm_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冗余组成员';

CREATE TABLE IF NOT EXISTS fault_event (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id     BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  fault_type     VARCHAR(30) NOT NULL,
  severity       VARCHAR(20) NOT NULL,
  description    VARCHAR(500),
  status         VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active / recovered',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  recovered_at   DATETIME,
  KEY idx_fault_project (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件';

CREATE TABLE IF NOT EXISTS propagation_analysis (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id           BIGINT NOT NULL,
  fault_source_count   INT NOT NULL DEFAULT 0,
  direct_count         INT NOT NULL DEFAULT 0,
  indirect_count       INT NOT NULL DEFAULT 0,
  total_affected_count INT NOT NULL DEFAULT 0,
  total_node_count     INT NOT NULL DEFAULT 0,
  impact_ratio         DECIMAL(6,4) NOT NULL DEFAULT 0,
  summary              VARCHAR(500),
  window_seconds       INT NULL,
  cascade_summary      VARCHAR(500),
  analysis_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_pa_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传播分析汇总';

CREATE TABLE IF NOT EXISTS analysis_fault_source (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  analysis_id BIGINT NOT NULL,
  fault_id    BIGINT,
  node_id     BIGINT,
  node_name   VARCHAR(100),
  fault_type  VARCHAR(30),
  severity    VARCHAR(20),
  KEY idx_afs_analysis (analysis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障源快照';

CREATE TABLE IF NOT EXISTS analysis_affected_node (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  analysis_id     BIGINT NOT NULL,
  node_id         BIGINT,
  node_name       VARCHAR(100),
  impact_level    VARCHAR(20),
  distance        INT,
  source_fault_id BIGINT,
  source_node_id  BIGINT,
  KEY idx_aan_analysis (analysis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受影响节点快照';

CREATE TABLE IF NOT EXISTS analysis_path_step (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  affected_node_id   BIGINT NOT NULL,
  step_index         INT,
  node_id            BIGINT,
  node_name          VARCHAR(100),
  edge_id            BIGINT,
  caller_port        BIGINT,
  callee_port        BIGINT,
  KEY idx_aps_affected (affected_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传播路径步骤';

CREATE TABLE IF NOT EXISTS recovery_plan (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id           BIGINT NOT NULL,
  analysis_id          BIGINT,
  recommended_type     VARCHAR(30),
  recommended_name     VARCHAR(50),
  recommendation_reason VARCHAR(600),
  weights_json         VARCHAR(300),
  estimation_notice    VARCHAR(600),
  created_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rp_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次恢复推荐';

CREATE TABLE IF NOT EXISTS recovery_strategy (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id                  BIGINT NOT NULL,
  strategy_type            VARCHAR(30),
  strategy_name            VARCHAR(50),
  applicable               TINYINT(1) NOT NULL DEFAULT 0,
  inapplicable_reason       VARCHAR(400),
  estimated_recovery_seconds INT,
  impact_after_count       INT,
  impact_ratio_after       DECIMAL(6,4),
  cost_level               VARCHAR(10),
  risk_level               VARCHAR(10),
  score                    DECIMAL(6,2),
  breakdown_json           VARCHAR(500),
  redundancy_group_id      BIGINT,
  redundancy_group_name    VARCHAR(100),
  primary_node_id          BIGINT,
  selected_standby_node_id BIGINT,
  selected_standby_name    VARCHAR(100),
  selection_reason         VARCHAR(300),
  candidates_json          VARCHAR(1500),
  checks_json              VARCHAR(2000),
  KEY idx_rs_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略评估（明细以 JSON 存，避免过度拆表）';