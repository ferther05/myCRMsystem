# myCRMsystem

一个面向中小企业销售团队的 CRM 毕业设计项目，采用前后端分离的模块化单体结构。仓库只保存可运行的技术部分：后端、前端、数据库 SQL、启动脚本和项目专用 Codex skills。需求分析、原始参考材料、详细接口文档、测试报告、真实凭据和运行输出保留在开发电脑本地，不进入远程代码仓库。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Spring Security 6、JWT、MyBatis-Plus、MySQL 8、Redis 7。
- 前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、axios、ECharts。
- 工程结构：模块化单体，前后端分离；金额使用 DECIMAL/BigDecimal，业务状态由后端事务和条件更新控制。

## 目录

```text
backend/                 Spring Boot 后端源码、测试和配置模板
frontend/                Vue 3 前端源码、依赖锁文件和构建配置
database/                建库、表结构、迁移和可选演示数据 SQL
tools/                   后端、前端和 Redis 的启动/停止/验证脚本
.agents/skills/          项目专用 Codex skills 及其校验参考
```

后端源码入口是 backend/src/main/java/com/bishe/crm/CrmApplication.java，前端入口是 frontend/src/main.js。业务模块位于后端 module/ 和前端 src/views/，按 auth、system、customer、opportunity、contract、analytics 等目录划分。

## 当前已完成

进度更新：2026-10-30。合同五步材料核验、异常/撤回恢复和本阶段已核验跟进推进已实现；完整验证通过后，最终格式/提示文字调整也已重新构建。
源码已补充文件、类型、显式方法及前端函数用途说明，并提供前后端 EditorConfig。Redis 目前已配置连接和健康检查，尚未实现业务缓存。

当前版本已完成并经过代码构建、真实 MySQL/Redis 集成验证的主要部分：

- 登录、JWT、当前用户、首次修改密码、普通修改密码、退出和单会话撤销。
- 管理员账号管理、团队角色范围、账号申请审核、启停和密码重置。
- 客户档案、联系人、等级标签、字典、客户查询、公海领取和客户详情关联商机；行业与标签提供更多中文选项。
- 商机创建、归属、协作、报价和自动阶段推进；每次前进需当前阶段、本轮至少一条经理/管理员已核验的有效跟进，旧阶段/旧轮次不可复用。
- 合同登记草稿、合同资料版本、经理核验/退回、合同附件受保护下载。
- 功能17合同五步：在线登记草稿、登记核对、合同签署与上传、文件待确认、文件已确认。草稿是可反复保存的表单，无需草稿文件；负责人/管理员提交，经理/管理员核对，负责人可撤回待审表单。实际文件多版本保留，只核验最新一版；三角色可异常/撤回，管理人员可恢复暂停前步骤并重查材料，历史保留。
- 合同核验通过后自动参与商机赢单闸门；系统不生成合同正文、不发起电子签、不判断法律效力。
- 数据看板、前端路由、角色页面、固定分页和统一接口请求基础已接通。

最新验证结果（2026-10-30合同保存错误修复后）：15项后端基础测试、66项真实MySQL/Redis集成测试全部通过；前端12项测试和生产构建通过。合同登记支持保存前格式校验、字段错误定位及固定按钮区失败提示；错误数据不会写库，修正后可继续保存。当前本地数据库为20张表，迁移文件已编号到019_contract_registration_form.sql。

## 尚未纳入完成范围

合同回款计划、分期到账、逾期与结清、合同终止审批、OSS/对象存储、外部电子签平台、复杂多主合同和完整生产部署仍需后续开发。演示 SQL 仅用于本地调试，不应直接作为生产数据。

## 本地运行

### 准备依赖

- JDK 17、Maven 3.9 或可用 Maven。
- Node.js >=22.12.0 和 npm。
- MySQL 8，准备一个 CRM 数据库账号。
- Redis 7；项目脚本默认使用 127.0.0.1:6380，tools/redis.ps1 依赖 WSL 中的 Ubuntu-24.04 和项目 Redis 服务。

