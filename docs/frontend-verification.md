# 前端框架与认证联调验证记录

验证日期：2026-10-18，业务时区 Asia/Shanghai。范围为 REQ-FOUNDATION、REQ-AUTH、REQ-UI 中的前端基础框架、登录/JWT 接入、首次/普通改密、本人信息查询、退出、角色菜单与路由入口。依据用户选定的 A 风格、详细设计第 11 章、过程指南阶段 1/2 和当前真实后端契约。

## 环境与命令

Windows PowerShell，Node 26.8.2、npm 11.19.1；依赖以 `frontend/package-lock.json` 为准。浏览器使用项目 Playwright CLI 0.1.22 的独立 Chromium 会话 `crm-auth`。前端 `http://127.0.0.1:5173`，开发代理转发到现有后端 `http://127.0.0.1:8080`；后端真实连接 MySQL 8.0.41 / `crm_system` 和 Redis 7.0.15 / 6380。

在 CRM 根目录运行：

```powershell
& .\tools\frontend.ps1 -Action Install
& .\tools\frontend.ps1 -Action Test
& .\tools\frontend.ps1 -Action Build
& .\tools\frontend.ps1 -Action Start
& .\tools\frontend.ps1 -Action Status
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 -s=crm-auth open http://127.0.0.1:5173
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 -s=crm-auth snapshot
```

本机最初使用同等的 `npm install --registry=https://registry.npmjs.org --no-fund` 生成锁文件，审计 97 个包、0 个已报告漏洞；换机/复现使用 `Install` 对应的 `npm ci`。漏洞结果只代表安装时 npm 的报告。临时浏览器流程脚本位于 `output/frontend-browser-*.js`，由 `output/run-frontend-browser.ps1` 调用 CLI `run-code --filename` 执行；测试数据准备/清理由 `output/frontend-fixtures.ps1` 完成。这些是本机验收辅助脚本，未作为完整长期端到端测试套件提交。

## 已通过

- 3 项 Node 规则测试：站内跳转与认证循环限制；新密码长度/字符/字母数字边界；登录密码 UTF-8 字节限制，防止 BCrypt 截断。
- `npm run build` 成功。Element Plus 按实际组件注册，构建最大 JS 分块约 450.05 kB、gzip 153.49 kB，无超 500 kB 分块警告；所有 15 个 Vue 文件另经 Vue compiler-sfc 解析及脚本/模板编译检查，包含尚未被业务页引用的公共组件。
- 19 项登录/首次改密断言：匿名保护路由跳转、空表单、错误密码反馈及清空、强制改密、直接访问与刷新不能绕过、弱密码/确认不一致、后端原密码错误、成功后清除会话、旧密码失效、新密码登录、真实身份与健康状态、专员菜单、刷新恢复、个人中心刷新、普通改密返回、专员直达系统页拒绝、待开发页面标识。
- 18 项会话/角色/适配断言：退出确认与本地清理、实际后端旧令牌拒绝、退出后路由限制、390px 登录/工作台无横向溢出、抽屉导航后关闭、健康失败/重试、身份服务失败保留令牌并恢复、经理菜单/直达拒绝、管理员菜单/入口、桌面侧栏收起、404、无效令牌清理。
- 14 项补充断言：390px 改密无横向溢出、新旧密码相同拒绝、普通改密后旧令牌撤销与新密码登录、退出失败保留会话并成功重试、同账号在后端重新登录撤销原会话、跨标签页换身份时隐藏旧页面并恢复新身份、跨页退出、没有认证 Cookie、用户名到密码的键盘顺序。
- 最终登录页的 CLI `console warning` 收集结果为错误 0、警告 0；完整浏览器日志另包含错误路径测试预期产生的 HTTP 401/503 资源加载记录，以及开发服务重启时的连接提示。截图已实际查看。51 项为断言数，包含同一业务流程内的多个检查，不是 51 个独立测试套件。
- 前端管理脚本的停止、重新后台启动、HTTP 200 与代理健康检查通过。最终前端 5173 与后端 8080 可访问，MySQL/Redis 均为 UP。
- 主题主要文字、按钮、侧栏、输入边界及占位文字对比度已计算，具体数值见 [页面与主题约定](ui/frontend-foundation.md)。这不是全站无障碍认证。

初次浏览器验收脚本在待开发页面标题检查处因预期文案与实际文案不符中断，改用实际标题后完整重跑该组 19 项并通过。核对中还补足了跨标签页身份同步、迟到身份响应与退出/改密后的令牌清理保护，并将组件从整库注册调整为按需注册；后续会话组及最终构建通过。

## 数据隔离与证据

验证创建独立临时团队与 ADMIN/MANAGER/SPECIALIST 三个临时账号，真实调用后端和数据库；未通过页面操作既有管理员或四个演示账号。测试结束已删除临时账号、对应密码审计与临时团队，并比对原五个账号的首次改密标志、版本、最后登录时间和密码哈希摘要，结果均未改变。没有数据库结构迁移，没有写入客户/商机/合同/回款数据。

健康 503、本人信息 503 和退出 503 由浏览器请求拦截模拟，用于验证错误恢复界面；不将其称为本轮真实依赖宕机测试。解除拦截后的恢复请求调用真实后端。后端实际依赖故障、JWT 到期/伪造、并发改密与审计回滚证据见 [原后端记录](backend-verification.md) 和 [认证记录](auth-verification.md)，本轮没有重复执行整套 Maven 测试。

本机证据位于 Git 忽略的 `output/`：

- `frontend-verification.json`：本轮检查汇总、51 项浏览器断言与数据清理结果。
- `frontend-browser-auth.json`、`frontend-browser-sessions.json`、`frontend-browser-edge.json`：三组浏览器结果。
- `frontend-cleanup.json`：临时数据清理与原账号一致性结果。
- `playwright/login-desktop.png`、`login-mobile.png`、`password-desktop.png`、`password-mobile.png`、`workbench-desktop.png`、`workbench-mobile.png`、`workbench-admin-desktop.png`：实际页面截图，工作台中显示的是临时联调账号。
- `frontend/stdout.log`、`frontend/stderr.log`：本机开发服务日志。

临时含凭据的 fixture JSON 和运行时代码文件已删除。源码、接口示例和前端资源不保存实际账号密码、后端数据库密码或 JWT 签名密钥。

## 尚未执行与范围限制

未进行正式 Nginx 部署、生产域名/HTTPS、真实重启 Windows、Firefox/WebKit 兼容性及负载/性能压测。生产构建通过不等于正式发布通过。

公共表格/分页、状态标签和 ECharts 容器已建立并编译，尚未在具体业务数据中联调或验证。工作台不展示虚构统计。个人中心目前只读；管理员账号/团队管理、内部申请审批、个人资料编辑、字典参数、完整行级/字段权限与客户等业务页面仍待后续开发。本轮完成前后端骨架与基础认证切片，阶段 2 和六模块整体验收仍未完成。
