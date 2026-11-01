-- Two synthetic quote-stage opportunities for the east.sales demo account.
-- The opportunities are ready for the customer-cooperation confirmation action;
-- all customer, quote, and follow-up details below are test data, not real business evidence.
-- Safe to rerun: existing matching records are preserved and not duplicated.
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

SET @demo_batch = 'CRM-DEMO-EAST-QUOTE-20261030';
SET @demo_now = CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00');
SET @demo_follow_at = DATE_SUB(@demo_now, INTERVAL 1 HOUR);
SET @demo_owner = (
    SELECT id FROM sys_user
    WHERE username = 'east.sales' AND role_code = 'SPECIALIST' AND account_status = 'ACTIVE'
    LIMIT 1
);
SET @demo_team = (SELECT team_id FROM sys_user WHERE id = @demo_owner);
SET @demo_manager = (
    SELECT id FROM sys_user
    WHERE role_code = 'MANAGER' AND team_id = @demo_team AND account_status = 'ACTIVE'
    ORDER BY id LIMIT 1
);
SET @demo_admin = (
    SELECT id FROM sys_user
    WHERE role_code = 'ADMIN' AND account_status = 'ACTIVE'
    ORDER BY id LIMIT 1
);

-- Create two private-sea customers with unique demo names and non-real contact details.
INSERT INTO crm_customer (
    customer_name, normalized_name, primary_phone, normalized_primary_phone,
    source_code, industry_code, region, company_size, remark, customer_level, tags_json,
    owner_id, team_id, customer_status, sea_type, ownership_seq, last_effective_follow_at,
    created_by, updated_by, created_at, updated_at
)
SELECT
    '演示专用·东区客户A·报价待确认', '演示专用·东区客户A·报价待确认',
    '13900010031', '13900010031', 'DEBUG_SEED', 'TECH', '上海', '50人以下',
    CONCAT(@demo_batch, '；合成演示数据；无真实客户、报价或合作关系'),
    'NORMAL', JSON_ARRAY('POTENTIAL', 'NEW_DEMAND'),
    @demo_owner, @demo_team, 'PROTECTED', 'PRIVATE', 1, @demo_follow_at,
    @demo_admin, @demo_admin, @demo_now, @demo_now
WHERE @demo_owner IS NOT NULL AND @demo_team IS NOT NULL AND @demo_manager IS NOT NULL AND @demo_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_customer
      WHERE deleted = 0 AND normalized_name = '演示专用·东区客户A·报价待确认'
  );

INSERT INTO crm_customer (
    customer_name, normalized_name, primary_phone, normalized_primary_phone,
    source_code, industry_code, region, company_size, remark, customer_level, tags_json,
    owner_id, team_id, customer_status, sea_type, ownership_seq, last_effective_follow_at,
    created_by, updated_by, created_at, updated_at
)
SELECT
    '演示专用·东区客户B·报价待确认', '演示专用·东区客户B·报价待确认',
    '13900010032', '13900010032', 'DEBUG_SEED', 'SERVICE', '江苏南京', '50-200人',
    CONCAT(@demo_batch, '；合成演示数据；无真实客户、报价或合作关系'),
    'NORMAL', JSON_ARRAY('LOCAL', 'LONG_TERM'),
    @demo_owner, @demo_team, 'PROTECTED', 'PRIVATE', 1, @demo_follow_at,
    @demo_admin, @demo_admin, @demo_now, @demo_now
WHERE @demo_owner IS NOT NULL AND @demo_team IS NOT NULL AND @demo_manager IS NOT NULL AND @demo_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_customer
      WHERE deleted = 0 AND normalized_name = '演示专用·东区客户B·报价待确认'
  );

-- Give each customer one synthetic primary contact so the confirmation workflow is testable.
INSERT INTO crm_contact (
    customer_id, contact_name, phone, email, job_title, decision_role,
    is_primary, created_by, created_at, updated_at
)
SELECT c.id, '演示联系人A', '13900010031', 'quote-a@crm-demo.example',
       '演示对接人', 'CONTACT', 1, @demo_admin, @demo_now, @demo_now