### 初始化数据库

新建空库时，在 MySQL 管理员工具中执行：

```text
database/001_create_database.sql
database/002_schema.sql
database/003_seed_demo_data.sql       # 可选：创建本地演示账号和基础数据
database/018_contract_workflow_and_stage_follow.sql  # 补充当前行业/标签
database/019_contract_registration_form.sql  # 在线登记表及旧批准事实兼容
```

002_schema.sql 是当前完整结构的空库初始化脚本，已包含本版本所需表结构。不要把所有编号迁移和演示数据 SQL 再对新库盲目执行；006及以后的旧文件用于相应版本旧库升级或明确选择的本地演示场景；018可在最新版002/003后执行以补齐当前字典。旧库按缺失编号顺序升级，019最后执行；不要在018之后单独重跑旧016回写旧进度。应用账号可由管理员单独创建，database/005_create_app_user.sql.example 仅是模板。不要把真实数据库密码写入仓库。

### 配置和启动

复制 backend/config/application-local.yml.example 为同目录的 application-local.yml，填入本机数据库密码、Redis 地址和随机 JWT 密钥。该文件已被 .gitignore 排除。模板默认数据库名为 crm_system，默认应用账号为 crm_app。

在仓库根目录 PowerShell 中执行：

```powershell
& .\tools\redis.ps1 -Action Start  # 仅当已配置项目要求的 WSL Redis 服务时
& .\tools\backend.ps1 -Action Build
& .\tools\backend.ps1 -Action Start
& .\tools\frontend.ps1 -Action Install
& .\tools\frontend.ps1 -Action Start
```

前端地址：http://127.0.0.1:5173；后端健康检查：http://127.0.0.1:8080/api/health。运行前需确保 MySQL 可连接，并有 Redis 7 在配置的地址/端口运行。仓库中的 Redis 脚本管理已准备好的 WSL Ubuntu-24.04 服务；新电脑尚未安装或配置该服务时，应先安装 Redis 7 并按本机环境配置连接，不能只靠该脚本完成 WSL 初始化。

检查和停止：

```powershell
& .\tools\backend.ps1 -Action Status
& .\tools\frontend.ps1 -Action Status
& .\tools\redis.ps1 -Action Status
& .\tools\frontend.ps1 -Action Stop
& .\tools\backend.ps1 -Action Stop
```

### 验证

```powershell
& .\tools\backend.ps1 -Action Test
& .\tools\backend.ps1 -Action Verify
& .\tools\frontend.ps1 -Action Test
& .\tools\frontend.ps1 -Action Build
```

后端完整验证会使用隔离测试数据并在结束后清理；不要在生产数据库执行这些测试。运行日志、测试报告、附件和备份保存在本地 output/，不会上传。

## Codex skills

.agents/skills/ 中的项目 skills 与代码一起保存，打开本仓库后可直接用于后续开发：

- springboot-mybatis-development：Java、Spring Boot、MyBatis、SQL。
- vue3-elementplus-development：Vue、Element Plus、Pinia、路由和页面。
- crm-business-state：客户公海、商机阶段、合同签署状态联动。
- crm-security-permissions：JWT、角色、团队数据范围、附件权限。
- crm-jobs-cache：定时任务、Redis 统计和缓存。
- crm-design-contracts：实体、数据库和接口契约。
- crm-ui-design：页面结构和视觉规范。
- crm-verification：功能、权限、数据库和浏览器验证。
- crm-deployment-recovery：构建、启动、迁移、备份和恢复。
- crm-spec-delivery、crm-graduation-delivery：需求追踪、进度和毕业交付材料。
- playwright、security-threat-model：浏览器实测与用户明确要求的威胁建模。

后续开发请先阅读根目录的协作规则文件（本地维护），再根据改动匹配 skill；新增业务功能时同步维护本地接口说明和阶段记录。
