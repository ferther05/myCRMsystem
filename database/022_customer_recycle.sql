-- 客户自动回收：只增加判定与预警字段，默认开启，不变更客户归属。
USE crm_system;
SET NAMES utf8mb4;
SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='ownership_started_at')=0,
 'ALTER TABLE crm_customer ADD COLUMN ownership_started_at DATETIME(3) NULL, ADD COLUMN recycle_warning_key VARCHAR(160) NULL, ADD COLUMN recycle_warned_at DATETIME(3) NULL, ADD COLUMN recycle_extension_key VARCHAR(160) NULL, ADD COLUMN recycle_extension_until DATETIME(3) NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;
INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,enabled)
VALUES('PARAM','crm.recycle','enabled','true','公海自动回收开关',1)
ON DUPLICATE KEY UPDATE id=id;
