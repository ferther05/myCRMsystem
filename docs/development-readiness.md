# CRM 开发前准备检查与下轮起步

最新更新：2026-10-18。数据库与 Spring Boot 后端已建立，真实连接 MySQL/Redis；Vue 3/Element Plus 前端框架及登录/首次改密等认证页面已完成联调。WSL 2/Ubuntu/Redis 7 环境和原有 Windows Redis 服务均保留。下列历史环境记录保留原检查范围，当前实现以对应最新条目为准。

## 开工结论

2026-10-17 最新补充：用户要求按毕业设计规模简化，并确认剩余问题按推荐方案落实。当前推荐 [18 表精简预览](../database/schema-preview.md)，已生成并执行 `database/001_create_database.sql`～`004_verify_schema.sql`。脚本只针对独立 `crm_system`，不会删除其他数据库；建库、建表、种子及只读结构校验均已成功。原 54 表＋1 可选表草案只作历史参考。

2026-10-17 业务口径补充：用户确认管理员审核开通、两个团队、查重、商机合同关系、报备转正、经过时长回收、赢单后保护与永久成交、商机关联跟进影响范围、待关闭三天升级、合同结束条件、回款计划换版规则，以及审计事务、待办唯一键和任务批次方案。初始化脚本含管理员、两个团队及四个演示账号；明文密码只写入被忽略的 `database/local-credentials.md`。

前后端均可启动，使用方式见 [后端说明](../backend/README.md)、[前端说明](../frontend/README.md)，实际检查结果见 [后端验证](backend-verification.md)、[认证验证](auth-verification.md)、[前端联调验证](frontend-verification.md)。阶段 1 的骨架范围完成；阶段 0 尚需补齐六模块接口清单与字段/权限契约，阶段 2 仍缺账号管理等功能。

参考资料、项目范围、需求覆盖、协作规则、13 个项目 skills 与 Git 连接已经具备。接下来重点是实际设计与运行配置，现有技能足够支持起步。

## 实际检查结果

