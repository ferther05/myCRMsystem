-- Optional local demonstration data, not a schema migration.
-- Requires the existing admin, east.manager/east.sales, south.manager/south.sales.
-- Use a MySQL client that stops on errors; rollback the transaction on any error.
-- Repeating this script skips existing demo rows and preserves their edits.
-- NEW = pending first contact; PROTECTED = protected by an active opportunity.
-- All companies, contacts, and telephone numbers below are fictional test data.
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;
SELECT id FROM sys_user
WHERE username IN ('admin','east.manager','east.sales','south.manager','south.sales')
ORDER BY id FOR UPDATE;

SET @demo_now = NOW(3);
SET @demo_batch = 'CRM-DEBUG-AUTH-V1';
SET @demo_admin = (SELECT id FROM sys_user WHERE username='admin' AND role_code='ADMIN' AND account_status='ACTIVE');
SET @demo_em = (SELECT u.id FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.username='east.manager' AND u.role_code='MANAGER' AND u.account_status='ACTIVE' AND t.team_code='EAST' AND t.enabled=1);
SET @demo_es = (SELECT u.id FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.username='east.sales' AND u.role_code='SPECIALIST' AND u.account_status='ACTIVE' AND t.team_code='EAST' AND t.enabled=1);
SET @demo_sm = (SELECT u.id FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.username='south.manager' AND u.role_code='MANAGER' AND u.account_status='ACTIVE' AND t.team_code='SOUTH' AND t.enabled=1);
SET @demo_ss = (SELECT u.id FROM sys_user u JOIN sys_team t ON t.id=u.team_id WHERE u.username='south.sales' AND u.role_code='SPECIALIST' AND u.account_status='ACTIVE' AND t.team_code='SOUTH' AND t.enabled=1);
SET @demo_east = (SELECT team_id FROM sys_user WHERE id=@demo_em);
SET @demo_south = (SELECT team_id FROM sys_user WHERE id=@demo_sm);

INSERT INTO crm_customer
    (customer_name,normalized_name,primary_phone,normalized_primary_phone,
     source_code,industry_code,customer_level,tags_json,owner_id,team_id,
     customer_status,sea_type,last_effective_follow_at,first_contact_deadline_at,
     ownership_seq,created_by,updated_by,created_at,updated_at)
SELECT s.customer_name,s.customer_name,s.phone,s.phone,'DEBUG_SEED',s.industry,'NORMAL',
       JSON_ARRAY(@demo_batch,s.demo_key,'演示数据'),s.owner_id,s.team_id,s.customer_status,s.sea_type,
       IF(s.customer_status='PROTECTED',DATE_SUB(@demo_now,INTERVAL 1 HOUR),NULL),
       IF(s.sea_type='PRIVATE' AND s.customer_status='NEW',DATE_ADD(@demo_now,INTERVAL 1 DAY),NULL),
       IF(s.sea_type='PRIVATE',1,0),@demo_admin,@demo_admin,
       DATE_SUB(@demo_now,INTERVAL 2 DAY),DATE_SUB(@demo_now,INTERVAL 1 HOUR)
