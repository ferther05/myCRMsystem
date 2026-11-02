-- 用户确认自动回收常开。只启用配置，不立即扫描或改变客户归属。
-- 用一次性迁移标记保证重跑不会覆盖管理员后续的应急暂停决定。
USE crm_system;
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,enabled)
VALUES('PARAM','crm.recycle','enabled','true','公海自动回收开关',1)
ON DUPLICATE KEY UPDATE id=id;
INSERT IGNORE INTO sys_config(config_type,config_group,config_key,config_value,display_name,enabled)
VALUES('PARAM','crm.recycle','always_on_v1','applied','自动回收常开迁移标记',1);
SET @crm_apply_always_on=ROW_COUNT();
INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,occurred_at,reason)
SELECT 'SYSTEM_CONFIG',id,'RECYCLE_SETTING_CHANGED',NULL,NOW(),'用户确认：自动回收默认常开，每10分钟检查；管理员保留应急暂停'
FROM sys_config WHERE config_group='crm.recycle' AND config_key='enabled' AND @crm_apply_always_on=1 AND config_value<>'true';
UPDATE sys_config SET config_value='true',version=version+1
WHERE config_type='PARAM' AND config_group='crm.recycle' AND config_key='enabled' AND @crm_apply_always_on=1 AND config_value<>'true';
COMMIT;
