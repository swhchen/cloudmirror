-- 已有 MySQL 库升级到 v2（时间窗 / 级联落库），新库由 init.sql 直接建好
ALTER TABLE propagation_analysis
  ADD COLUMN window_seconds INT NULL AFTER summary,
  ADD COLUMN cascade_summary VARCHAR(500) NULL AFTER window_seconds;
