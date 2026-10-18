# 后端骨架验证记录

验证日期：2026-10-17，最终运行复查时间 20:37（UTC+08:00），业务时区 `Asia/Shanghai`。范围为后端框架和本地依赖连接，不包含业务模块验收、前端、部署或完整权限验证。

本文件保留 2026-10-17 骨架验证的历史结果。2026-10-18 已继续完成登录/JWT 与首次改密，当前完整套件为 15 项基础和 15 项集成测试，最新范围与证据见 [认证验证记录](auth-verification.md)；下文的“尚未执行”为当时状态。

## 实际环境与构建

Windows 本机，Microsoft JDK 17.0.8.1、Maven 3.9.16。使用 `backend/pom.xml` 锁定 Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Druid 1.2.28，使用本机 MySQL 8.0.41 与 WSL Redis 7.0.15/6380。

从 `backend/` 执行：

```powershell
& 'E:/apache-maven-3.9.16/bin/mvn.cmd' -B -ntp -Plocal-integration verify
```

最终结果为 `BUILD SUCCESS`，产物为 `backend/target/crm-backend.jar`。详细测试报告位于 `backend/target/surefire-reports/`、`backend/target/failsafe-reports/`；本轮复核命令日志为 `output/backend-review-verify.log`，数据库只读结构核对记录为 `output/backend-review-schema.log`，可复核摘要为 `output/backend-verification.json`。报告与日志不提交 Git。

从项目根目录实际执行 `& .\tools\backend.ps1 -Action Verify`，最终得到 8 项基础测试和 4 项集成测试全部通过，构建于 20:32:10（UTC+08:00）完成；验证后已重新启动后端。该命令包含重新打包，应先停止运行中的 jar，避免 Windows 文件锁。

## 已通过

- `ApiFoundationTest`：7 项。HTTP 200 成功响应、依赖异常 503、未登录 GET/POST 的 JSON 401、方法权限拒绝 403、分页参数校验 400、业务冲突 409、MVC 资源缺失 404。
- `InfrastructureHealthServiceTest`：1 项。模拟 MySQL 探针失败时，Redis 仍单独检查；响应不泄露连接异常详情，固定 Clock 正确返回 `+08:00`。此项故障为模拟验证。
- `BackendInfrastructureIT`：3 项。真实 `crm_app` 连接 `crm_system`，读取 18 张表及管理员/团队/时区种子，核对当前账号权限；真实 MyBatis-Plus 分页、主键/时间/version 填充与过期 version 更新拒绝；通过完整 Spring Boot + MockMvc 过滤器链查询真实 MySQL 与 Redis，并验证匿名业务访问拒绝。
- `BackendDegradedHealthIT`：1 项。独立应用实例使用随机 HTTP 端口，并将其 Redis 端口设为本机不可用的 1；通过真实 HTTP 确认返回 503、`code=50300`、总体 `DOWN`、MySQL `UP`、Redis `DOWN`，响应不含连接异常详情且不产生会话 Cookie。此项为真实连接失败验证，原 Redis 6380 服务保持运行。
- 涉及写入的集成测试使用临时 `sys_config` 行，在事务内回滚；本轮全部测试结束后另行只读查询，残留 `it.%` 配置行 0 条、原配置 7 条、启用管理员 1 个、启用团队 2 个、团队示例账号 4 个。
- 数据库只读结构复核：18 张表均为 InnoDB、`utf8mb4_0900_ai_ci`；外键及待办/任务关键唯一索引与初始化脚本一致。待办唯一键包含接收人，任务唯一键为逻辑批次与尝试号。`SHOW GRANTS` 确认应用账号仅有 `crm_system.*` 的 SELECT、INSERT、UPDATE、DELETE 权限，无建表、删库或授权权限。
- 运行打包后的 jar，真实网络请求 `GET http://127.0.0.1:8080/api/health` 返回 HTTP 200、`code=0`，MySQL/Redis 均为 `UP`。
- 真实网络匿名 GET/POST 访问 `/api/customers`、GET 访问 `/druid/index.html` 返回 HTTP 401、`code=40100`；未开放业务数据和连接池监控页，上述健康/拒绝响应均不产生会话 Cookie。
- `tools/backend.ps1` 的启动、状态、停止、再次启动与状态检查通过。脚本只停止其记录且启动时间匹配的 Java 进程，不终止占用端口的其他程序。
- 本地 `backend/config/application-local.yml` 由 `.gitignore` 排除，凭据不打入 jar；应用连接不使用 MySQL root。打包产物不含本地配置、种子凭据或测试专用类；应用入口 class 的 major version 为 61（Java 17）。可见源码凭据扫描及 `git diff --check` 通过。

总计 12 项：8 项基础测试与 4 项真实环境集成测试，最终 0 失败、0 错误、0 跳过；运行 jar 的 HTTP/脚本启停与只读 SQL 复查为额外命令验证，不计入 JUnit 数量。后端在复查结束时保持运行，健康地址为 `http://127.0.0.1:8080/api/health`。

## 本轮代码核对

核对了 Maven 依赖与 Java 级别、Controller/Service/Mapper 分层、统一响应/异常、分页及乐观锁、审计字段填充、安全过滤器、健康检查和启停脚本，并对照已确认业务决策检查本轮范围。本轮未发现需要修复的阻塞性代码缺陷；发现原验证缺少真实依赖故障下的 HTTP 证据，因此新增上述 `BackendDegradedHealthIT` 回归测试。未据此宣称尚未实现的业务规则已经通过验收。

种子账号密码哈希的非空与长度、首次改密标记及团队经理关联完成只读核对；未实际登录，也未校验每个初始明文密码与哈希的匹配，相关验证随登录模块完成。

## 发现并修复

首次真实集成测试因 MySQL 缺少命名时区表，报 `Unknown or incorrect time zone: 'Asia/Shanghai'`。JDBC 改用 URL 编码的 `%2B08:00` 并强制设置连接会话，随后 `SELECT @@session.time_zone` 为 `+08:00`；Java Clock 和 JSON 时间仍使用 `Asia/Shanghai`。没有修改 MySQL 全局时区或导入系统表。

首次 Maven 构建受到沙箱对本机 Maven 缓存目录的写入限制；获得执行授权后依赖下载与构建成功，没有为绕过限制修改全局配置。

## 尚未执行

登录/JWT、首次修改密码、团队/本人/协作数据范围、关键业务审计、客户/商机/合同/回款、任务缓存和看板未实现；没有把本次框架测试当作这些需求的验收。前端尚未建立，未执行浏览器业务流程、前后端联调、性能压测、部署或备份恢复。

阶段 1 只完成后端部分。阶段 0 的完整六模块接口清单和阶段 1 的前端骨架/联调仍需继续补齐。