- 项目 skills：13 个入口通过官方 quick_validate，当前配置未发现失效引用；最新结果见 [技能结构验证](skills-validation.json)。本轮没有修改技能正文。
- 需求：六个必选模块、三类角色与主要技术方向已记录；[需求覆盖](requirements-traceability.md) 已增加骨架基础项。后端框架部分完成，六模块业务功能仍未实现。
- Java：Microsoft JDK 17.0.8.1 可运行，路径为 `C:/Program Files/Microsoft/jdk-17.0.8.101-hotspot/bin/java.exe`；Maven 3.9.16 使用该 JDK。PATH 的 `java` 使用 Oracle 25.0.4.1，后续构建与启动需明确选定同一 JDK 17。
- Maven：本机默认平台编码为 GBK；项目创建时在实际构建配置中明确源码/资源 UTF-8，不通过更改系统全局设置掩盖差异。
- 前端工具：使用 Node.js 26.8.2、npm 11.19.1 完成依赖安装、构建与测试；本机另有 Node.js 20.20.2，未切换全局版本。实际兼容要求与依赖见 `frontend/package.json`、`package-lock.json`、`.node-version`。
- MySQL：MySQL80 服务运行，3306 监听；服务器版本 8.0.41。独立 `crm_system` 的 18 张表、种子、外键和关键索引已校验。已创建 `crm_app@127.0.0.1`，仅具 CRM 库 SELECT/INSERT/UPDATE/DELETE 权限；随机密码只写入忽略的 `backend/config/application-local.yml`。Spring Boot/Druid/JDBC/MyBatis 查询与 MyBatis-Plus 分页/乐观锁/时间填充通过真实验证；业务权限尚未实现。MySQL 会话使用 `+08:00`，避免依赖未导入的命名时区表。
- Redis：Windows 原有服务仍在 6379，`PING`/`INFO server` 确认版本为 **3.0.504**；本次保留其运行状态和数据，没有升级或覆盖。
- Redis 7 运行方式：已安装 WSL 2（WSL 3.0.1.0，内核 6.18.40.1）与 Ubuntu 24.04.5 LTS，在 Ubuntu 官方软件仓库安装 Redis **7.0.15**。已建立 `crm-redis.service`，独立监听 `127.0.0.1:6380`，数据目录 `/var/lib/redis-crm`，AOF/RDB 持久化开启；Ubuntu 内和 Windows 侧的 PING/INFO 均通过。当前用户启动文件夹的 `CRM-Redis7.vbs` 负责登录后唤醒 WSL 保活程序；`tools/redis.ps1` 已通过停止/启动/重启流程验证。Docker 不在 PATH，且不是该项目必选项。
- Redis 7 验收：临时键读写、服务与 WSL 实例重启后的数据保留、隐藏登录启动项的直接调用、保活进程去重及旧 Redis 保留均通过，临时键已清理。完整证据见 [Redis 验收](redis-verification.json)，操作见 [Redis 使用说明](redis-setup.md)。未实际重启/注销 Windows；2026-10-17 后端通过 StringRedisTemplate 的真实 PING，尚无缓存业务验证。
- Spec Kit：在沙箱外只读运行 `specify version` 确认 CLI 1.1.2；沙箱内的启动器曾报路径解析错误，不能据此认定工具损坏。当前项目没有 `.specify/`、`specs/` 或 speckit 项目技能，尚未初始化。
- Git：远程地址为 `https://github.com/ferther05/myCRMsystem.git`，本地 main 跟踪 origin/main。基于本地远程引用，main 领先 1 个提交（353f379）；此前技能整合及本轮检查资料仍有未提交修改。本轮未访问 GitHub 核对最新远程状态或验证推送权限。
- 后端：已有 `backend/pom.xml`，锁定 Java 17 / Spring Boot 3.5.16 / MyBatis-Plus 3.5.17 / Druid 1.2.28 / JJWT 0.13.0；2026-10-18 完成 15 项基础与 15 项真实环境集成测试，包含 JWT、首次改密、并发和审计回滚，保留 Redis 故障 HTTP 503 验证。命令入口 `tools/backend.ps1`，接口包括健康检查及 `/api/auth` 登录/本人信息/改密/退出；细节见 [认证验证](auth-verification.md)。
- 前端：`frontend/` 已有 Vue 3/Vite/Element Plus/Pinia/Router/axios/ECharts 骨架、布局和认证页面。开发端口 5173，代理后端 8080；`tools/frontend.ps1` 提供安装、启停、状态、测试与构建。3 项规则测试、全部 15 个 Vue 文件编译、构建与 51 项浏览器断言通过，见 [前端验证](frontend-verification.md)。业务页面与完整数据权限仍待实现。

## 开发起步时要补齐的内容

### 环境与本地配置

数据库脚本已按独立 `crm_system` 执行，执行顺序和验证命令见 [数据库说明](../database/README.md)。应用最小权限账号和本地 JDBC 已配置，应用启动不重跑数据库初始化。凭据只在环境变量或 Git 忽略的本地配置中；未来 JWT 密钥同样处理，不写入版本库。

准备符合基线的 Redis 7。已选择 WSL 2 + Ubuntu 24.04.5 的隔离实例，连接参数固定为 `127.0.0.1:6380`，并使用独立数据目录；开发配置不得回退到 6379。当前 6379 上的 Redis 3.0 服务已保留，不作为 CRM 依赖。

前后端兼容版本已锁定并实际构建，具体组合见 [环境说明](environment.md) 与实际 pom/package-lock。原参考版本号不直接作为最终锁定值。

### 数据模型、权限与接口

按毕业设计尺度形成覆盖六模块的实体关系、字段/枚举、唯一键/版本/金额精度及数据权限设计。采用共用表覆盖附件资料版本、审批和待办接收人，保留回款计划版本/期次等真正需要独立的数据关系，不按原 54 表草案逐表实现。

