# CRM 本地 Redis 7 环境

2026-10-12 已在本机准备可供 CRM 使用的 Redis 7.0.15。Windows 原有 Redis 3.0.504 继续使用 6379；CRM 固定连接新的 6380 实例。

## 连接参数

```text
host: 127.0.0.1
port: 6380
database: 0
password: 未设置（本机开发实例）
version: Redis 7.0.15
```

不要把 CRM 配置成 6379。6379 是旧的 Windows Redis 服务，保留它是为了避免影响已有数据。

## 已完成的环境

- WSL 2，Ubuntu 24.04.5 LTS。
- Ubuntu 软件包 `redis-server` 和 `redis-tools` 版本 7.0.15。
- systemd 服务名：`crm-redis.service`。
- Redis 配置：`/etc/redis/crm-redis.conf`。
- 数据目录：`/var/lib/redis-crm`。
- 仅监听本机回环地址，protected mode 开启。
- AOF 与 RDB 持久化开启。

## 日常操作

在项目根目录 `E:\bishe\CRM` 的 PowerShell 中执行：

```powershell
& .\tools\redis.ps1 -Action Status
& .\tools\redis.ps1 -Action Start
& .\tools\redis.ps1 -Action Restart
& .\tools\redis.ps1 -Action Stop
```

`Status` 必须显示 Redis 7.x 和 PONG。`Start` 会在 WSL 实例已被回收时重新启动隐藏的保活进程；`Stop` 只停止 CRM 的 6380 实例。

当前用户的启动文件夹中有 `CRM-Redis7.vbs`，登录 Windows 后会自动唤醒 WSL 保活进程。WSL 的 systemd 服务不会单独保持实例常驻，因此不要只执行一次 `wsl ... systemctl start` 后就关闭所有 WSL 进程。

启动项路径为 `C:\Users\psc\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\CRM-Redis7.vbs`，调用项目的 `tools/redis.ps1 -Action Start`。保活进程带有独占锁，重复启动不会积累多个进程。Redis 由 Linux 的 `redis` 用户运行，保活程序只负责启动服务和保持 WSL 在线。

用户级 `C:\Users\psc\.wslconfig` 显式设置了 `[wsl2] localhostForwarding=true`。启动项和 WSL 配置属于当前电脑，不是项目内可随 Git 迁移的运行服务；换电脑后需重新准备环境。

如果 PowerShell 拒绝直接执行 `.ps1`，可以使用单次进程的执行策略参数，不必修改系统全局设置：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "E:\bishe\CRM\tools\redis.ps1" -Action Start
```

将 `Start` 换成 `Status`、`Restart` 或 `Stop` 即可执行相应操作。项目路径改变时，需同步修改登录启动项中的脚本路径。

如果手动执行 `wsl --shutdown`，6380 会暂时不可用。重新登录会触发启动项；也可以在项目根目录执行：

```powershell
& .\tools\redis.ps1 -Action Start
```

## 验证命令

Ubuntu 内：

```powershell
wsl -d Ubuntu-24.04 --user root --exec redis-cli -h 127.0.0.1 -p 6380 PING
wsl -d Ubuntu-24.04 --user root --exec redis-cli -h 127.0.0.1 -p 6380 INFO server
```

Windows 侧：

```powershell
& 'E:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6380 PING
& 'E:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6380 INFO server | Select-String 'redis_version'
```

预期分别得到 `PONG` 和 `redis_version:7.0.15`（补丁版本在安全更新后可能变化，但必须仍为 7.x）。

## 相关文件

- [WSL 安装脚本](../tools/setup-redis-wsl.sh)：只在 Ubuntu 24.04 中运行，检查候选包必须是 Redis 7 后安装并配置服务。
- [PowerShell 管理脚本](../tools/redis.ps1)：在 Windows 中启动、停止、重启和检查 CRM Redis。
- [验收记录](redis-verification.json)：Windows 连接、实际配置、临时键读写、服务与 WSL 实例重启后的数据保留、停止、隐藏启动项唤醒、保活进程去重以及旧 Redis 保留均已通过。临时测试键已清理。

本次没有重启或注销 Windows；登录启动项是从停止的 WSL 实例上直接调用验证的。CRM 尚无应用骨架，因此 Spring Boot 实际集成连接仍待开发时验证。
