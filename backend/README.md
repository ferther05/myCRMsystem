# CRM 后端

这是 Java 17 + Spring Boot 3 的模块化单体，开发根目录为 `E:/bishe/CRM/backend`。数据库 SQL 统一放在项目根目录的 `database/`。

## 先启动起来

在 `E:/bishe/CRM` 的 PowerShell 中执行：

```powershell
# 修改代码后先构建；包含无需数据库的基础测试
& .\tools\backend.ps1 -Action Build

# 后台启动，自动使用 JDK 17 和 local 配置
& .\tools\backend.ps1 -Action Start

# 查看连接状态
& .\tools\backend.ps1 -Action Status

# 停止本脚本启动的后端
& .\tools\backend.ps1 -Action Stop
```

默认检查地址为 `http://127.0.0.1:8080/api/health`。MySQL 和 Redis 都正常时，返回 `code=0`、`data.status=UP`，两个组件均为 `UP`。依赖异常时返回 HTTP 503，不会把连接失败报告成成功。

如果希望在当前终端看实时日志，使用 `-Action Run`，按 Ctrl+C 退出。`Start` 使用已经构建的 jar；修改代码后依次执行 `Stop`、`Build`、`Start`，避免 Windows 下运行中的 jar 被锁定。8080 已被其他程序占用时会报错，不会结束其他程序。

Redis 使用项目专用的 `127.0.0.1:6380`。未启动时先运行：

```powershell
& .\tools\redis.ps1 -Action Start
```

## 已锁定的依赖

- Spring Boot 3.5.16，Java 17；不升级到 Spring Boot 4。
- MyBatis-Plus 3.5.17，使用对应的 Spring Boot 3 starter 和分页解析依赖。
- Druid 1.2.28，连接池最多 10 个连接；关闭 Web 监控页面。
- JJWT 0.13.0，使用 HS256 签发和验证 JWT；版本于 2026-10-18 核对 Maven Central。
- MySQL Connector/J、Spring Security 6、Spring Data Redis、Validation、Lombok、JUnit 5 由 Spring Boot 3.5.16 的 BOM 锁定版本。

原框架版本于 2026-10-17 从 Maven Central 正式版本元数据核对，并通过本项目实际构建确定组合。原参考的 Boot 3.0.x 为旧基线，本轮沿用 Boot 3 技术方向。

## 配置放在哪里

- `src/main/resources/application.yml`：可提交的公共配置；不含真实密码，不自动执行建库/建表/种子 SQL。
- `config/application-local.yml`：当前电脑的应用数据库凭据和 JWT 签名密钥，已被 Git 忽略。`local` profile 且工作目录为 `backend/` 时由 Spring Boot 自动读取。
- `config/application-local.yml.example`：换电脑时可复制的模板；需要自行提供新电脑的应用账号密码及 JWT 密钥。

当前应用数据库账号为 `crm_app`，通过 TCP 连接 `127.0.0.1:3306/crm_system`。权限仅为该库的 SELECT、INSERT、UPDATE、DELETE；建表和迁移继续使用管理员工具手动执行。此账号与 CRM 页面登录用的 `admin` 是两类账号。

可用环境变量覆盖本地配置：`CRM_DB_URL`、`CRM_DB_USERNAME`、`CRM_DB_PASSWORD`、`CRM_REDIS_HOST`、`CRM_REDIS_PORT`、`CRM_REDIS_PASSWORD`、`CRM_SERVER_PORT`、`CRM_JWT_SECRET`、`CRM_JWT_TTL_SECONDS`。签名密钥使用至少 32 随机字节的 Base64 字符串，不能使用账号密码；缺少密钥时应用拒绝启动。脚本可通过 `CRM_JAVA_HOME`、`CRM_MAVEN_HOME` 指定工具位置，不修改系统全局环境。

只生成密钥时可在本机 PowerShell 执行下面命令，然后将结果填入本地配置；不要把结果提交或粘贴到日志中。当前电脑已自动生成，无需重新替换。

