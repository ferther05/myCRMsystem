# CRM Skills 安装与使用

## 存放和加载

项目已把两个现成 skills 和十一个定制 skills 放入 `.agents/skills/`，共十三个。Codex 的项目技能目录是 `.agents/skills/`；打开 `E:/bishe/CRM` 后可按任务自动选择或在消息中明确调用。若新技能未出现在列表，重新打开项目或重启 Codex 后核对。无需上传文件或修改全局 config.toml。

`AGENTS.md` 是持续项目约束，各 skill 按任务加载。当前主机提供的文档、表格、PDF、演示文稿和代码审查 skills 已核对文件存在，不重复复制到项目，仍由对应插件或用户 skills 管理。换电脑时，项目自带十三个 skills 可随目录保留，共享能力需该主机具备。

## 已安装 skills

- [playwright](../.agents/skills/playwright/SKILL.md)：真实浏览器操作。原始来源为 openai/skills；本地新增 PowerShell wrapper 和项目路径说明，CLI 固定 0.1.22。
- [security-threat-model](../.agents/skills/security-threat-model/SKILL.md)：用户要求威胁建模时使用，保留原有范围与上下文确认流程。
- [crm-spec-delivery](../.agents/skills/crm-spec-delivery/SKILL.md)：对应需求来源、功能切片与阶段验收。
- [springboot-mybatis-development](../.agents/skills/springboot-mybatis-development/SKILL.md)：Java 后端、SQL、迁移和事务并发。
- [vue3-elementplus-development](../.agents/skills/vue3-elementplus-development/SKILL.md)：Vue 页面、接口契约、权限菜单与 ECharts。
- [crm-ui-design](../.agents/skills/crm-ui-design/SKILL.md)：CRM 页面信息架构、交互原型、视觉规范、Element Plus 主题与界面检查；根据任务选择产出。
- [crm-business-state](../.agents/skills/crm-business-state/SKILL.md)：生命周期、六道闸、商机签约回款联动。
- [crm-security-permissions](../.agents/skills/crm-security-permissions/SKILL.md)：团队归属、协作、字段权限、下载与导出。
- [crm-jobs-cache](../.agents/skills/crm-jobs-cache/SKILL.md)：应用定时任务、待办闭环与 Redis 缓存。
- [crm-verification](../.agents/skills/crm-verification/SKILL.md)：按风险选择单元、集成、浏览器、性能验证与相关代码审查，报告实际验收结果。
- [crm-design-contracts](../.agents/skills/crm-design-contracts/SKILL.md)：阶段 0 的 ER、字段/枚举、数据库约束及前后端接口/权限契约；跨模块变更时核对兼容影响。
- [crm-graduation-delivery](../.agents/skills/crm-graduation-delivery/SKILL.md)：对应需求、设计、实际实现、验证证据与毕业材料；整理论文、任务清单、使用手册和答辩内容。
- [crm-deployment-recovery](../.agents/skills/crm-deployment-recovery/SKILL.md)：构建和运行配置、Nginx、初始化、启动排障、备份恢复及演练。安装 skill 不代表已部署应用。

## 当前主机提供的共享能力

- documents：Word/DOCX 文件与渲染核对。
- spreadsheets：任务清单等电子表格的读取、编辑与检查。
- pdf：PDF 读取、输出和版面检查。
- presentations：答辩演示文稿。
- review、review-bugbot、review-security：按任务调用的代码审查指导。

上述七个入口在当前 skill 列表中可用，文件路径与检查记录见 `skills-installation.json`。本次核对其入口文件和元数据；文档渲染、文件导出和专项审查的实际工具在相应任务中另行验证，不能把文件存在当作所有运行能力均已测试。

当前无 GitHub Actions 配置，未安装 gh-fix-ci；后续接入后再添加。通用 security-best-practices 不支持 Java 核心指南，未作为本项目后端依赖安装。

## 聊天调用示例

下面是发给 Codex 的消息，不是 PowerShell 命令：

```text
$crm-spec-delivery 请开始阶段0，先统一相关业务口径并完成环境、数据模型与接口契约准备。
$crm-design-contracts 请设计六个必选模块的 ER、数据字典和接口契约，标明待确认业务规则及其影响。
$crm-ui-design 请规划客户详情页、公海池与商机页的布局、角色动作和异常状态，先给出页面原型与统一视觉规范。
$crm-graduation-delivery 请根据实际实现与验证记录，整理毕业设计材料的内容和证据对应关系。
$crm-deployment-recovery 请检查本机运行条件并准备部署与恢复说明，列出实际可执行和尚缺条件的步骤。
$crm-business-state 请设计客户回收与商机保护规则，指出仍需要确认的业务决策。
$crm-security-permissions 请检查本次客户详情、附件下载和统计接口的访问控制。
$crm-verification 请验证本次功能，区分通过、失败和未执行的检查。
$crm-verification 请审查本次后端和前端改动，再按资金、权限、并发风险选择必要回归测试。
$playwright 请在实际运行的前端上验证客户新增和经理审批流程。
```

也可以自然语言提出匹配任务，Codex 按 description 自动选择；关键专项验收明确写 skill 名更容易核对。

## 环境与维护

[环境记录](environment.md) 区分已检查工具与尚未搭建的应用。skills 本身不需要 JDK/MySQL 才能加载；相应开发与验证任务运行时需要对应环境。

在本机可执行：

```powershell
& 'E:\python\python.exe' -X utf8 .\tools\validate_skills.py
& .\.agents\skills\playwright\scripts\playwright_cli.ps1 --version
```

验证脚本需 Python 3.10+ 与 PyYAML（本机已有）。换机时用选定 Python 环境安装 `tools/requirements-skills.txt`。验证结果保存为 `docs/skills-validation.json`，只证明 skills 元数据与引用结构，不代表业务测试通过。

截至本次配置，十三个项目 skills 已通过官方 quick_validate 和本地引用检查。原有 Playwright PowerShell wrapper 的版本/帮助及独立空白页浏览器冒烟此前已通过，本次未修改 wrapper，也未重复运行浏览器。新技能的自动发现需由后续工作区上下文加载，当前轮次的 skill 列表不作为刷新成功证据。

本次从用户提供的 `skills` 与 `ui-design-system` 资料中整合一个 UI 设计 skill，并增强现有验证、前端及后端技能。来源、适配范围与修正见 [资料整合记录](skills-adaptation.md)，源文件指纹在 `skills-installation.json` 中保留。功能测试复用已安装的 Playwright；JMeter、数据库测试与持续回归框架按后续实际任务需要准备，本次未安装这些运行依赖。

技能目录、自动/明确调用及刷新方式依据 [OpenAI 官方技能文档](https://developers.openai.com/codex/skills/)。文件验证只确认结构与引用，不能证明当前会话已经热加载新技能。

上游来源及修改前哈希记录在 `docs/skills-installation.json`。更新 Playwright 时保留并重新验证 Windows wrapper 与项目路径；升级 CLI 在两个 wrapper 中一起改版本，执行浏览器冒烟验证。不要直接覆盖本地适配。

原始参考副本与完整文本在 `docs/reference/`；[业务决策](business-decisions.md) 保存冲突与缺口，[需求覆盖](requirements-traceability.md) 和 [阶段状态](development-status.md) 后续随真实交付更新。
