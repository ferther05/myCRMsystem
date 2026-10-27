-- 2026-10-22 商机阶段由业务事实自动推进；不变更已有商机阶段或记录。
-- 先备份，使用管理员执行。可重复运行。MySQL DDL 会隐式提交。
USE crm_system;
ALTER TABLE crm_opportunity_stage_log
    MODIFY COLUMN operator_id BIGINT UNSIGNED NULL COMMENT 'NULL表示系统自动流转';
SELECT column_name, is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='crm_opportunity_stage_log' AND column_name='operator_id';
