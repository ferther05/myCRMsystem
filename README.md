# CRM 毕业设计项目

本目录 `E:/bishe/CRM` 是项目的固定开发根目录。后续前端、后端和数据库脚本都在这里编写；在 Codex 中直接打开此文件夹即可继续开发。

项目面向中小企业销售团队，使用 Spring Boot 3 + Vue 3，保留用户权限、客户、商机、合同回款、看板、任务日志六个模块。按毕业设计规模实现模块化单体，优先简单、容易理解和维护。

## 目录怎么用

```text
CRM/
├─ backend/          Spring Boot 后端、配置模板与测试
├─ frontend/         Vue 3 前端、页面、公共组件与依赖锁文件
├─ database/         18 表数据库设计、建表和初始化 SQL
├─ docs/             需求、决策、开发状态与环境说明
│  ├─ api/           全项目与各功能模块的接口说明、示例和规则
│  └─ reference/     原始参考材料与全文提取
├─ tools/            项目维护脚本、Redis 管理脚本
├─ .agents/skills/   Codex 使用的 13 个项目 skills
├─ output/           本机临时产物、检查证据与历史归档（不提交）
├─ AGENTS.md         Codex 的项目协作规则
└─ .git/             Git 版本记录
```

`.agents/skills/` 是当前生效的技能位置，保留原路径。`output/archive/` 中的技能副本是安装时的备份和暂存，不是第二套需要维护的技能。以后把生成的报告、截图和临时文件放入 `output/`，避免混到业务代码目录。

## 从这里开始看

- [数据库精简预览：18 张表](database/schema-preview.md)：本轮推荐设计，替代原来的 54 表方案；对应 SQL 位于 `database/001_create_database.sql`～`004_verify_schema.sql`。
- [项目资料导航](docs/README.md)：范围、业务决策、阶段状态和技能使用说明。
- [后端启动与开发说明](backend/README.md)、[前端目录说明](frontend/README.md)、[数据库目录说明](database/README.md)。
- [全项目接口说明与请求示例](docs/api/00-全项目接口说明与请求示例.md)、[账号认证与密码](docs/api/02-账号认证与密码.md)、[前端联调验证](docs/frontend-verification.md)。
- [Redis 7 使用说明](docs/redis-setup.md)：项目实例为 `127.0.0.1:6380`。

数据库预览的审阅副本放在 `E:/bishe/开始参考/CRM数据库精简预览.md`；项目内以 `database/schema-preview.md` 为维护源，后续修改时同步导出。旧版 `database-schema-preview.md` 保留供对照。

## 当前进度

2026-10-18 已完成 Spring Boot 3.5.16 + MyBatis-Plus 3.5.17 后端骨架，以及 JJWT 0.13.0 登录、当前用户、首次/普通改密和退出；真实连接 MySQL 8.0.41 的 `crm_system` 与 Redis 7/6380。已建立 Vue 3 / Element Plus 前端框架，并完成上述认证功能的页面与前后端联调。应用使用只可读写 CRM 库的 `crm_app` 账号，现有 18 表无需迁移。此前后端 15 项基础测试、15 项真实环境集成测试通过；本轮前端 3 项规则测试、构建和 51 项浏览器断言通过，使用临时账号，既有五个账号未改变。客户等业务接口/页面、完整数据权限和正式部署尚未完成。具体边界见 [开发状态](docs/development-status.md)。

在本目录的 PowerShell 中启动项目：

```powershell
& .\tools\backend.ps1 -Action Build
& .\tools\backend.ps1 -Action Start
& .\tools\backend.ps1 -Action Status
& .\tools\frontend.ps1 -Action Start
& .\tools\frontend.ps1 -Action Status
```

浏览器打开 **http://127.0.0.1:5173**，后端检查地址为 `http://127.0.0.1:8080/api/health`。首次拉取前端先运行 `& .\tools\frontend.ps1 -Action Install`，后续启动无需每次安装或重建数据库。前后端各用对应脚本的 `-Action Stop` 停止。后端脚本自动选用 JDK 17；完整操作见 [后端说明](backend/README.md) 与 [前端说明](frontend/README.md)。

`database/` 保存设计和 SQL；MySQL 服务的运行数据文件仍由 MySQL 管理。密码、JWT 密钥及本地连接配置使用环境变量或 Git 忽略的本地配置，不写入版本库。
