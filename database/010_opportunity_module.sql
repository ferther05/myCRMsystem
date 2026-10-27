-- 商机任务 9-14 增量迁移。
-- 兼容已执行 001-009 的 crm_system；可重复执行，不清空业务数据。
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 自动任务以 NULL 操作人表示系统身份，手工执行保留管理员 ID。
ALTER TABLE crm_operation_log MODIFY COLUMN operator_id BIGINT UNSIGNED NULL;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='stagnation_days')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN stagnation_days INT UNSIGNED NOT NULL DEFAULT 20 AFTER activity_status', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='grace_days')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN grace_days INT UNSIGNED NOT NULL DEFAULT 10 AFTER stagnation_days', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='threshold_customized')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN threshold_customized TINYINT(1) NOT NULL DEFAULT 0 AFTER grace_days', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='stage_changed_at')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN stage_changed_at DATETIME(3) NULL AFTER last_effective_progress_at', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='close_pending_at')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN close_pending_at DATETIME(3) NULL AFTER stage_changed_at', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='terminal_at')=0,
  'ALTER TABLE crm_opportunity ADD COLUMN terminal_at DATETIME(3) NULL AFTER close_pending_at', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND index_name='idx_opportunity_activity_scan')=0,
  'ALTER TABLE crm_opportunity ADD KEY idx_opportunity_activity_scan (activity_status,stage_code,stage_changed_at,last_effective_progress_at)', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS crm_opportunity_stage_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    opportunity_id BIGINT UNSIGNED NOT NULL,
    from_stage VARCHAR(32) NULL,
    to_stage VARCHAR(32) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    operator_id BIGINT UNSIGNED NULL COMMENT 'NULL表示系统自动流转',
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_opportunity_stage_log (opportunity_id, occurred_at),
    CONSTRAINT fk_stage_log_opportunity FOREIGN KEY (opportunity_id) REFERENCES crm_opportunity(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_stage_log_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商机阶段流转历史';

UPDATE crm_opportunity
SET stage_changed_at=COALESCE(stage_changed_at, last_effective_progress_at, created_at),
    last_effective_progress_at=COALESCE(last_effective_progress_at, created_at),
    stagnation_days=CASE stage_code WHEN 'NEW' THEN 20 WHEN 'FOLLOWING' THEN 30 WHEN 'QUOTED' THEN 45 WHEN 'DEAL' THEN 45 ELSE stagnation_days END,
    grace_days=CASE WHEN stage_code IN ('QUOTED','DEAL') THEN 15 ELSE 10 END
WHERE stage_changed_at IS NULL OR last_effective_progress_at IS NULL;

UPDATE crm_opportunity SET grace_days=15 WHERE threshold_customized=0 AND stage_code IN ('QUOTED','DEAL') AND grace_days=10;

INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
SELECT 'PARAM','crm.opportunity','max_collaborators','3','单条商机最多协作人数',10,1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_type='PARAM' AND config_group='crm.opportunity' AND config_key='max_collaborators');
INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
SELECT 'PARAM','crm.opportunity','default_grace_days','10','商机停滞默认宽限天数',20,1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_type='PARAM' AND config_group='crm.opportunity' AND config_key='default_grace_days');

SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='crm_opportunity_stage_log';