FROM (
    SELECT 'EM01' demo_key,'演示·华东启明科技有限公司' customer_name,'19900001001' phone,'TECH' industry,@demo_em owner_id,@demo_east team_id,'NEW' customer_status,'PRIVATE' sea_type
    UNION ALL SELECT 'EM02','演示·华东远航设备有限公司','19900001002','MANUFACTURING',@demo_em,@demo_east,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'ES01','演示·华东青禾商贸有限公司','19900001003','TRADE',@demo_es,@demo_east,'NEW','PRIVATE'
    UNION ALL SELECT 'ES02','演示·华东星云软件有限公司','19900001004','TECH',@demo_es,@demo_east,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'ES03','演示·华东嘉禾供应链有限公司','19900001005','SERVICE',@demo_es,@demo_east,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'ES04','演示·华东蓝鲸咨询有限公司','19900001006','SERVICE',@demo_es,@demo_east,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'SM01','演示·华南晨曦科技有限公司','19900002001','TECH',@demo_sm,@demo_south,'NEW','PRIVATE'
    UNION ALL SELECT 'SM02','演示·华南海川设备有限公司','19900002002','MANUFACTURING',@demo_sm,@demo_south,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'SS01','演示·华南绿洲商贸有限公司','19900002003','TRADE',@demo_ss,@demo_south,'NEW','PRIVATE'
    UNION ALL SELECT 'SS02','演示·华南凌云软件有限公司','19900002004','TECH',@demo_ss,@demo_south,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'SS03','演示·华南顺达供应链有限公司','19900002005','SERVICE',@demo_ss,@demo_south,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'SS04','演示·华南海豚咨询有限公司','19900002006','SERVICE',@demo_ss,@demo_south,'PROTECTED','PRIVATE'
    UNION ALL SELECT 'P01','演示·公海云帆科技有限公司','19900003001','TECH',NULL,NULL,'NEW','PUBLIC'
    UNION ALL SELECT 'P02','演示·公海宏远设备有限公司','19900003002','MANUFACTURING',NULL,NULL,'NEW','PUBLIC'
    UNION ALL SELECT 'P03','演示·公海短号测试客户','12345','SERVICE',NULL,NULL,'NEW','PUBLIC'
    UNION ALL SELECT 'P04','演示·公海未填电话客户',NULL,'TRADE',NULL,NULL,'NEW','PUBLIC'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM crm_customer c
    WHERE JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
      AND JSON_CONTAINS(c.tags_json,JSON_QUOTE(s.demo_key))
);

INSERT INTO crm_contact
    (customer_id,contact_name,job_title,phone,email,decision_role,is_primary,created_by,created_at,updated_at)
SELECT c.id,CONCAT('演示联系人-',JSON_UNQUOTE(JSON_EXTRACT(c.tags_json,'$[1]'))),
       '业务对接人',c.primary_phone,
       CONCAT(LOWER(JSON_UNQUOTE(JSON_EXTRACT(c.tags_json,'$[1]'))),'@crm-debug.example'),
       'CONTACT',1,@demo_admin,c.created_at,c.created_at
FROM crm_customer c
WHERE JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
  AND c.sea_type='PRIVATE'
  AND NOT EXISTS (SELECT 1 FROM crm_contact p WHERE p.customer_id=c.id AND p.is_primary=1);

INSERT INTO crm_opportunity
    (customer_id,opportunity_name,owner_id,team_id,stage_code,activity_status,
     expected_amount,expected_close_date,last_effective_progress_at,created_by,created_at,updated_at)
SELECT c.id,s.opportunity_name,c.owner_id,c.team_id,'NEW','ACTIVE',s.amount,
       DATE_ADD(DATE(@demo_now),INTERVAL s.close_days DAY),
       DATE_SUB(@demo_now,INTERVAL 1 HOUR),c.owner_id,
       DATE_SUB(@demo_now,INTERVAL 2 HOUR),DATE_SUB(@demo_now,INTERVAL 1 HOUR)
FROM (
    SELECT 'EM02' demo_key,'演示·华东设备采购项目' opportunity_name,CAST(120000.00 AS DECIMAL(18,2)) amount,30 close_days
    UNION ALL SELECT 'ES02','演示·华东CRM软件采购',68000.00,20
    UNION ALL SELECT 'ES03','演示·华东供应链数字化',156000.00,45
    UNION ALL SELECT 'ES04','演示·华东销售咨询服务',32000.00,15
    UNION ALL SELECT 'SM02','演示·华南设备采购项目',98000.00,30
    UNION ALL SELECT 'SS02','演示·华南CRM软件采购',72000.00,25
    UNION ALL SELECT 'SS03','演示·华南供应链数字化',180000.00,50
    UNION ALL SELECT 'SS04','演示·华南销售咨询服务',45000.00,15
) s
JOIN crm_customer c ON JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
                  AND JSON_CONTAINS(c.tags_json,JSON_QUOTE(s.demo_key))
