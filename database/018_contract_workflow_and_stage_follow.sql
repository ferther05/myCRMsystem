-- 五步合同材料核验、暂停恢复、当前阶段已核验跟进；保留全部历史文件与原审批事实。
-- 先备份。DDL 隐式提交；可重复执行。旧未审批草稿回到草稿生成，不伪造草稿核验。
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

SET @crm_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_opportunity' AND column_name='stage_seq')=0,
 'ALTER TABLE crm_opportunity ADD COLUMN stage_seq BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='opportunity_stage_code')=0,
 'ALTER TABLE crm_follow_record ADD COLUMN opportunity_stage_code VARCHAR(32) NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='opportunity_stage_seq')=0,
 'ALTER TABLE crm_follow_record ADD COLUMN opportunity_stage_seq BIGINT NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='resume_signing_progress')=0,
 'ALTER TABLE crm_contract ADD COLUMN resume_signing_progress VARCHAR(32) NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND index_name='idx_follow_stage_review')=0,
 'ALTER TABLE crm_follow_record ADD INDEX idx_follow_stage_review (opportunity_id,opportunity_stage_seq,opportunity_stage_code,review_status,is_effective)', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;
ALTER TABLE crm_contract ALTER COLUMN signing_progress SET DEFAULT 'DRAFT_GENERATED';

START TRANSACTION;
-- 仅当旧记录确实提交于当前阶段开始后才归入第 0 轮；未证明的旧跟进不用于新门槛。
UPDATE crm_follow_record f JOIN crm_opportunity o ON o.id=f.opportunity_id
SET f.opportunity_stage_code=o.stage_code,f.opportunity_stage_seq=0
WHERE f.opportunity_stage_code IS NULL AND o.stage_seq=0
 AND f.created_at>=COALESCE(o.stage_changed_at,o.created_at)
 AND f.followed_at>=COALESCE(o.stage_changed_at,o.created_at);

-- 追加迁移说明，不覆盖原历史。待签署的旧草稿回到首步补做草稿检验；旧签署待审保留待审。
INSERT INTO crm_contract_signing_history(contract_id,from_progress,to_progress,operator_name,operator_role,changed_at,reason,is_correction)
SELECT c.id,c.signing_progress,
 CASE WHEN c.signing_progress IN ('EXCEPTION','WITHDRAWN') THEN c.signing_progress
 WHEN c.contract_status IN ('SIGNED','PERFORMING','SETTLED','TERMINATED') THEN 'FILE_CONFIRMED'
 WHEN c.contract_status='PENDING_REVIEW' THEN 'FILE_REVIEW' ELSE 'DRAFT_GENERATED' END,
 '系统迁移','SYSTEM',CURRENT_TIMESTAMP(3),'20261030五步流程迁移：保留原签署审批；旧未审批草稿补做草稿检验',0
FROM crm_contract c
WHERE c.signing_progress IN ('INTERNAL_HANDLING','WAIT_OUR_SIGN','WAIT_COUNTERPARTY_SIGN','BOTH_SIGNED')
 OR (c.signing_progress IN ('EXCEPTION','WITHDRAWN') AND c.resume_signing_progress IS NULL);

UPDATE crm_contract SET resume_signing_progress='DRAFT_GENERATED',version=version+1
WHERE signing_progress IN ('EXCEPTION','WITHDRAWN') AND resume_signing_progress IS NULL;
UPDATE crm_contract SET signing_progress=CASE
 WHEN contract_status IN ('SIGNED','PERFORMING','SETTLED','TERMINATED') THEN 'FILE_CONFIRMED'
 WHEN contract_status='PENDING_REVIEW' THEN 'FILE_REVIEW' ELSE 'DRAFT_GENERATED' END,
 version=version+1
WHERE signing_progress IN ('INTERNAL_HANDLING','WAIT_OUR_SIGN','WAIT_COUNTERPARTY_SIGN','BOTH_SIGNED');

-- 新增中文选项；重复执行不更改管理员对既有选项的配置。
INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled) VALUES
('DICT','customer.industry','EDUCATION','EDUCATION','教育培训',100,1),
('DICT','customer.industry','HEALTHCARE','HEALTHCARE','医疗健康',110,1),
('DICT','customer.industry','FINANCE','FINANCE','金融保险',120,1),
('DICT','customer.industry','CONSTRUCTION','CONSTRUCTION','建筑工程',130,1),
('DICT','customer.industry','LOGISTICS','LOGISTICS','物流运输',140,1),
('DICT','customer.industry','ENERGY','ENERGY','能源环保',150,1),
('DICT','customer.industry','AGRICULTURE','AGRICULTURE','农业食品',160,1),
('DICT','customer.industry','HOSPITALITY','HOSPITALITY','餐饮文旅',170,1),
('DICT','customer.industry','INTERNET','INTERNET','互联网与电商',180,1),
('DICT','customer.industry','GOVERNMENT','GOVERNMENT','政务与公共服务',190,1),
('DICT','customer.industry','MEDIA','MEDIA','文化传媒',200,1),
('DICT','customer.industry','REAL_ESTATE','REAL_ESTATE','房地产与物业',210,1),
('DICT','customer.tag','KEY_ACCOUNT','KEY_ACCOUNT','重点客户',100,1),
('DICT','customer.tag','FAST_DECISION','FAST_DECISION','决策较快',110,1),
('DICT','customer.tag','LONG_CYCLE','LONG_CYCLE','长周期决策',120,1),
('DICT','customer.tag','BUDGET_CONFIRMED','BUDGET_CONFIRMED','预算已明确',130,1),
('DICT','customer.tag','TECH_DRIVEN','TECH_DRIVEN','技术导向',140,1),
('DICT','customer.tag','SERVICE_FOCUSED','SERVICE_FOCUSED','重视服务',150,1),
('DICT','customer.tag','MULTI_DEPARTMENT','MULTI_DEPARTMENT','多部门协同',160,1),
('DICT','customer.tag','CROSS_REGION','CROSS_REGION','跨区域业务',170,1),
('DICT','customer.tag','RENEWAL','RENEWAL','续约机会',180,1),
('DICT','customer.tag','UPSELL','UPSELL','增购机会',190,1),
('DICT','customer.tag','REFERRAL','REFERRAL','转介绍',200,1),
('DICT','customer.tag','STRATEGIC','STRATEGIC','战略合作',210,1),
('DICT','customer.tag','COMPETITOR','COMPETITOR','存在竞品',220,1),
('DICT','customer.tag','NEEDS_DEVELOPMENT','NEEDS_DEVELOPMENT','待培育',230,1)
ON DUPLICATE KEY UPDATE id=id;
COMMIT;
