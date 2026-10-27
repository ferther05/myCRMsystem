-- 合成演示数据：指定专员8个私海客户、6个商机；8个无归属公海客户。
-- 通过 tools/seed-specialist-demo.ps1 备份并执行，传入 @crm_demo_username。
-- 前置迁移001-014。整批DML同事务；按固定演示信用代码去重，重复运行保留用户修改。
-- NEW/FOLLOWING/QUOTED/DEAL仅作测试场景，不伪造签署、文件或真实交易。
USE crm_system;
SET NAMES utf8mb4;
SET time_zone='+08:00';
START TRANSACTION;
SELECT id FROM sys_config WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='write_mutex' FOR UPDATE;
SET @crm_demo_now=NOW(3);
SET @crm_demo_batch='CRM-DEMO-CLAIM-20261022';
SET @crm_demo_owner=NULL;
SELECT u.id INTO @crm_demo_owner FROM sys_user u JOIN sys_team t ON t.id=u.team_id
WHERE u.username=@crm_demo_username AND u.role_code='SPECIALIST' AND u.account_status='ACTIVE' AND t.enabled=1 FOR UPDATE;
SET @crm_demo_team=(SELECT team_id FROM sys_user WHERE id=@crm_demo_owner);
SET @crm_demo_admin=NULL;
SELECT id INTO @crm_demo_admin FROM sys_user WHERE username='admin' AND role_code='ADMIN' AND account_status='ACTIVE' FOR UPDATE;
SET @crm_demo_manager=(SELECT id FROM sys_user WHERE team_id=@crm_demo_team AND role_code='MANAGER' AND account_status='ACTIVE' ORDER BY id LIMIT 1);
SET @crm_demo_check=IF(@crm_demo_owner IS NOT NULL AND @crm_demo_admin IS NOT NULL AND @crm_demo_manager IS NOT NULL,'SELECT 1','SELECT crm_demo_required_active_account_missing FROM sys_user');
PREPARE crm_demo_check_stmt FROM @crm_demo_check;
EXECUTE crm_demo_check_stmt;
DEALLOCATE PREPARE crm_demo_check_stmt;

