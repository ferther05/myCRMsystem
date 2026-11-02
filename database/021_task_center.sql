-- 任务中心：已读与业务完成分离；重复执行不重置既有记录。
USE crm_system;
SET NAMES utf8mb4;
SET @crm_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_todo' AND column_name='read_at')=0,
 'ALTER TABLE crm_todo ADD COLUMN read_at DATETIME(3) NULL', 'SELECT 1');
PREPARE crm_stmt FROM @crm_ddl;
EXECUTE crm_stmt;
DEALLOCATE PREPARE crm_stmt;
