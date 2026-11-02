-- 合同分期计划、到账核销、误录作废及逾期提醒字段。
-- 执行前备份 crm_system；对已初始化数据库安全重复执行。
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_payment_period' AND column_name='trigger_node')=0,
 'ALTER TABLE crm_payment_period ADD COLUMN trigger_node VARCHAR(24) NOT NULL DEFAULT ''OTHER''', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_payment_record' AND column_name='difference_description')=0,
 'ALTER TABLE crm_payment_record ADD COLUMN difference_description VARCHAR(500) NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_payment_record' AND column_name='voided_at')=0,
 'ALTER TABLE crm_payment_record ADD COLUMN voided_at DATETIME(3) NULL, ADD COLUMN voided_by BIGINT UNSIGNED NULL, ADD COLUMN void_reason VARCHAR(500) NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_fk=IF((SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='crm_payment_record' AND constraint_name='fk_payment_record_voided_by')=0,
 'ALTER TABLE crm_payment_record ADD CONSTRAINT fk_payment_record_voided_by FOREIGN KEY (voided_by) REFERENCES sys_user(id) ON DELETE SET NULL ON UPDATE RESTRICT', 'SELECT 1');
PREPARE crm_stmt FROM @crm_fk;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;
