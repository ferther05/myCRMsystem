-- Upgrade an existing 001-004 crm_system database for customer profile/classification.
-- Safe to rerun: existing columns, constraints, and indexes are detected before DDL.
-- Back up the database and stop the backend before applying; MySQL DDL commits implicitly.
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- Add columns that are not present in the original schema. Ordering also supports a
-- partially upgraded database where some of these fields already exist.
SET @crm_customer_columns = CONCAT(
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='region')=0,
       ', ADD COLUMN region VARCHAR(100) NULL AFTER industry_code', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='company_size')=0,
       ', ADD COLUMN company_size VARCHAR(32) NULL AFTER region', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='remark')=0,
       ', ADD COLUMN remark VARCHAR(1000) NULL AFTER company_size', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='deleted')=0,
       ', ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='deleted_at')=0,
       ', ADD COLUMN deleted_at DATETIME(3) NULL AFTER deleted', '')
);
SET @crm_ddl = IF(@crm_customer_columns='', 'SELECT 1',
    CONCAT('ALTER TABLE crm_customer ', SUBSTRING(@crm_customer_columns, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

-- Generated values are NULL for deleted rows so the unique indexes cover active rows only.
SET @crm_customer_generated = CONCAT(
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='active_unified_social_credit_code')=0,
       ', ADD COLUMN active_unified_social_credit_code VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN unified_social_credit_code ELSE NULL END) STORED', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='active_normalized_name')=0,
       ', ADD COLUMN active_normalized_name VARCHAR(160) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN normalized_name ELSE NULL END) STORED', ''),
    IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_customer' AND column_name='active_normalized_primary_phone')=0,
       ', ADD COLUMN active_normalized_primary_phone VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN normalized_primary_phone ELSE NULL END) STORED', '')
);
SET @crm_ddl = IF(@crm_customer_generated='', 'SELECT 1',
    CONCAT('ALTER TABLE crm_customer ', SUBSTRING(@crm_customer_generated, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

-- Replace full-table unique keys atomically with active-row unique keys.
SET @crm_customer_constraints = CONCAT(
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_credit_code')>0,
       ', DROP INDEX uk_customer_credit_code', ''),
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_name_phone')>0,
       ', DROP INDEX uk_customer_name_phone', ''),
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_active_credit_code')=0,
       ', ADD UNIQUE KEY uk_customer_active_credit_code (active_unified_social_credit_code)', ''),
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_active_name_phone')=0,
       ', ADD UNIQUE KEY uk_customer_active_name_phone (active_normalized_name,active_normalized_primary_phone)', ''),
    IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='idx_customer_active_scope')=0,
       ', ADD KEY idx_customer_active_scope (deleted,sea_type,team_id,owner_id)', ''),
    IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='crm_customer' AND constraint_name='ck_customer_deleted' AND constraint_type='CHECK')=0,
       ', ADD CONSTRAINT ck_customer_deleted CHECK (deleted IN (0,1))', '')
);
SET @crm_ddl = IF(@crm_customer_constraints='', 'SELECT 1',
    CONCAT('ALTER TABLE crm_customer ', SUBSTRING(@crm_customer_constraints, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

-- A customer has at most one current primary contact; NULL generated values allow all others.
SET @crm_contact_columns = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_contact' AND column_name='primary_customer_id')=0,
    ', ADD COLUMN primary_customer_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN is_primary=1 THEN customer_id ELSE NULL END) STORED',
    ''
);
SET @crm_ddl = IF(@crm_contact_columns='', 'SELECT 1',
    CONCAT('ALTER TABLE crm_contact ', SUBSTRING(@crm_contact_columns, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

SET @crm_contact_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='crm_contact' AND index_name='uk_contact_primary_customer')=0,
    ', ADD UNIQUE KEY uk_contact_primary_customer (primary_customer_id)',
    ''
);
SET @crm_ddl = IF(@crm_contact_index='', 'SELECT 1',
    CONCAT('ALTER TABLE crm_contact ', SUBSTRING(@crm_contact_index, 3)));
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;

-- This existing row serializes concurrent duplicate checks; the dictionary insert is repeatable.
INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
VALUES
('PARAM','crm.customer','write_mutex','1','客户查重写入锁',0,1),
('DICT','customer.source','REFERRAL','REFERRAL','客户转介绍',10,1),
('DICT','customer.source','EXHIBITION','EXHIBITION','展会活动',20,1),
('DICT','customer.source','WEB','WEB','网络咨询',30,1),
('DICT','customer.source','COLD_CALL','COLD_CALL','主动拓客',40,1),
('DICT','customer.source','OTHER','OTHER','其他来源',50,1),
('DICT','customer.source','DEBUG_SEED','DEBUG_SEED','演示数据',90,1),
('DICT','customer.industry','TECH','TECH','软件与科技',10,1),
('DICT','customer.industry','MANUFACTURING','MANUFACTURING','生产制造',20,1),
('DICT','customer.industry','TRADE','TRADE','商贸零售',30,1),
('DICT','customer.industry','SERVICE','SERVICE','企业服务',40,1),
('DICT','customer.industry','OTHER','OTHER','其他行业',50,1),
('DICT','customer.level','KEY','KEY','重点客户',10,1),
('DICT','customer.level','NORMAL','NORMAL','常规客户',20,1),
('DICT','customer.level','WATCH','WATCH','观望客户',30,1),
('DICT','customer.tag','POTENTIAL','POTENTIAL','高潜力',10,1),
('DICT','customer.tag','LONG_TERM','LONG_TERM','长期合作',20,1),
('DICT','customer.tag','PRICE_SENSITIVE','PRICE_SENSITIVE','价格敏感',30,1),
('DICT','customer.tag','LOCAL','LOCAL','本地客户',40,1),
('DICT','customer.tag','NEW_DEMAND','NEW_DEMAND','新增需求',50,1)
ON DUPLICATE KEY UPDATE config_key=VALUES(config_key);

SELECT COUNT(*) AS active_credit_unique_index FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_active_credit_code';
SELECT COUNT(*) AS active_name_phone_unique_index FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='crm_customer' AND index_name='uk_customer_active_name_phone';
SELECT COUNT(*) AS primary_contact_unique_index FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='crm_contact' AND index_name='uk_contact_primary_customer';
