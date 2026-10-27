-- 合同任务15-16第一切片：合同登记草稿、签约资料版本和经理核验。
-- 只增加登记字段，不生成合同正文，不清空已有业务数据；可重复执行。
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='external_contract_no')=0,
  'ALTER TABLE crm_contract ADD COLUMN external_contract_no VARCHAR(64) NULL AFTER contract_no', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='business_type')=0,
  'ALTER TABLE crm_contract ADD COLUMN business_type VARCHAR(64) NULL AFTER contract_name', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='our_party_name')=0,
  'ALTER TABLE crm_contract ADD COLUMN our_party_name VARCHAR(160) NULL AFTER business_type', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='counterparty_name')=0,
  'ALTER TABLE crm_contract ADD COLUMN counterparty_name VARCHAR(160) NULL AFTER our_party_name', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='description')=0,
  'ALTER TABLE crm_contract ADD COLUMN description VARCHAR(1000) NULL AFTER counterparty_name', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_contract' AND index_name='uk_contract_external_no')=0,
  'ALTER TABLE crm_contract ADD UNIQUE KEY uk_contract_external_no (external_contract_no)', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='crm_contract'
  AND column_name IN ('external_contract_no','business_type','our_party_name','counterparty_name','description')
ORDER BY ordinal_position;
