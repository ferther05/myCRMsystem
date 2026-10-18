-- Read-only checks for the dedicated CRM database.
USE crm_system;

SELECT DATABASE() AS selected_database, COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE';

SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;

SELECT table_name, COUNT(*) AS foreign_key_count
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
GROUP BY table_name
ORDER BY table_name;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('crm_todo', 'crm_job_log', 'crm_payment_plan', 'crm_payment_period', 'crm_payment_record')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT COUNT(*) AS admin_count
FROM sys_user
WHERE role_code = 'ADMIN' AND account_status = 'ACTIVE';

SELECT COUNT(*) AS team_count
FROM sys_team
WHERE enabled = 1;

SELECT COUNT(*) AS demo_user_count
FROM sys_user
WHERE username IN ('east.manager', 'east.sales', 'south.manager', 'south.sales')
  AND account_status = 'ACTIVE';

SELECT 'todo_unique_key' AS check_name,
       COUNT(*) AS matching_indexes
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'crm_todo'
  AND index_name = 'uk_todo_event_recipient'
  AND column_name = 'recipient_id';

SELECT 'job_log_batch_key' AS check_name,
       COUNT(*) AS matching_indexes
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'crm_job_log'
  AND index_name = 'uk_job_log_attempt';
