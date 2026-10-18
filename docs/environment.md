# 本机开发与 skills 运行环境

此记录针对当前 Windows 主机。换电脑后重新检查路径与版本，不修改系统环境变量来掩盖差异。

## 已检查

- PowerShell 为当前 shell。
- Maven 3.9.16：`E:/apache-maven-3.9.16/bin/mvn.cmd`，实际使用 Microsoft JDK 17.0.8.1。
- 直接 `java -version` 使用 Oracle JDK 25.0.4.1。项目启动应使用与 Maven 一致的 JDK 17，不直接假设 PATH 上的 java 正确。
- JDK 17 可执行文件：`C:/Program Files/Microsoft/jdk-17.0.8.101-hotspot/bin/java.exe`。
- Node.js 26.8.2 与 npm 11.19.1 在 PATH 可用；2026-10-18 已用该环境安装锁定依赖、通过前端构建与规则测试；Vite 8.3.4 要求 `^20.19.0 || >=22.12.0`，与本机兼容，未切换全局 Node。
- Windows 调用优先 `npm.cmd`/`npx.cmd`，避免 PowerShell 对 npm.ps1 的执行策略问题。
- MySQL80 服务运行，3306 监听；服务器为 MySQL 8.0.41。2026-10-17 已完成独立 `crm_system` 的 18 表初始化、种子和只读结构校验；表均为 InnoDB、`utf8mb4_0900_ai_ci`。随后创建 `crm_app@127.0.0.1`，权限仅为 `crm_system.*` 的 SELECT、INSERT、UPDATE、DELETE；Spring Boot/Druid/JDBC/MyBatis 和 MyBatis-Plus 的真实查询、分页、乐观锁验证通过。管理员密码未写入项目文件，应用随机密码只保存在 Git 忽略的 `backend/config/application-local.yml`。本机 MySQL 缺少命名时区表，JDBC 使用 `%2B08:00`，会话时区验证为 `+08:00`；Java 业务时钟使用 `Asia/Shanghai`。
- Windows 原有 Redis 服务继续运行在 6379；只读 PING/INFO 确认版本为 Redis 3.0.504（Windows 64 位），本次未停止、升级或改写它。
- 已准备项目专用 Redis 7：WSL 2（WSL 3.0.1.0，内核 6.18.40.1）中的 Ubuntu 24.04.5 LTS，Ubuntu 软件包 `redis-server`/`redis-tools` 为 7.0.15。CRM 专用 systemd 服务 `crm-redis.service` 监听 `127.0.0.1:6380`，绑定本机、启用 protected mode、启用 AOF/RDB 持久化，数据目录为 `/var/lib/redis-crm`。Windows 侧 `127.0.0.1:6380` 的 PING/INFO 已验证返回 PONG 与 `redis_version:7.0.15`。
- WSL systemd 服务不会单独保持分发常驻；已在当前用户启动文件夹配置隐藏的 `CRM-Redis7.vbs`，登录后启动 `/usr/local/sbin/crm-redis-keepalive` 维持 Ubuntu 实例和 Redis 服务。项目根目录 [Redis 管理脚本](../tools/redis.ps1) 支持 `Start`、`Stop`、`Restart`、`Status`，只操作 6380。执行 `wsl --shutdown` 后需重新登录或运行 `Start` 恢复 6380；6379 旧服务不受影响。
- Redis 7 的真实配置、Windows 侧读写、服务重启与 WSL 实例重启后的数据保留、停止/启动、登录启动项的直接调用以及保活去重均已通过；证据见 [Redis 验收记录](redis-verification.json)，使用说明见 [Redis 环境](redis-setup.md)。未实际重启/注销 Windows。2026-10-17 已通过 Spring Boot `StringRedisTemplate` 的真实 PING 集成检查，缓存业务及调度任务尚未实现。
- Git 2.54.0 已安装；本项目已初始化 Git，`main` 跟踪 `origin/main`，远程为 `https://github.com/ferther05/myCRMsystem.git`。
- Git Credential Manager 2.7.3 已安装并配置为凭据助手；已验证远程读取，尚未验证推送权限。
- Playwright CLI 0.1.22 已下载到 npm 缓存并验证版本；项目提供 Windows wrapper。已启动独立会话访问 about:blank、读取快照与 HeadlessChrome/154.0.0.0 标识并关闭，结果见 `skills-installation.json`。这不是 CRM 业务验证。
- Spec Kit CLI 1.1.2 在沙箱外只读版本检查通过；当前 CRM 尚未初始化 `.specify/` 或 `specs/`。沙箱内启动器的路径解析错误不代表主机安装损坏。

2026-10-12 开发前复查：上述 JDK 17、Maven、PATH Java、Node/npm、Git 和服务版本已重新核对。另有可执行的 Node.js 20.20.2，未切换全局版本；本次已安装 WSL 2/Ubuntu 24.04 并完成 Redis 7.0.15 隔离实例，Docker 仍不是项目必选项。完整检查范围、剩余准备及下一轮起步见 [开发准备检查](development-readiness.md)。