WHERE NOT EXISTS (SELECT 1 FROM crm_opportunity o WHERE o.customer_id=c.id AND o.opportunity_name=s.opportunity_name);

INSERT INTO crm_follow_record
    (customer_id,opportunity_id,contact_id,follow_user_id,record_type,follow_method,
     content,substantive_result,next_action_at,is_effective,occurrence_key,followed_at,created_at)
SELECT c.id,o.id,p.id,o.owner_id,'FOLLOW','PHONE',
       '演示数据：已与业务联系人沟通采购需求、预算及后续安排。',
       '演示结果：联系人确认需求与预算，约定下一次方案沟通。',
       DATE_ADD(@demo_now,INTERVAL 2 DAY),1,CONCAT('debug-auth-v1:follow:',o.id),
       o.last_effective_progress_at,o.last_effective_progress_at
FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id
JOIN crm_contact p ON p.customer_id=c.id AND p.is_primary=1
WHERE JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
  AND o.opportunity_name LIKE '演示·%'
  AND NOT EXISTS (SELECT 1 FROM crm_follow_record f WHERE f.customer_id=c.id AND f.occurrence_key=CONCAT('debug-auth-v1:follow:',o.id));

INSERT INTO crm_operation_log
    (biz_type,biz_id,action_code,operator_id,occurred_at,request_id,ownership_seq,
     new_owner_id,new_team_id,after_json,reason)
SELECT 'CUSTOMER',c.id,'DEMO_SEEDED',@demo_admin,@demo_now,
       CONCAT('debug-auth-v1:customer:',JSON_UNQUOTE(JSON_EXTRACT(c.tags_json,'$[1]'))),
       c.ownership_seq,c.owner_id,c.team_id,
       JSON_OBJECT('demoBatch',@demo_batch,'customerName',c.customer_name,'seaType',c.sea_type,
                   'ownerId',c.owner_id,'teamId',c.team_id,'status',c.customer_status),
       '用户授权导入第一模块调试用演示数据；非实际客户创建、领取或交接。'
FROM crm_customer c
WHERE JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
  AND NOT EXISTS (SELECT 1 FROM crm_operation_log l WHERE l.request_id=CONCAT('debug-auth-v1:customer:',JSON_UNQUOTE(JSON_EXTRACT(c.tags_json,'$[1]'))));

INSERT INTO crm_operation_log
    (biz_type,biz_id,action_code,operator_id,occurred_at,request_id,after_json,reason)
SELECT 'OPPORTUNITY',o.id,'DEMO_SEEDED',@demo_admin,@demo_now,CONCAT('debug-auth-v1:opportunity:',o.id),
       JSON_OBJECT('demoBatch',@demo_batch,'customerId',o.customer_id,'stage',o.stage_code,
                   'activity',o.activity_status,'expectedAmount',o.expected_amount),
       '用户授权导入第一模块调试用演示商机；非实际商机阶段推进。'
FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id
WHERE JSON_CONTAINS(COALESCE(c.tags_json,JSON_ARRAY()),JSON_QUOTE(@demo_batch))
  AND o.opportunity_name LIKE '演示·%'
  AND NOT EXISTS (SELECT 1 FROM crm_operation_log l WHERE l.request_id=CONCAT('debug-auth-v1:opportunity:',o.id));

-- Maintain ownership counts without changing credentials, roles, or JWT versions.
UPDATE sys_user u
SET current_hold_count=(SELECT COUNT(*) FROM crm_customer c WHERE c.owner_id=u.id AND c.sea_type='PRIVATE')
WHERE u.id IN (@demo_em,@demo_es,@demo_sm,@demo_ss)
  AND u.current_hold_count<>(SELECT COUNT(*) FROM crm_customer c WHERE c.owner_id=u.id AND c.sea_type='PRIVATE');

COMMIT;
