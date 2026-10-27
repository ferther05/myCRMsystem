-- Customer ownership limits used by the public-pool claim endpoints.
USE crm_system;

INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
SELECT 'PARAM','crm.customer','claim_hold_limit','50','专员私海持有上限',10,1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='claim_hold_limit');

INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
SELECT 'PARAM','crm.customer','claim_daily_limit','3','专员每日普通领取上限',20,1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='claim_daily_limit');

-- Reconcile denormalized hold counts after upgrade; subsequent ownership changes keep them current.
UPDATE sys_user u
SET u.current_hold_count=(
    SELECT COUNT(*) FROM crm_customer c
    WHERE c.owner_id=u.id AND c.sea_type='PRIVATE' AND c.deleted=0
);

SELECT config_key,config_value,enabled
FROM sys_config
WHERE config_type='PARAM' AND config_group='crm.customer'
  AND config_key IN ('claim_hold_limit','claim_daily_limit')
ORDER BY config_key;