FROM crm_customer c
WHERE c.deleted = 0 AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户A·报价待确认'
  AND NOT EXISTS (SELECT 1 FROM crm_contact ct WHERE ct.customer_id = c.id AND ct.is_primary = 1);

INSERT INTO crm_contact (
    customer_id, contact_name, phone, email, job_title, decision_role,
    is_primary, created_by, created_at, updated_at
)
SELECT c.id, '演示联系人B', '13900010032', 'quote-b@crm-demo.example',
       '演示对接人', 'CONTACT', 1, @demo_admin, @demo_now, @demo_now
FROM crm_customer c
WHERE c.deleted = 0 AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户B·报价待确认'
  AND NOT EXISTS (SELECT 1 FROM crm_contact ct WHERE ct.customer_id = c.id AND ct.is_primary = 1);

-- Seed the QUOTED stage with quote data, leaving customer cooperation unconfirmed.
INSERT INTO crm_opportunity (
    customer_id, opportunity_name, owner_id, team_id, stage_code, activity_status,
    stagnation_days, grace_days, expected_amount, expected_close_date,
    quote_amount, quote_at, customer_confirm_result, customer_confirm_at,
    last_effective_progress_at, stage_changed_at, created_by, created_at, updated_at
)
SELECT c.id, '演示专用·东区商机A·报价后待客户确认',
       @demo_owner, @demo_team, 'QUOTED', 'ACTIVE', 45, 15,
       26800.00, DATE_ADD(DATE(@demo_now), INTERVAL 45 DAY),
       26800.00, @demo_now, NULL, NULL,
       @demo_follow_at, @demo_now, @demo_admin, @demo_now, @demo_now
FROM crm_customer c
WHERE c.deleted = 0 AND c.sea_type = 'PRIVATE' AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户A·报价待确认'
  AND NOT EXISTS (
      SELECT 1 FROM crm_opportunity o
      WHERE o.customer_id = c.id AND o.opportunity_name = '演示专用·东区商机A·报价后待客户确认'
  );

INSERT INTO crm_opportunity (
    customer_id, opportunity_name, owner_id, team_id, stage_code, activity_status,
    stagnation_days, grace_days, expected_amount, expected_close_date,
    quote_amount, quote_at, customer_confirm_result, customer_confirm_at,
    last_effective_progress_at, stage_changed_at, created_by, created_at, updated_at
)
SELECT c.id, '演示专用·东区商机B·报价后待客户确认',
       @demo_owner, @demo_team, 'QUOTED', 'ACTIVE', 45, 15,
       53800.00, DATE_ADD(DATE(@demo_now), INTERVAL 45 DAY),
       53800.00, @demo_now, NULL, NULL,
       @demo_follow_at, @demo_now, @demo_admin, @demo_now, @demo_now
FROM crm_customer c
WHERE c.deleted = 0 AND c.sea_type = 'PRIVATE' AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户B·报价待确认'
  AND NOT EXISTS (
      SELECT 1 FROM crm_opportunity o
      WHERE o.customer_id = c.id AND o.opportunity_name = '演示专用·东区商机B·报价后待客户确认'
  );

-- Seed one clearly synthetic, reviewed effective follow-up per opportunity.
INSERT INTO crm_follow_record (
    customer_id, opportunity_id, contact_id, follow_user_id, record_type, follow_method,
    content, result_code, substantive_result, next_action, next_action_at,
    is_effective, review_status, reviewer_id, reviewed_at, review_reason,
    occurrence_key, followed_at, created_at
)
SELECT c.id, o.id, ct.id, @demo_owner, 'FOLLOW', 'PHONE',
       '合成演示跟进：模拟已向客户说明报价，等待录入客户是否明确合作；非真实沟通。',
       'NEED_CONFIRMED',
       '演示：已模拟完成需求沟通和报价说明，等待客户明确合作确认。',
       '演示：使用测试账号登记客户明确合作结果。',
       DATE_ADD(@demo_now, INTERVAL 2 DAY),
       1, 'CONFIRMED', @demo_manager, @demo_now, '合成演示记录，仅供测试阶段推进',
       'CRM-DEMO-EAST-QUOTE-20261030:A', @demo_follow_at, @demo_now
