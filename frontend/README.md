# CRM 前端启动与开发

2026-10-18 已建立 Vue 3 前端，采用用户确认的浅灰页面、白色卡片、深蓝侧栏和蓝色主按钮。已接通真实后端的登录、当前用户、首次/普通改密、退出及健康检查；客户等业务页面目前是明确标注“待开发”的路由骨架。

## 启动

在项目根目录 `E:/bishe/CRM` 的 PowerShell 中执行：

```powershell
# 首次拉取或依赖锁文件更新后安装
& .\tools\frontend.ps1 -Action Install
# 后端需已构建，并准备好 MySQL 和项目 Redis 7/6380
& .\tools\backend.ps1 -Action Start
& .\tools\frontend.ps1 -Action Start
& .\tools\frontend.ps1 -Action Status
```

浏览器打开 **http://127.0.0.1:5173**。使用本地 [初始化账号说明](../database/local-credentials.md) 中的账号和密码；该文件含凭据，已被 Git 忽略。首次登录会进入强制改密页。不要将该文件或密码复制到前端源码。

`Start` 后台运行，`Run` 在当前终端运行；`Stop` 仅停止该脚本管理且进程身份核对一致的前端。日志位于 `output/frontend/`。Vite 固定监听本机 5173；端口被占用时报告错误，不自动终止其他程序或切换端口。

也可以在 `frontend/` 运行 `npm.cmd ci`、`npm.cmd run dev`。目前实际验证环境为 Node **26.8.2**、npm **11.19.1**；`.node-version` 记录本机版本。Vite 8.3.4 的 Node 要求为 `^20.19.0 || >=22.12.0`，项目声明 `>=22.12.0`；换机后使用受维护且满足约束的 Node，执行 `npm ci` 复现锁文件，不直接整体升级依赖。

## 依赖与目录

依赖版本已精确锁定：Vue 3.5.44、Vite 8.3.4、Element Plus 2.14.7、Pinia 4.1.0、Vue Router 4.6.4、axios 1.20.0、ECharts 6.1.0；使用 JavaScript 和 Vue 组合式 API。

```text
src/
├─ api/                 axios 统一请求、认证与健康接口
├─ assets/styles/       CRM 主题与公共样式
├─ components/          品牌、标题、表格分页、状态标签、图表容器
├─ layouts/             侧栏、顶部导航、账号菜单、手机抽屉
├─ router/              路由、模块菜单及角色约定
├─ store/               Pinia 身份与会话
├─ utils/               令牌存储、密码校验、安全内部跳转及规则测试
└─ views/               auth、workbench、account、common
```

## 接口与会话

前端请求 `/api`，开发/预览代理默认转发到 `http://127.0.0.1:8080`，配置在 `vite.config.js`；需要时在启动前设置 `$env:CRM_API_TARGET`。前端通常无需 `.env`。数据库密码、JWT 签名密钥等仅属于后端，不能放入任何 `VITE_*` 变量或网页资源。

仅将 JWT 保存至 `localStorage` 的 `crm.access-token`；密码不持久化，用户/角色不从本地存储信任加载。保护页面导航与刷新时调用 `GET /api/auth/me`，首次改密前只开放改密页，401 清理失效身份，临时连接失败保留令牌并允许重试。跨标签页会话变化会隐藏旧身份页面。

角色菜单辅助导航；后端仍负责所有业务权限。当前已实现账号管理、内部申请审核、个人资料编辑、用户/团队与客户基础查询范围；商机协作、业务写入、附件与导出权限随各模块接入。没有公众自助注册、忘记密码或周期性刷新令牌；资料保存会返回并替换新令牌。模块操作与技术说明见 [用户与权限管理](../docs/module-guides/01-用户与权限管理.md)。

## 验证与构建

```powershell
& .\tools\frontend.ps1 -Action Test
& .\tools\frontend.ps1 -Action Build
```

以上命令在 CRM 根目录执行；在 `frontend/` 可分别运行 `npm.cmd test`、`npm.cmd run build`。测试为 Node 内置规则测试，不需要另装测试框架。构建产物在 `frontend/dist/`，不提交到 Git。Element Plus 仅注册基础框架使用的组件，页面按路由加载；ECharts 容器已准备，当前业务页面未生成图表或虚构统计。

在 `frontend/` 运行 `npm.cmd run preview` 可在本机 4173 检查构建产物，并通过同一 `/api` 代理访问后端；Vite dev/preview 均用于本地验证。正式部署时由 Nginx 提供 `dist/`、配置 history fallback 与 `/api` 反向代理，本轮尚未部署。

真实验证记录见 [前端验证](../docs/frontend-verification.md)。接口与页面对应见 [账号认证与密码](../docs/api/02-账号认证与密码.md)，全项目接口统一在 [docs/api/](../docs/api/00-全项目接口说明与请求示例.md) 维护；后续新增功能必须同步总文档与模块文档。
