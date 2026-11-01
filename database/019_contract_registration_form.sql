-- 合同草稿为在线登记表，不依赖草稿文件；保留既有附件、历史和核验事实。

USE crm_system;

SET NAMES utf8mb4;

SET time_zone = '+08:00';

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='our_contact_name')=0, 'ALTER TABLE crm_contract ADD COLUMN our_contact_name VARCHAR(64) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='our_contact_phone')=0, 'ALTER TABLE crm_contract ADD COLUMN our_contact_phone VARCHAR(40) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='counterparty_contact_name')=0, 'ALTER TABLE crm_contract ADD COLUMN counterparty_contact_name VARCHAR(64) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='counterparty_contact_phone')=0, 'ALTER TABLE crm_contract ADD COLUMN counterparty_contact_phone VARCHAR(40) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='our_address')=0, 'ALTER TABLE crm_contract ADD COLUMN our_address VARCHAR(255) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='counterparty_address')=0, 'ALTER TABLE crm_contract ADD COLUMN counterparty_address VARCHAR(255) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='our_credit_code')=0, 'ALTER TABLE crm_contract ADD COLUMN our_credit_code VARCHAR(18) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='counterparty_credit_code')=0, 'ALTER TABLE crm_contract ADD COLUMN counterparty_credit_code VARCHAR(18) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='payment_terms')=0, 'ALTER TABLE crm_contract ADD COLUMN payment_terms VARCHAR(1000) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='delivery_terms')=0, 'ALTER TABLE crm_contract ADD COLUMN delivery_terms VARCHAR(1000) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='planned_signed_at')=0, 'ALTER TABLE crm_contract ADD COLUMN planned_signed_at DATE NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='performance_start_date')=0, 'ALTER TABLE crm_contract ADD COLUMN performance_start_date DATE NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='registration_approved_at')=0, 'ALTER TABLE crm_contract ADD COLUMN registration_approved_at DATETIME(3) NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contract' AND column_name='registration_approved_by')=0, 'ALTER TABLE crm_contract ADD COLUMN registration_approved_by BIGINT UNSIGNED NULL', 'SELECT 1');

PREPARE crm_stmt FROM @crm_ddl;

EXECUTE crm_stmt;

DEALLOCATE PREPARE crm_stmt;

START TRANSACTION;

-- 兼容已经完成旧草稿核验的登记；不为未审核草稿伪造批准。

UPDATE crm_contract c SET registration_approved_at=COALESCE((SELECT MAX(h.changed_at) FROM crm_contract_signing_history h WHERE h.contract_id=c.id AND h.to_progress='SIGNED_UPLOAD' AND h.from_progress='DRAFT_REVIEW'),c.updated_at) WHERE registration_approved_at IS NULL AND (signing_progress IN ('SIGNED_UPLOAD','FILE_REVIEW','FILE_CONFIRMED') OR resume_signing_progress IN ('SIGNED_UPLOAD','FILE_REVIEW','FILE_CONFIRMED'));

COMMIT;
