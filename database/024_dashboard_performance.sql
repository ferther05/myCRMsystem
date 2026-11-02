-- 看板统计快照：保留业务发生时的业绩归属，不新增业务表。
USE crm_system;
SET NAMES utf8mb4;
SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='performance_confirmed_at')=0,
'ALTER TABLE crm_contract ADD COLUMN performance_confirmed_at DATETIME(3) NULL, ADD COLUMN performance_amount DECIMAL(18,2) NULL, ADD COLUMN performance_legacy TINYINT NOT NULL DEFAULT 0, ADD INDEX idx_contract_performance(performance_confirmed_at,performance_owner_id)', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl; EXECUTE crm_stmt; DEALLOCATE PREPARE crm_stmt;
SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_payment_record' AND column_name='performance_owner_id')=0,
'ALTER TABLE crm_payment_record ADD COLUMN performance_owner_id BIGINT UNSIGNED NULL, ADD COLUMN performance_team_id BIGINT UNSIGNED NULL, ADD COLUMN performance_legacy TINYINT NOT NULL DEFAULT 0, ADD INDEX idx_receipt_performance(received_at,performance_owner_id)', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl; EXECUTE crm_stmt; DEALLOCATE PREPARE crm_stmt;
-- 旧记录不能凭当前人员关系伪造历史；优先还原首次确认日期，归属沿用合同记录并标注补录。
UPDATE crm_contract c SET performance_owner_id=COALESCE(c.performance_owner_id,c.owner_id),performance_team_id=COALESCE(c.performance_team_id,c.team_id),
 performance_confirmed_at=COALESCE((SELECT MIN(h.changed_at) FROM crm_contract_signing_history h WHERE h.contract_id=c.id AND h.to_progress IN ('FILE_CONFIRMED','BOTH_SIGNED')),c.signed_at,c.updated_at),
 performance_amount=c.contract_amount,performance_legacy=1
WHERE c.performance_confirmed_at IS NULL AND c.contract_status IN ('SIGNED','PERFORMING','SETTLED','TERMINATED');
UPDATE crm_payment_record r JOIN crm_contract c ON c.id=r.contract_id
SET r.performance_owner_id=c.owner_id,r.performance_team_id=c.team_id,r.performance_legacy=1
WHERE r.performance_owner_id IS NULL;
