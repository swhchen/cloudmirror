-- ============================================================
-- 云镜推演 · H2 建表脚本（profile=h2 本地联调用）
-- 与 sql/init.sql 表结构一致，仅方言不同（无 ENGINE/CHARSET，索引单独建）
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(50),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON sys_user (username);

CREATE TABLE IF NOT EXISTS project (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id      BIGINT NOT NULL DEFAULT 0,
  name          VARCHAR(100) NOT NULL,
  description   VARCHAR(500),
  view_scale    DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  view_offset_x DOUBLE NOT NULL DEFAULT 0,
  view_offset_y DOUBLE NOT NULL DEFAULT 0,
  grid_snap     TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_name ON project (owner_id, name);

CREATE TABLE IF NOT EXISTS asm_device_model (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind       VARCHAR(30)  NOT NULL,
  model_name VARCHAR(100) NOT NULL,
  cpu_cores  DECIMAL(6,2) NOT NULL DEFAULT 0,
  mem_mb     INT          NOT NULL DEFAULT 0,
  disk_gb    DECIMAL(8,2) NOT NULL DEFAULT 0,
  spec_json  VARCHAR(500),
  builtin    TINYINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kind_model ON asm_device_model (kind, model_name);

CREATE TABLE IF NOT EXISTS asm_node (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id      BIGINT NOT NULL,
  model_id        BIGINT,
  name            VARCHAR(100) NOT NULL,
  node_kind       VARCHAR(30)  NOT NULL,
  cpu_cores       DECIMAL(6,2) NOT NULL DEFAULT 0,
  cpu_request     DECIMAL(6,2) NOT NULL DEFAULT 0,
  mem_total_mb    INT NOT NULL DEFAULT 0,
  mem_request_mb  INT NOT NULL DEFAULT 0,
  disk_gb         DECIMAL(8,2) NOT NULL DEFAULT 0,
  replicas        INT NOT NULL DEFAULT 1,
  ip              VARCHAR(45),
  runtime         VARCHAR(100),
  status          VARCHAR(20) NOT NULL DEFAULT 'healthy',
  pos_x           DOUBLE NOT NULL DEFAULT 0,
  pos_y           DOUBLE NOT NULL DEFAULT 0,
  description     VARCHAR(500),
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_node_project ON asm_node (project_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_name ON asm_node (project_id, name);

CREATE TABLE IF NOT EXISTS asm_node_port (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  node_id   BIGINT NOT NULL,
  port      INT NOT NULL,
  protocol  VARCHAR(10) NOT NULL DEFAULT 'tcp',
  scope     VARCHAR(20) NOT NULL DEFAULT 'internal',
  host_port INT,
  purpose   VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_port_node ON asm_node_port (node_id);

CREATE TABLE IF NOT EXISTS asm_edge (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id     BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  target_node_id BIGINT NOT NULL,
  edge_kind      VARCHAR(20) NOT NULL,
  source_port_id BIGINT NOT NULL DEFAULT 0,
  target_port_id BIGINT NOT NULL DEFAULT 0,
  bandwidth_mbps INT,
  latency_ms     INT,
  strong         TINYINT NOT NULL DEFAULT 1,
  weight         DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  description    VARCHAR(300),
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_edge ON asm_edge
  (project_id, source_node_id, target_node_id, edge_kind, source_port_id, target_port_id);
CREATE INDEX IF NOT EXISTS idx_edge_project ON asm_edge (project_id);
CREATE INDEX IF NOT EXISTS idx_edge_source ON asm_edge (source_node_id);
CREATE INDEX IF NOT EXISTS idx_edge_target ON asm_edge (target_node_id);

CREATE TABLE IF NOT EXISTS asm_check_run (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  runnable    TINYINT NOT NULL,
  score       INT NOT NULL,
  error_count INT NOT NULL DEFAULT 0,
  warn_count  INT NOT NULL DEFAULT 0,
  info_count  INT NOT NULL DEFAULT 0,
  summary     VARCHAR(500),
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_run_project ON asm_check_run (project_id);

CREATE TABLE IF NOT EXISTS asm_check_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id     BIGINT NOT NULL,
  rule_code  VARCHAR(10) NOT NULL,
  severity   VARCHAR(10) NOT NULL,
  passed     TINYINT NOT NULL,
  message    VARCHAR(500),
  suggestion VARCHAR(500),
  node_ids   VARCHAR(300),
  edge_ids   VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS idx_item_run ON asm_check_item (run_id);

-- 内置型号（H2 用 MERGE，可重复执行）
MERGE INTO asm_device_model (id, kind, model_name, cpu_cores, mem_mb, disk_gb, spec_json, builtin) KEY (id) VALUES
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
  (16, 'external',   '外部接口',      0,   0,     0,   NULL, 1);

-- ============================================================
-- 推演期表（H2 版）
-- ============================================================

CREATE TABLE IF NOT EXISTS redundancy_group (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id      BIGINT NOT NULL,
  name            VARCHAR(100) NOT NULL,
  redundancy_type VARCHAR(30) NOT NULL,
  description     VARCHAR(500),
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rg_project ON redundancy_group (project_id);

CREATE TABLE IF NOT EXISTS redundancy_member (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id    BIGINT NOT NULL,
  node_id     BIGINT NOT NULL,
  member_role VARCHAR(20) NOT NULL,
  priority    INT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_group_node ON redundancy_member (group_id, node_id);
CREATE INDEX IF NOT EXISTS idx_rm_group ON redundancy_member (group_id);
CREATE INDEX IF NOT EXISTS idx_rm_node ON redundancy_member (node_id);

CREATE TABLE IF NOT EXISTS fault_event (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id     BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  fault_type     VARCHAR(30) NOT NULL,
  severity       VARCHAR(20) NOT NULL,
  description    VARCHAR(500),
  status         VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  recovered_at   DATETIME
);
CREATE INDEX IF NOT EXISTS idx_fault_project ON fault_event (project_id, status);

CREATE TABLE IF NOT EXISTS propagation_analysis (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id           BIGINT NOT NULL,
  fault_source_count   INT NOT NULL DEFAULT 0,
  direct_count         INT NOT NULL DEFAULT 0,
  indirect_count       INT NOT NULL DEFAULT 0,
  total_affected_count INT NOT NULL DEFAULT 0,
  total_node_count     INT NOT NULL DEFAULT 0,
  impact_ratio         DECIMAL(6,4) NOT NULL DEFAULT 0,
  summary              VARCHAR(500),
  analysis_time        DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pa_project ON propagation_analysis (project_id);

CREATE TABLE IF NOT EXISTS analysis_fault_source (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  analysis_id BIGINT NOT NULL,
  fault_id    BIGINT,
  node_id     BIGINT,
  node_name   VARCHAR(100),
  fault_type  VARCHAR(30),
  severity    VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_afs_analysis ON analysis_fault_source (analysis_id);

CREATE TABLE IF NOT EXISTS analysis_affected_node (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  analysis_id     BIGINT NOT NULL,
  node_id         BIGINT,
  node_name       VARCHAR(100),
  impact_level    VARCHAR(20),
  distance        INT,
  source_fault_id BIGINT,
  source_node_id  BIGINT
);
CREATE INDEX IF NOT EXISTS idx_aan_analysis ON analysis_affected_node (analysis_id);

CREATE TABLE IF NOT EXISTS analysis_path_step (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  affected_node_id BIGINT NOT NULL,
  step_index       INT,
  node_id          BIGINT,
  node_name        VARCHAR(100),
  edge_id          BIGINT,
  caller_port      BIGINT,
  callee_port      BIGINT
);
CREATE INDEX IF NOT EXISTS idx_aps_affected ON analysis_path_step (affected_node_id);

CREATE TABLE IF NOT EXISTS recovery_plan (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id            BIGINT NOT NULL,
  analysis_id           BIGINT,
  recommended_type      VARCHAR(30),
  recommended_name      VARCHAR(50),
  recommendation_reason VARCHAR(600),
  weights_json          VARCHAR(300),
  estimation_notice     VARCHAR(600),
  created_at            DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rp_project ON recovery_plan (project_id);

CREATE TABLE IF NOT EXISTS recovery_strategy (
  id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id                    BIGINT NOT NULL,
  strategy_type              VARCHAR(30),
  strategy_name              VARCHAR(50),
  applicable                 TINYINT NOT NULL DEFAULT 0,
  inapplicable_reason        VARCHAR(400),
  estimated_recovery_seconds INT,
  impact_after_count         INT,
  impact_ratio_after         DECIMAL(6,4),
  cost_level                 VARCHAR(10),
  risk_level                 VARCHAR(10),
  score                      DECIMAL(6,2),
  breakdown_json             VARCHAR(500),
  redundancy_group_id        BIGINT,
  redundancy_group_name      VARCHAR(100),
  primary_node_id            BIGINT,
  selected_standby_node_id   BIGINT,
  selected_standby_name      VARCHAR(100),
  selection_reason           VARCHAR(300),
  candidates_json            VARCHAR(1500),
  checks_json                VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS idx_rs_plan ON recovery_strategy (plan_id);
-- v2：时间窗与级联结论落库（已有库平滑升级）
ALTER TABLE propagation_analysis ADD COLUMN IF NOT EXISTS window_seconds INT;
ALTER TABLE propagation_analysis ADD COLUMN IF NOT EXISTS cascade_summary VARCHAR(500);