```powershell
$jwtBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

业务时钟固定 `Asia/Shanghai`。本机 MySQL 缺少命名时区表，JDBC 使用 URL 编码的 `%2B08:00` 并强制会话为 `+08:00`；不要求导入 MySQL 全局时区数据。默认 JDBC 地址只针对本地开发，远程环境需用 `CRM_DB_URL` 设置自己的 TLS 和证书校验选项。

## 目录与开发约定

```text
backend/
├─ pom.xml
├─ config/                       本地配置和模板
├─ src/main/java/com/bishe/crm/
│  ├─ CrmApplication.java         启动入口
│  ├─ common/                    响应、分页、异常、实体公共字段
│  ├─ config/                    安全、MyBatis-Plus、业务时钟和时间填充
│  └─ module/
│     ├─ health/                 Controller → Service → Mapper 健康检查
│     ├─ auth/                   登录、当前用户、首次/普通改密、退出与 JWT
│     └─ system/                 sys_config/sys_user Entity 和 MyBatis Mapper
├─ src/main/resources/           公共 application.yml
└─ src/test/java/                基础测试和真实依赖集成测试
```

新增业务按模块分包，Controller 接收 DTO 并返回 VO，Service 负责规则和事务，Mapper 访问数据库。Entity 不直接作为业务接口返回；暂不生成所有 18 张表的 CRUD。当前没有逻辑删除列，因此未配置全局逻辑删除，不能自动给现有表拼接不存在的字段。

统一响应为 `{ code, message, data }`，成功 `code=0`；分页为 `data: { total, list }`。已提供分页最大 100 条、`@Version` 乐观锁、`created_at/updated_at` 自动填充和可注入的业务 `Clock`。自动填充时间不等于已经实现业务审计日志。

允许匿名访问 `GET /api/health` 和 `POST /api/auth/login`；`GET /api/auth/me`、`POST /api/auth/password`、`POST /api/auth/logout` 使用 Bearer JWT。初始化账号可用，初始密码在忽略的 `database/local-credentials.md`；首次登录只能查看本人信息、改密或退出。改密成功后需使用新密码重新登录。单账号单会话，重新登录、退出或改密会撤销旧令牌；不启用默认生成密码、Basic 登录或表单登录。完整请求和错误码见 [接口约定](../docs/backend-api.md)。

## 验证和日志

```powershell
# 基础 HTTP/异常/权限过滤测试，不需要 MySQL/Redis
& .\tools\backend.ps1 -Action Test

# 基础测试 + 真实 MySQL/Redis、分页、乐观锁和字段填充
& .\tools\backend.ps1 -Action Verify
```

`Verify` 需要当前本地配置及运行中的 MySQL/Redis；只连接 `crm_system`。临时 `sys_config` 记录位于测试事务中并回滚；认证测试使用独立 `auth.it.*` 临时账号/团队，通过随机端口真实 HTTP 验证已提交的改密、退出、并发和审计回滚，结束后按精确 id 清理。已有管理员和团队演示账号仅核对初始密码哈希，不改变其密码或首次改密标记；该项核对需要本机 `database/local-credentials.md`。集成验证还会启动独立随机端口实例，将该实例的 Redis 连接指向不可用端口，确认真实 HTTP 返回 503；现有 Redis 服务保持运行。默认 `Build/Test` 不执行真实依赖集成测试。

`Verify` 会重新打包 jar，执行前同样先用 `Stop` 停止本项目后端，完成后再 `Start`。

应用日志位于 `output/backend/crm-backend.log`，脚本后台启动输出为同目录的 `stdout.log`、`stderr.log`；这些文件均不提交。测试结果分别位于 `backend/target/surefire-reports/` 和 `failsafe-reports/`。

IDEA 导入 `backend/pom.xml`，项目 SDK/Maven Runner 设置 JDK 17，运行 `CrmApplication`，Working directory 设置为 `E:/bishe/CRM/backend`，Active profiles 设置为 `local`。需要接口契约时查看 [后端接口约定](../docs/backend-api.md)。

## 用户与权限模块

已实现内部账号申请/管理员审核与直接建号、角色团队/启停管理、密码重置、个人资料及令牌更新、用户与客户基础查询的数据隔离。复用 18 表，无结构迁移。`AccountManagementIT` 使用独立 `am-*` 账号和团队、临时客户验证真实权限、并发和事务回滚，并按唯一前缀/精确 id 清理。运行 `Verify` 前停止受管理后端，完成后重新 `Start`。说明见 [模块操作指南](../docs/module-guides/01-用户与权限管理.md) 和 [验证记录](../docs/user-permissions-verification.md)。
