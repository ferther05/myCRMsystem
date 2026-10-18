# 数据库目录

本目录统一保存 MySQL 数据库设计、建表、初始化和后续变更 SQL。

当前数据库落地文件按以下顺序执行：

1. `001_create_database.sql`：只创建独立的 `crm_system` 数据库，不删除任何现有数据库。
2. `002_schema.sql`：创建 18 张表、外键、金额检查、唯一键和查询索引。
3. `003_seed_demo_data.sql`：写入一个管理员、两个团队和四个团队演示账号，以及首批参数。
4. `004_verify_schema.sql`：只读检查表数量、字符集、外键、关键索引和种子账号。

执行后仍需由 Spring Boot JDBC 连接和业务测试验证服务层规则；SQL 通过不等于应用完成。

## 本机执行记录

2026-10-17 已使用本机 MySQL 管理员凭据在 `127.0.0.1:3306` 执行全部四步脚本。结果为：`crm_system` 创建成功，18 张表全部使用 InnoDB 和 `utf8mb4_0900_ai_ci`；管理员、两个启用团队和四个演示账号写入成功；`004_verify_schema.sql` 的表数量、外键、待办接收人唯一键和任务逻辑批次键检查通过。管理员密码仅临时传入命令环境，没有写入源码。

2026-10-17 后续已创建 `crm_app@127.0.0.1`，只授予 `crm_system.*` 的 SELECT、INSERT、UPDATE、DELETE。账号创建模板为 `005_create_app_user.sql.example`，其中占位密码不能直接执行，也不纳入四步初始化链。真实随机密码只放在被忽略的 `backend/config/application-local.yml`。

Spring Boot/Druid/JDBC/MyBatis 真实连接、MyBatis-Plus 分页/乐观锁、MySQL 会话 `+08:00` 时区以及测试事务回滚已通过验证，临时测试记录剩余 0 条。没有更改既有 18 表结构或管理员/团队种子数据。业务登录、角色隔离和客户到回款流程仍未实现或验收；详情见 [后端验证记录](../docs/backend-verification.md)。

项目内的 `schema-preview.md` 是维护源，另导出一份到 `E:/bishe/开始参考/CRM数据库精简预览.md` 方便用户查看。旧 54 表草案仍保留在开始参考目录，只作为历史对照。

本目录不是 MySQL 的运行数据目录。实际数据文件、数据库密码及含真实数据的备份不作为源码提交。

本地演示账号的明文密码仅写在被 Git 忽略的 `local-credentials.md`，SQL 里只保存 BCrypt 哈希。上线或共享环境必须立即替换这些密码，并通过环境变量或本地配置提供连接凭据。

## 用户权限模块实现更新

2026-10-18：账号、团队、内部申请和个人资料接口已实现，使用现有 `sys_user`、`sys_team`、`crm_approval`、`crm_operation_log`；客户范围查询复用 `crm_customer`。本次无需执行迁移或重新初始化，仍为 18 表。审批类型 ACCOUNT_OPEN、业务类型 USER，待审账号 PENDING，审核通过后 ACTIVE；明文密码不写入审批、日志或数据库。此前关于应用未实现的说明是历史准备记录。操作说明见 `docs/module-guides/01-用户与权限管理.md`。