上述已有 [18 表设计](../database/schema-preview.md)，对应可执行 SQL 与验证脚本已放入 `database/`；脚本已在本机独立库实际执行并记录结果。数据库创建不代表 JDBC 或业务功能验收完成。

统一表名、接口字段、分页、HTTP/业务错误和状态动作。已有 `{ code, message, data }` 与 `{ total, list }` 的实际 Java 实现，[接口总文档](api/00-全项目接口说明与请求示例.md) 覆盖当前健康与认证接口；后续功能同步总文档和独立模块文档。数据库初始化已完成；完整六模块接口清单仍待编写。

页面设计优先确定后台布局、导航、列表/详情/表单的公共约定，再逐模块完善原型。不需要在第一轮把所有页面设计完才能建立骨架。

### 业务口径

[业务决策记录](business-decisions.md) 已记录 2026-10-17 用户确认的 BD-01/02/03/04/05/06/07/08/10/11/12，以及客户查重和商机合同关系。相关业务规则实现仍未开始；本轮数据库/框架验证不等于这些生命周期、权限和金额规则验收。

公海回收与报备涉及 BD-01/03/04，客户终态与回款金额涉及 BD-02/05，跟进授权涉及 BD-11；相关功能实现时按已确认口径落地。审计、待办和调度涉及 BD-06/07/10，实施时给出符合既有约束的具体方案；BD-13 用于统一里程碑，不据此擅自承诺日期。

不要求用户先一次回答全部 13 项。未决事项只阻塞依赖它的行为，环境、工程骨架、页面公共结构及无冲突契约可继续推进。

### 开发工作流

若决定使用 Spec Kit，在现有 CRM 工作区初始化后建立项目原则，再按功能规格、计划和任务推进，保留现有 Git、AGENTS 与 13 个 skills。CLI 存在不等于项目已初始化；Spec Kit 不是基础编码的必要依赖。

现有资料与技能修改尚未形成新的提交。后续提交/推送按当前授权执行，不能用“已连接 GitHub”替代远程备份成功。

## 下一轮建议顺序

1. 读取本文件、[项目范围](project-scope.md)、[业务决策](business-decisions.md) 和 [阶段状态](development-status.md)，沿用已有资料与用户改动。
2. 沿用已验证的后端版本、`crm_app` 本地配置和 Redis 7/6380；需要启动时使用 `tools/backend.ps1`，不重复创建数据库或应用账号。
3. 以 18 表设计统一各模块实体、字段/枚举、权限边界和接口契约，遵守已确认的简化偏好 DS-01；若使用 Spec Kit，仍需在现有目录实际初始化。
4. 沿用现有前后端骨架、认证、角色菜单和主题，继续阶段 2 的管理员账号/团队管理、内部账号申请、个人资料维护和参数字典；业务接口按功能逐项定稿。沿用现有技术栈，不引入微服务。
5. 对新增产出执行相应构建、接口/权限或前后端联调。已有后端基础检查通过，不反复重建已完成环境；只在改动影响到它时回归。

阶段 0 验收还要求初始化 SQL 可在明确的空库执行、接口清单覆盖六模块及相关业务规则统一；仅启动骨架不能把该阶段全部标为完成。

## 下一轮可直接使用的请求

```text
请继续 CRM 的阶段 0 和阶段 2 开发；前后端骨架、登录/JWT/首次与普通改密、退出和个人信息查询已完成联调。
先阅读 docs/development-readiness.md、AGENTS.md 和现有需求资料。
沿用已有前后端、本地配置和数据库，逐项补齐六模块接口契约，继续账号管理、内部申请、个人资料维护及相关权限。
遵循既定 Java 17 / Spring Boot 3 / MyBatis-Plus / MySQL 8 / Redis 7
和 Vue 3 / Element Plus 技术栈。
涉及关键待定业务规则时提出具体选择，继续推进不受影响的工作，记录真实验证结果。
```