SET @crm_demo_source='[
 {
  "seq": 1,
  "customer_name": "演示·专员1234·青禾咨询",
  "industry": "SERVICE",
  "region": "上海",
  "level_code": "NORMAL",
  "tags": [
   "NEW_DEMAND"
  ],
  "sea": "PRIVATE",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 2,
  "customer_name": "演示·专员1234·远景商贸",
  "industry": "TRADE",
  "region": "浙江杭州",
  "level_code": "WATCH",
  "tags": [
   "PRICE_SENSITIVE",
   "LOCAL"
  ],
  "sea": "PRIVATE",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 3,
  "customer_name": "演示·专员1234·启辰科技",
  "industry": "TECH",
  "region": "上海",
  "level_code": "KEY",
  "tags": [
   "POTENTIAL",
   "NEW_DEMAND"
  ],
  "sea": "PRIVATE",
  "stage": "NEW",
  "amount": 48000
 },
 {
  "seq": 4,
  "customer_name": "演示·专员1234·云桥供应链",
  "industry": "SERVICE",
  "region": "江苏南京",
  "level_code": "NORMAL",
  "tags": [
   "LONG_TERM"
  ],
  "sea": "PRIVATE",
  "stage": "NEW",
  "amount": 78000
 },
 {
  "seq": 5,
  "customer_name": "演示·专员1234·星海软件",
  "industry": "TECH",
  "region": "浙江宁波",
  "level_code": "KEY",
  "tags": [
   "POTENTIAL",
   "LOCAL"
  ],
  "sea": "PRIVATE",
  "stage": "FOLLOWING",
  "amount": 128000
 },
 {
  "seq": 6,
  "customer_name": "演示·专员1234·嘉禾制造",
  "industry": "MANUFACTURING",
  "region": "安徽合肥",
  "level_code": "NORMAL",
  "tags": [
   "NEW_DEMAND",
   "PRICE_SENSITIVE"
  ],
  "sea": "PRIVATE",
  "stage": "FOLLOWING",
  "amount": 260000
 },
 {
  "seq": 7,
  "customer_name": "演示·专员1234·锦程设备",
  "industry": "MANUFACTURING",
  "region": "江苏苏州",
  "level_code": "KEY",
  "tags": [
   "LONG_TERM",
   "NEW_DEMAND"
  ],
  "sea": "PRIVATE",
  "stage": "QUOTED",
  "amount": 98000
 },
 {
  "seq": 8,
  "customer_name": "演示·专员1234·博远服务",
  "industry": "SERVICE",
  "region": "山东济南",
  "level_code": "KEY",
  "tags": [
   "POTENTIAL",
   "LONG_TERM"
  ],
  "sea": "PRIVATE",
  "stage": "DEAL",
  "amount": 320000
 },
 {
  "seq": 9,
  "customer_name": "演示·申领公海·晨光科技",
  "industry": "TECH",
  "region": "上海",
  "level_code": "KEY",
  "tags": [
   "POTENTIAL",
   "NEW_DEMAND"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 10,
  "customer_name": "演示·申领公海·宏达设备",
  "industry": "MANUFACTURING",
  "region": "江苏无锡",
  "level_code": "NORMAL",
  "tags": [
   "LOCAL"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 11,
  "customer_name": "演示·申领公海·新叶商贸",
  "industry": "TRADE",
  "region": "浙江杭州",
  "level_code": "WATCH",
  "tags": [
   "PRICE_SENSITIVE"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 12,
  "customer_name": "演示·申领公海·海岳咨询",
  "industry": "SERVICE",
  "region": "安徽合肥",
  "level_code": "NORMAL",
  "tags": [
   "NEW_DEMAND",
   "LOCAL"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 13,
  "customer_name": "演示·申领公海·锐思软件",
  "industry": "TECH",
  "region": "江苏南京",
  "level_code": "KEY",
  "tags": [
   "POTENTIAL"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 14,
  "customer_name": "演示·申领公海·长青制造",
  "industry": "MANUFACTURING",
  "region": "山东青岛",
  "level_code": "NORMAL",
  "tags": [
   "LONG_TERM"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 15,
  "customer_name": "演示·申领公海·丰泽供应链",
  "industry": "SERVICE",
  "region": "浙江温州",
  "level_code": "WATCH",
  "tags": [
   "PRICE_SENSITIVE",
   "NEW_DEMAND"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 },
 {
  "seq": 16,
  "customer_name": "演示·申领公海·蓝湾贸易",
  "industry": "TRADE",
  "region": "福建厦门",
  "level_code": "KEY",
  "tags": [
   "LOCAL",
   "POTENTIAL"
  ],
  "sea": "PUBLIC",
  "stage": null,
  "amount": 0
 }
]';

-- 同编码的既有数据必须确属本批次；否则中断整批，不能误关联其他业务数据。
SET @crm_demo_check=IF(EXISTS(
SELECT 1 FROM crm_customer c JOIN JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s ON c.unified_social_credit_code=CONCAT('91319920261022',LPAD(s.seq,4,'0'))
WHERE COALESCE(c.remark,'') NOT LIKE CONCAT(@crm_demo_batch,'；%')
),'SELECT crm_demo_credit_code_collision FROM crm_customer','SELECT 1');
PREPARE crm_demo_check_stmt FROM @crm_demo_check;
EXECUTE crm_demo_check_stmt;
DEALLOCATE PREPARE crm_demo_check_stmt;
SET @crm_demo_existing=(
SELECT COALESCE(JSON_ARRAYAGG(s.seq),JSON_ARRAY()) FROM JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s JOIN crm_customer c
 ON c.unified_social_credit_code=CONCAT('91319920261022',LPAD(s.seq,4,'0')));

INSERT INTO crm_customer(customer_name,normalized_name,unified_social_credit_code,primary_phone,normalized_primary_phone,
 source_code,industry_code,region,company_size,remark,customer_level,tags_json,owner_id,team_id,customer_status,sea_type,
 ownership_seq,last_effective_follow_at,warning_at,first_contact_deadline_at,created_by,updated_by,created_at,updated_at)
SELECT s.customer_name,s.customer_name,CONCAT('91319920261022',LPAD(s.seq,4,'0')),CONCAT('1390022',LPAD(s.seq,4,'0')),CONCAT('1390022',LPAD(s.seq,4,'0')),
 'DEBUG_SEED',s.industry,s.region,IF(MOD(s.seq,2)=0,'50-200人','50人以下'),
 CONCAT(@crm_demo_batch,'；合成演示数据，非真实企业、沟通或交易'),s.level_code,s.tags,
 IF(s.sea='PRIVATE',@crm_demo_owner,NULL),IF(s.sea='PRIVATE',@crm_demo_team,NULL),IF(s.stage IS NULL,'NEW','PROTECTED'),s.sea,
 IF(s.sea='PRIVATE',1,0),IF(s.stage IS NOT NULL,DATE_SUB(@crm_demo_now,INTERVAL 1 DAY),NULL),
 IF(s.seq IN(1,2),DATE_ADD(DATE_SUB(@crm_demo_now,INTERVAL IF(s.seq=2,52,4) HOUR),INTERVAL 48 HOUR),NULL),
 IF(s.seq IN(1,2),DATE_ADD(DATE_SUB(@crm_demo_now,INTERVAL IF(s.seq=2,52,4) HOUR),INTERVAL 72 HOUR),NULL),
 @crm_demo_admin,@crm_demo_admin,
 IF(s.seq IN(1,2),DATE_SUB(@crm_demo_now,INTERVAL IF(s.seq=2,52,4) HOUR),IF(s.sea='PUBLIC',CAST(@crm_demo_now AS DATETIME(3)),DATE_SUB(@crm_demo_now,INTERVAL 8 DAY))),@crm_demo_now
FROM JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s WHERE NOT JSON_CONTAINS(@crm_demo_existing,CAST(s.seq AS JSON));

SET @crm_demo_fresh=(
SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('seq',s.seq,'id',c.id)),JSON_ARRAY()) FROM JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s JOIN crm_customer c
 ON c.unified_social_credit_code=CONCAT('91319920261022',LPAD(s.seq,4,'0'))
WHERE NOT JSON_CONTAINS(@crm_demo_existing,CAST(s.seq AS JSON)));

INSERT INTO crm_contact(customer_id,contact_name,job_title,phone,email,decision_role,is_primary,created_by,created_at,updated_at)
SELECT f.id,CONCAT('演示联系人',s.seq),'业务对接人',CONCAT('1390022',LPAD(s.seq,4,'0')),
 CONCAT('claim-demo-',s.seq,'@crm-demo.example'),'CONTACT',1,@crm_demo_admin,c.created_at,@crm_demo_now
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s ON s.seq=f.seq JOIN crm_customer c ON c.id=f.id;

INSERT INTO crm_opportunity(customer_id,opportunity_name,owner_id,team_id,stage_code,activity_status,stagnation_days,grace_days,
 threshold_customized,expected_amount,expected_close_date,quote_amount,quote_at,customer_confirm_result,customer_confirm_at,
 last_effective_progress_at,stage_changed_at,created_by,created_at,updated_at)
SELECT f.id,CONCAT('演示·专员1234·',CASE s.seq WHEN 3 THEN '客户管理系统咨询' WHEN 4 THEN '供应链流程优化' WHEN 5 THEN '销售平台实施'
 WHEN 6 THEN '设备运维服务' WHEN 7 THEN '管理平台采购' ELSE '企业服务合作' END),@crm_demo_owner,@crm_demo_team,s.stage,'ACTIVE',
 CASE s.stage WHEN 'NEW' THEN 20 WHEN 'FOLLOWING' THEN 30 ELSE 45 END,10,0,s.amount,DATE_ADD(CURRENT_DATE(),INTERVAL (s.seq*5) DAY),
 IF(s.stage IN('QUOTED','DEAL'),s.amount,NULL),IF(s.stage IN('QUOTED','DEAL'),DATE_SUB(@crm_demo_now,INTERVAL 12 HOUR),NULL),
 IF(s.stage='DEAL','CONFIRMED',NULL),IF(s.stage='DEAL',DATE_SUB(@crm_demo_now,INTERVAL 2 HOUR),NULL),
 DATE_SUB(@crm_demo_now,INTERVAL 1 DAY),DATE_SUB(@crm_demo_now,INTERVAL 2 DAY),@crm_demo_admin,DATE_SUB(@crm_demo_now,INTERVAL 4 DAY),@crm_demo_now
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s ON s.seq=f.seq WHERE s.stage IS NOT NULL;

-- NEW商机仅有客户级有效跟进，未满足本商机有效跟进闸门；其他商机关联完整有效记录。
INSERT INTO crm_follow_record(customer_id,opportunity_id,contact_id,follow_user_id,record_type,follow_method,content,result_code,
 substantive_result,next_action,next_action_at,is_effective,review_status,reviewer_id,reviewed_at,occurrence_key,followed_at,created_at)
SELECT f.id,IF(s.stage='NEW',NULL,o.id),p.id,@crm_demo_owner,'FOLLOW','PHONE',
 '合成演示：已沟通采购需求、预算和后续方案；并非真实沟通。','NEED_CONFIRMED','演示：确认系统需求及预计采购时间',
 '演示：下一次沟通实施方案',DATE_ADD(@crm_demo_now,INTERVAL 2 DAY),1,'CONFIRMED',@crm_demo_manager,
 DATE_SUB(@crm_demo_now,INTERVAL 23 HOUR),CONCAT('claim-demo-20261022:',s.seq),DATE_SUB(@crm_demo_now,INTERVAL 1 DAY),DATE_SUB(@crm_demo_now,INTERVAL 1 DAY)
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s ON s.seq=f.seq JOIN crm_contact p ON p.customer_id=f.id AND p.is_primary=1
JOIN crm_opportunity o ON o.customer_id=f.id WHERE s.stage IS NOT NULL;

INSERT INTO crm_follow_record(customer_id,opportunity_id,contact_id,follow_user_id,record_type,follow_method,content,result_code,
 is_effective,review_status,occurrence_key,followed_at,created_at)
SELECT f.id,o.id,p.id,@crm_demo_owner,'FOLLOW','PHONE','合成演示：电话未接通，仅留痕，不推进阶段。','NO_RESPONSE',0,'NOT_REQUIRED',
 CONCAT('claim-demo-no-response:',s.seq),DATE_SUB(@crm_demo_now,INTERVAL 3 HOUR),DATE_SUB(@crm_demo_now,INTERVAL 3 HOUR)
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN JSON_TABLE(@crm_demo_source,'$[*]' COLUMNS(
 seq INT PATH '$.seq', customer_name VARCHAR(160) PATH '$.customer_name', industry VARCHAR(32) PATH '$.industry',
 region VARCHAR(100) PATH '$.region', level_code VARCHAR(20) PATH '$.level_code', tags JSON PATH '$.tags',
 sea VARCHAR(16) PATH '$.sea', stage VARCHAR(32) PATH '$.stage', amount DECIMAL(18,2) PATH '$.amount')) s ON s.seq=f.seq JOIN crm_contact p ON p.customer_id=f.id AND p.is_primary=1
JOIN crm_opportunity o ON o.customer_id=f.id WHERE s.seq=4;

INSERT INTO crm_opportunity_stage_log(opportunity_id,from_stage,to_stage,action_code,reason,operator_id,occurred_at)
SELECT o.id,NULL,o.stage_code,'DEMO_SEEDED','用户授权初始化合成测试场景；并非实际业务流转',@crm_demo_admin,@crm_demo_now
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN crm_opportunity o ON o.customer_id=f.id;
INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,after_json,reason)
SELECT 'OPPORTUNITY',o.id,'DEMO_SEEDED',@crm_demo_admin,@crm_demo_team,@crm_demo_now,
 JSON_OBJECT('batch',@crm_demo_batch,'stage',o.stage_code,'ownerId',o.owner_id,'teamId',o.team_id),
 '用户授权新增合成演示商机；不代表真实报价、合作确认或签约'
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN crm_opportunity o ON o.customer_id=f.id;
INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,ownership_seq,new_owner_id,new_team_id,after_json,reason)
SELECT 'CUSTOMER',c.id,'DEMO_SEEDED',@crm_demo_admin,c.team_id,@crm_demo_now,c.ownership_seq,c.owner_id,c.team_id,
 JSON_OBJECT('batch',@crm_demo_batch,'sea',c.sea_type,'ownerId',c.owner_id,'teamId',c.team_id),
 '用户授权新增合成演示客户；管理员直接分配不占专员公海领取次数'
FROM JSON_TABLE(@crm_demo_fresh,'$[*]' COLUMNS(seq INT PATH '$.seq',id BIGINT PATH '$.id')) f JOIN crm_customer c ON c.id=f.id;
UPDATE sys_user u SET current_hold_count=(SELECT COUNT(*) FROM crm_customer c WHERE c.owner_id=u.id AND c.sea_type='PRIVATE' AND c.deleted=0)
WHERE u.id=@crm_demo_owner;
COMMIT;
SELECT JSON_LENGTH(@crm_demo_fresh) AS newly_created_customers;
SELECT c.sea_type,COUNT(*) AS customer_count FROM crm_customer c
WHERE c.remark LIKE CONCAT(@crm_demo_batch,'；%') AND c.deleted=0 GROUP BY c.sea_type;
SELECT o.stage_code,COUNT(*) AS opportunity_count FROM crm_opportunity o JOIN crm_customer c ON c.id=o.customer_id
WHERE c.remark LIKE CONCAT(@crm_demo_batch,'；%') GROUP BY o.stage_code;
