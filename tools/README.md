# 项目维护工具

- `validate_skills.py`：校验 13 个项目 skills 的结构与本地文档链接，结果保存为 `docs/skills-validation.json`。
- `requirements-skills.txt`：技能校验脚本所需的 Python 依赖。
- `redis.ps1`：Windows 侧管理项目 Redis 7 实例，操作见 [Redis 使用说明](../docs/redis-setup.md)。
- `backend.ps1`：后端构建、基础测试、真实依赖验证、前台/后台运行、停止与健康检查；固定选用 JDK 17。操作见 [后端使用说明](../backend/README.md)。
- `frontend.ps1`：前端依赖安装、规则测试、构建、前台/后台运行、停止与开发代理健康检查。操作见 [前端使用说明](../frontend/README.md)。
- `sync_api_docs.py`：更新 `docs/api/01` 起的功能文档后，在项目根目录运行 `python tools/sync_api_docs.py`，保留总文档公共约定并重新汇总各功能内容，避免总文档与分模块说明不同步。
- `setup-redis-wsl.sh`：已有 WSL Redis 环境的配置脚本，当前环境已配置，日常使用无需重新执行安装。

这些脚本保留原路径，避免影响现有 Redis 登录启动项。临时生成器、安装备份和旧检查产物统一归档到 `output/archive/`。
