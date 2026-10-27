-- Task 17: contract signing progress and immutable transition history.
-- Contract review state remains in contract_status; signing_progress tracks signing milestones.
USE crm_system;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

ALTER TABLE crm_contract ALTER COLUMN signing_progress SET DEFAULT 'INTERNAL_HANDLING';

CREATE TABLE IF NOT EXISTS crm_contract_signing_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    contract_id BIGINT UNSIGNED NOT NULL,
    from_progress VARCHAR(32) NULL,
    to_progress VARCHAR(32) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(80) NOT NULL,
    operator_role VARCHAR(24) NOT NULL,
    operator_team_id BIGINT UNSIGNED NULL,
    changed_at DATETIME(3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    is_correction TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_contract_signing_history (contract_id, id),
    CONSTRAINT fk_signing_history_contract FOREIGN KEY (contract_id) REFERENCES crm_contract (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_signing_history_user FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_signing_history_team FOREIGN KEY (operator_team_id) REFERENCES sys_team (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同签署进度历史';

UPDATE crm_contract
SET signing_progress = CASE
    WHEN contract_status = 'VOID' THEN 'WITHDRAWN'
    WHEN contract_status IN ('PENDING_REVIEW','RETURNED') THEN 'WAIT_COUNTERPARTY_SIGN'
    WHEN contract_status IN ('SIGNED','PERFORMING','SETTLED','TERMINATED') THEN 'BOTH_SIGNED'
    WHEN signing_progress IN ('INTERNAL_HANDLING','WAIT_OUR_SIGN','WAIT_COUNTERPARTY_SIGN','BOTH_SIGNED','EXCEPTION','WITHDRAWN') THEN signing_progress
    ELSE 'INTERNAL_HANDLING'
END;

UPDATE crm_contract_signing_history h
JOIN crm_contract c ON c.id=h.contract_id
SET h.to_progress=c.signing_progress,
    h.reason='任务17迁移初始化；原合同仍待内部核验，签署节点保留在待对方签署'
WHERE c.contract_status IN ('PENDING_REVIEW','RETURNED')
  AND h.operator_name='系统迁移' AND h.from_progress IS NULL AND h.is_correction=0;

INSERT INTO crm_contract_signing_history(contract_id,from_progress,to_progress,operator_id,operator_name,operator_role,operator_team_id,changed_at,reason,is_correction)
SELECT c.id,NULL,c.signing_progress,NULL,'系统迁移','SYSTEM',NULL,c.updated_at,
       '任务17迁移初始化；旧合同登记/核验状态保留在合同状态字段',0
FROM crm_contract c
WHERE NOT EXISTS (SELECT 1 FROM crm_contract_signing_history h WHERE h.contract_id=c.id);

SELECT c.id,c.contract_status,c.signing_progress,COUNT(h.id) AS history_count
FROM crm_contract c LEFT JOIN crm_contract_signing_history h ON h.contract_id=c.id
GROUP BY c.id,c.contract_status,c.signing_progress ORDER BY c.id;