## Git 仓库连接

2026-10-12 已将当前 `E:/bishe/CRM` 目录关联到用户指定的 GitHub 仓库。拉取并保留远程初始提交 `44a621f` 的 `README.md`；本地 skills、协作资料和工具纳入项目资料的首次本地提交，尚未上传。后续提交/推送按用户实际授权执行。

可用 `git remote -v` 核对地址、`git branch -vv` 核对跟踪关系、`git status` 查看待提交文件。项目已有 `.gitignore` 排除本地配置、构建产物和临时验证输出。

## 应用命令

后端为 `backend/pom.xml`，JDK 17；锁定 Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Druid 1.2.28。实际 jar 中包含 MyBatis 3.5.19、MyBatis-Spring 3.0.5、Spring Security 6.5.11、Spring Data Redis 3.5.13、MySQL Connector/J 9.7.0，已对本机 MySQL 8.0.41 验证兼容。原文档的版本号为参考基线。

从项目根目录运行 `& .\tools\backend.ps1 -Action Build` 构建，`-Action Start` 后台启动，`-Action Status` 检查，`-Action Stop` 停止本脚本管理的实例；`-Action Run` 在当前终端运行。监听 `127.0.0.1:8080`，检查接口为 `GET /api/health`。脚本选择 JDK 17，不改变全局 JAVA_HOME；需要时用 `CRM_JAVA_HOME`、`CRM_MAVEN_HOME` 覆盖工具位置。

`-Action Test` 运行无需外部依赖的基础测试，`-Action Verify` 通过 Maven profile `local-integration` 运行真实 MySQL/Redis 集成验证。2026-10-17 复核结果为 8 项基础测试、4 项集成测试通过，包含独立实例 Redis 连接失败时的真实 HTTP 503；运行 jar 的真实 HTTP 检查、停止/重启及测试数据回滚也通过。详情见 [验证记录](backend-verification.md)。

2026-10-18 已增加 JJWT 0.13.0，登录、当前用户、首次/普通改密和退出接口已实现；15 项基础与 15 项真实环境集成测试通过，认证验证见 [认证记录](auth-verification.md)。不新增表或字段；会话版本复用 `sys_user.version`，具体失效行为见 [接口约定](backend-api.md)。

本地凭据和随机 JWT 签名密钥位于 `backend/config/application-local.yml`，由 `local` profile 加载；IDEA 工作目录必须是 `backend/`，入口为 `com.bishe.crm.CrmApplication`。可提交模板为同目录的 `application-local.yml.example`；可通过 `CRM_JWT_SECRET`、`CRM_JWT_TTL_SECONDS` 覆盖 JWT 配置。缺失或不合格密钥会阻止启动，密钥不打入 jar。应用日志与后台输出位于 `output/backend/`，jar 位于 `backend/target/crm-backend.jar`，均被 Git 忽略。客户等业务模块尚未实现。

前端入口为 `frontend/package.json`，使用 Vue 3.5.44、Vite 8.3.4、Element Plus 2.14.7、Pinia 4.1.0、Vue Router 4.6.4、axios 1.20.0、ECharts 6.1.0；直接依赖精确版本与 `package-lock.json` 已保存。执行 `& .\tools\frontend.ps1 -Action Install` 安装锁文件，`-Action Start` 后台启动本机 5173，`-Action Status` 检查网页与 `/api` 代理，`-Action Stop` 停止受管理实例；`-Action Run` 前台运行，`-Action Test` 规则测试，`-Action Build` 生成 `frontend/dist/`。无需在前端保存数据库或 JWT 签名密钥；前端只请求 `/api`，代理默认指向 8080，可用 `CRM_API_TARGET` 覆盖。

2026-10-18 前端框架与认证页面联调通过，3 项规则测试与 51 项浏览器断言通过；临时验证账号已清理，既有账号未更改。页面风格使用用户选定的 A 方案；启动、目录和剩余范围见 [前端说明](../frontend/README.md)、[前端验证](frontend-verification.md)。本轮不包含正式 Nginx 部署。

## Playwright 命令

从项目根目录执行：

```powershell
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --version
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --help
```

前端当前地址为 `http://127.0.0.1:5173`，通过 snapshot 获取元素引用后交互。2026-10-18 已验证登录、改密、身份、菜单与会话的真实浏览器流程；截图放 `output/playwright/`，结果见 [前端验证](frontend-verification.md)。真实会话可能包含客户资料，不公开上传。

2026-10-18 用户权限增量：本轮依赖版本和端口不变，18 表无迁移。`Verify` 共 15 基础 + 23 集成检查通过，前端 6 项规则及生产构建通过；新页面和真实浏览器验证见 [用户权限验证](user-permissions-verification.md)。后端 Stop 在保留进程名称/启动时间校验后改用 Process.Kill，解决本 PowerShell 的 Stop-Process 空引用；构建前停止、构建后重新启动。