FROM crm_customer c
JOIN crm_opportunity o ON o.customer_id = c.id
JOIN crm_contact ct ON ct.customer_id = c.id AND ct.is_primary = 1
WHERE c.deleted = 0 AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户A·报价待确认'
  AND o.opportunity_name = '演示专用·东区商机A·报价后待客户确认'
  AND NOT EXISTS (
      SELECT 1 FROM crm_follow_record f WHERE f.occurrence_key = 'CRM-DEMO-EAST-QUOTE-20261030:A'
  );

INSERT INTO crm_follow_record (
    customer_id, opportunity_id, contact_id, follow_user_id, record_type, follow_method,
    content, result_code, substantive_result, next_action, next_action_at,
    is_effective, review_status, reviewer_id, reviewed_at, review_reason,
    occurrence_key, followed_at, created_at
)
SELECT c.id, o.id, ct.id, @demo_owner, 'FOLLOW', 'PHONE',
       '合成演示跟进：模拟已向客户说明报价，等待录入客户是否明确合作；非真实沟通。',
       'NEED_CONFIRMED',
       '演示：已模拟完成需求沟通和报价说明，等待客户明确合作确认。',
       '演示：使用测试账号登记客户明确合作结果。',
       DATE_ADD(@demo_now, INTERVAL 2 DAY),
       1, 'CONFIRMED', @demo_manager, @demo_now, '合成演示记录，仅供测试阶段推进',
       'CRM-DEMO-EAST-QUOTE-20261030:B', @demo_follow_at, @demo_now
FROM crm_customer c
JOIN crm_opportunity o ON o.customer_id = c.id
JOIN crm_contact ct ON ct.customer_id = c.id AND ct.is_primary = 1
WHERE c.deleted = 0 AND c.owner_id = @demo_owner AND c.team_id = @demo_team
  AND c.normalized_name = '演示专用·东区客户B·报价待确认'
  AND o.opportunity_name = '演示专用·东区商机B·报价后待客户确认'
  AND NOT EXISTS (
      SELECT 1 FROM crm_follow_record f WHERE f.occurrence_key = 'CRM-DEMO-EAST-QUOTE-20261030:B'
  );

-- Record seeded stages and their synthetic origin for later traceability.
INSERT INTO crm_opportunity_stage_log (
    opportunity_id, from_stage, to_stage, action_code, reason, operator_id, occurred_at
)
SELECT o.id, NULL, 'QUOTED', 'DEMO_SEEDED',
       '合成演示初始化；不是实际销售阶段流转或客户合作确认',
       @demo_admin, @demo_now
FROM crm_opportunity o
WHERE o.opportunity_name IN (
        '演示专用·东区商机A·报价后待客户确认',
        '演示专用·东区商机B·报价后待客户确认'
    )
  AND o.owner_id = @demo_owner
  AND NOT EXISTS (
      SELECT 1 FROM crm_opportunity_stage_log l
      WHERE l.opportunity_id = o.id AND l.action_code = 'DEMO_SEEDED'
  );

INSERT INTO crm_operation_log (
    biz_type, biz_id, action_code, operator_id, operator_team_id,
    occurred_at, after_json, reason
)
SELECT 'CUSTOMER', c.id, 'DEMO_SEEDED', @demo_admin, @demo_team,
       @demo_now, JSON_OBJECT(
           'batch', @demo_batch, 'ownerId', @demo_owner,
           'seaType', 'PRIVATE', 'customerStatus', 'PROTECTED'
       ),
       '用户授权新增华东演示私海客户；合成数据，不代表真实客户'
