-- Add structured follow-up results and manager review fields to an existing crm_system.
-- Safe to rerun. MySQL DDL commits implicitly; this migration preserves existing records.
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

SET @crm_follow_columns = CONCAT(
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='result_code')=0,
       ', ADD COLUMN result_code VARCHAR(32) NULL AFTER substantive_result', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='next_action')=0,
       ', ADD COLUMN next_action VARCHAR(500) NULL AFTER result_code', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='review_status')=0,
       ', ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT ''NOT_REQUIRED'' AFTER is_effective', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='reviewer_id')=0,
       ', ADD COLUMN reviewer_id BIGINT UNSIGNED NULL AFTER review_status', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='reviewed_at')=0,
       ', ADD COLUMN reviewed_at DATETIME(3) NULL AFTER reviewer_id', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='review_reason')=0,
       ', ADD COLUMN review_reason VARCHAR(500) NULL AFTER reviewed_at', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND column_name='version')=0,
       ', ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER created_at', '')
);
SET @crm_ddl = IF(@crm_follow_columns='', 'SELECT 1', CONCAT('ALTER TABLE crm_follow_record ', SUBSTRING(@crm_follow_columns, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_follow_indexes = CONCAT(
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_follow_record' AND index_name='idx_follow_review_queue')=0,
       ', ADD INDEX idx_follow_review_queue (review_status, customer_id, followed_at)', ''),
    IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='crm_follow_record' AND constraint_name='fk_follow_reviewer' AND constraint_type='FOREIGN KEY')=0,
       ', ADD CONSTRAINT fk_follow_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id) ON DELETE SET NULL ON UPDATE RESTRICT', '')
);
SET @crm_ddl = IF(@crm_follow_indexes='', 'SELECT 1', CONCAT('ALTER TABLE crm_follow_record ', SUBSTRING(@crm_follow_indexes, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SELECT column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='crm_follow_record'
  AND column_name IN ('result_code','next_action','review_status','reviewer_id','reviewed_at','review_reason','version')
ORDER BY ordinal_position;
