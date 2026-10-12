# 本机开发与 skills 运行环境

此记录针对当前 Windows 主机。换电脑后重新检查路径与版本，不修改系统环境变量来掩盖差异。

## 已检查

- PowerShell 为当前 shell。
- Maven 3.9.16：`E:/apache-maven-3.9.16/bin/mvn.cmd`，实际使用 Microsoft JDK 17.0.8.1。
- 直接 `java -version` 使用 Oracle JDK 25.0.4.1。项目启动应使用与 Maven 一致的 JDK 17，不直接假设 PATH 上的 java 正确。
- JDK 17 可执行文件：`C:/Program Files/Microsoft/jdk-17.0.8.101-hotspot/bin/java.exe`。
- Node.js 26.8.2 与 npm 11.19.1 在 PATH 可用；版本兼容性应在前端依赖定稿时重新核对。
- Windows 调用优先 `npm.cmd`/`npx.cmd`，避免 PowerShell 对 npm.ps1 的执行策略问题。
- `mysql.exe` 与 `redis-cli.exe` 在 PATH 可用，仅证明客户端存在，不证明服务运行、密码或连接已配置。
- Git 2.54.0 已安装；本项目已初始化 Git，`main` 跟踪 `origin/main`，远程为 `https://github.com/ferther05/myCRMsystem.git`。
- Git Credential Manager 2.7.3 已安装并配置为凭据助手；已验证远程读取，尚未验证推送权限。
- Playwright CLI 0.1.22 已下载到 npm 缓存并验证版本；项目提供 Windows wrapper。已启动独立会话访问 about:blank、读取快照与 HeadlessChrome/154.0.0.0 标识并关闭，结果见 `skills-installation.json`。这不是 CRM 业务验证。

## Git 仓库连接

2026-10-12 已将当前 `E:/bishe/CRM` 目录关联到用户指定的 GitHub 仓库。拉取并保留远程初始提交 `44a621f` 的 `README.md`；本地 skills、协作资料和工具纳入项目资料的首次本地提交，尚未上传。后续提交/推送按用户实际授权执行。

可用 `git remote -v` 核对地址、`git branch -vv` 核对跟踪关系、`git status` 查看待提交文件。项目已有 `.gitignore` 排除本地配置、构建产物和临时验证输出。

## 应用命令

当前无 pom.xml/package.json 和应用服务，因此尚无项目启动、构建或业务测试命令。创建骨架后记录真实目录、端口、wrapper、配置和测试命令。数据库密码/JWT 密钥写入本地环境配置并加入忽略列表，不写入本文档。

## Playwright 命令

从项目根目录执行：

```powershell
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --version
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --help
```

前端实际运行后，以它的真实 URL 打开浏览器，并 snapshot 获取元素引用后交互。截图、trace 放 `output/playwright/`；真实会话可能包含客户资料，不公开上传。
