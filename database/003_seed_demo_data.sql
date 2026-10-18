-- Seed data for local demonstration only.
-- Passwords are BCrypt hashes. Plaintext values are recorded only in the ignored
-- database/local-credentials.md file for the local developer.
USE crm_system;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO sys_team (id, team_code, team_name, enabled, version)
VALUES
    (1, 'EAST', '华东销售团队', 1, 0),
    (2, 'SOUTH', '华南销售团队', 1, 0)
ON DUPLICATE KEY UPDATE
    team_name = VALUES(team_name), enabled = VALUES(enabled);

INSERT INTO sys_user
    (id, username, password_hash, real_name, phone, email, role_code, team_id, account_status, must_change_password, version)
VALUES
    (1, 'admin', '$2a$10$KqZHIsxGbtRbIT/p6Uuo0u9gfOLLU.Oho1UO54IpPpWgStHiMIilS', '系统管理员', NULL, 'admin@crm.local', 'ADMIN', NULL, 'ACTIVE', 1, 0),
    (2, 'east.manager', '$2a$10$SUC7GbGYLKsTfn1cWMg0oeFwEylMngJX7uoBZZ/vzGGOMk99EWEsW', '华东经理', NULL, 'east.manager@crm.local', 'MANAGER', 1, 'ACTIVE', 1, 0),
    (3, 'east.sales', '$2a$10$0f9qDCSwzDXwum2/8UIqOOg5m4a4nOfDUi8b6BHiLDXatMrrc7V4q', '华东专员', NULL, 'east.sales@crm.local', 'SPECIALIST', 1, 'ACTIVE', 1, 0),
    (4, 'south.manager', '$2a$10$SUC7GbGYLKsTfn1cWMg0oeFwEylMngJX7uoBZZ/vzGGOMk99EWEsW', '华南经理', NULL, 'south.manager@crm.local', 'MANAGER', 2, 'ACTIVE', 1, 0),
    (5, 'south.sales', '$2a$10$0f9qDCSwzDXwum2/8UIqOOg5m4a4nOfDUi8b6BHiLDXatMrrc7V4q', '华南专员', NULL, 'south.sales@crm.local', 'SPECIALIST', 2, 'ACTIVE', 1, 0)
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name), role_code = VALUES(role_code), team_id = VALUES(team_id), account_status = VALUES(account_status), must_change_password = VALUES(must_change_password);

UPDATE sys_team SET manager_id = 2 WHERE id = 1;
UPDATE sys_team SET manager_id = 4 WHERE id = 2;

INSERT INTO sys_config (config_type, config_group, config_key, config_value, display_name, sort_no, enabled)
VALUES
    ('PARAM', 'crm.time', 'first_contact_warning_hours', '48', '首联预警小时数', 10, 1),
    ('PARAM', 'crm.time', 'first_contact_recycle_hours', '72', '首联回收小时数', 20, 1),
    ('PARAM', 'crm.time', 'follow_warning_days', '27', '常规跟进预警天数', 30, 1),
    ('PARAM', 'crm.time', 'follow_recycle_days', '30', '常规跟进回收天数', 40, 1),
    ('PARAM', 'crm.time', 'close_pending_max_days', '3', '待关闭最长保护天数', 50, 1),
    ('PARAM', 'crm.system', 'business_timezone', 'Asia/Shanghai', '业务时区', 10, 1),
    ('PARAM', 'crm.job', 'logical_run_format', '{job_name}:{business_date}', '任务逻辑批次格式', 10, 1)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value), display_name = VALUES(display_name), enabled = VALUES(enabled);

SELECT id, username, real_name, role_code, team_id, account_status
FROM sys_user
ORDER BY id;

SELECT id, team_code, team_name, manager_id, enabled
FROM sys_team
ORDER BY id;