FROM crm_customer c
WHERE c.normalized_name IN (
        '演示专用·东区客户A·报价待确认',
        '演示专用·东区客户B·报价待确认'
    )
  AND c.owner_id = @demo_owner
  AND NOT EXISTS (
      SELECT 1 FROM crm_operation_log l
      WHERE l.biz_type = 'CUSTOMER' AND l.biz_id = c.id
        AND l.action_code = 'DEMO_SEEDED'
        AND JSON_UNQUOTE(JSON_EXTRACT(l.after_json, '$.batch')) = @demo_batch
  );

INSERT INTO crm_operation_log (
    biz_type, biz_id, action_code, operator_id, operator_team_id,
    occurred_at, after_json, reason
)
SELECT 'OPPORTUNITY', o.id, 'DEMO_SEEDED', @demo_admin, @demo_team,
       @demo_now, JSON_OBJECT(
           'batch', @demo_batch, 'stage', 'QUOTED',
           'quoteAmount', o.quote_amount, 'customerConfirmResult', NULL
       ),
       '用户授权新增报价阶段演示商机；报价金额为模拟值，合作确认尚未登记'
FROM crm_opportunity o
WHERE o.opportunity_name IN (
        '演示专用·东区商机A·报价后待客户确认',
        '演示专用·东区商机B·报价后待客户确认'
    )
  AND o.owner_id = @demo_owner
  AND NOT EXISTS (
      SELECT 1 FROM crm_operation_log l
      WHERE l.biz_type = 'OPPORTUNITY' AND l.biz_id = o.id
        AND l.action_code = 'DEMO_SEEDED'
        AND JSON_UNQUOTE(JSON_EXTRACT(l.after_json, '$.batch')) = @demo_batch
  );

INSERT INTO crm_operation_log (
    biz_type, biz_id, action_code, operator_id, operator_team_id,
    occurred_at, after_json, reason
)
SELECT 'OPPORTUNITY', o.id, 'QUOTE_RECORDED', @demo_owner, @demo_team,
       o.quote_at, JSON_OBJECT(
           'batch', @demo_batch, 'quoteAmount', o.quote_amount
       ),
       '演示数据预置模拟报价；不代表真实商业报价或客户承诺'
FROM crm_opportunity o
WHERE o.opportunity_name IN (
        '演示专用·东区商机A·报价后待客户确认',
        '演示专用·东区商机B·报价后待客户确认'
    )
  AND o.owner_id = @demo_owner
  AND NOT EXISTS (
      SELECT 1 FROM crm_operation_log l
      WHERE l.biz_type = 'OPPORTUNITY' AND l.biz_id = o.id
        AND l.action_code = 'QUOTE_RECORDED'
        AND JSON_UNQUOTE(JSON_EXTRACT(l.after_json, '$.batch')) = @demo_batch
  );

-- Keep the specialist's private-sea counter aligned with assigned active customers.
UPDATE sys_user
SET current_hold_count = (
    SELECT COUNT(*) FROM crm_customer
    WHERE owner_id = @demo_owner AND sea_type = 'PRIVATE' AND deleted = 0
)
WHERE id = @demo_owner;

COMMIT;

SELECT
    c.id AS customer_id, c.customer_name, u.username AS owner,
    c.sea_type, c.customer_status,
    o.id AS opportunity_id, o.opportunity_name, o.stage_code, o.activity_status,
    o.quote_amount, o.quote_at, o.customer_confirm_result, o.customer_confirm_at,
    EXISTS (
        SELECT 1 FROM crm_follow_record f
        WHERE f.opportunity_id = o.id AND f.is_effective = 1 AND f.review_status = 'CONFIRMED'
    ) AS has_confirmed_effective_follow
FROM crm_customer c
JOIN crm_opportunity o ON o.customer_id = c.id
JOIN sys_user u ON u.id = c.owner_id
WHERE c.normalized_name IN (
        '演示专用·东区客户A·报价待确认',
        '演示专用·东区客户B·报价待确认'
    )
ORDER BY c.normalized_name;
