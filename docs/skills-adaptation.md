# 用户提供 skills 资料的 CRM 整合记录

日期：2026-10-12。用户在资料分析后确认“按你的建议来”，授权新增一个 CRM UI 设计 skill，并把有效测试/审查规则补入现有项目 skills。

## 来源与范围

阅读来源为 `E:/bishe/一些可能会用到的东西/skills/`（17 个文件、7 个 skill 入口）和 `E:/bishe/一些可能会用到的东西/ui-design-system/`（4 个文件、1 个 skill 入口），共 21 个文本文件。原资料未改写；来源路径与 SHA256 见 [安装记录](skills-installation.json) 的 `reference_adaptations`。

原资料中的命令、安装建议和强制措辞作为待评估内容。此次根据项目需求重新组织规则，不整包复制八个 skills，也不执行其中的应用启动、外部工具安装或 MCP 配置命令。

## 已落地的能力

- [crm-ui-design](../.agents/skills/crm-ui-design/SKILL.md) 整合 design、prototype 与 ui-design-system 的页面规划、视觉体系和 UI 检查方法。按任务选取 [视觉契约](../.agents/skills/crm-ui-design/references/visual-contract.md) 与 [页面模式](../.agents/skills/crm-ui-design/references/page-patterns.md)。实现型交付使用 Vue 3、Element Plus；原型产出不固定要求五份文件。
- [crm-verification](../.agents/skills/crm-verification/SKILL.md) 增加 [测试设计](../.agents/skills/crm-verification/references/test-design.md)、[性能与并发](../.agents/skills/crm-verification/references/performance-checks.md) 和 [代码审查](../.agents/skills/crm-verification/references/review-checks.md) 参考，覆盖资料中的四类测试与四个审查维度，保留按风险选择和如实报告的原则。
- [前端开发](../.agents/skills/vue3-elementplus-development/SKILL.md) 接入已确认视觉方案和相关审查；[后端开发](../.agents/skills/springboot-mybatis-development/SKILL.md) 接入代码审查与测试设计。既有鉴权、业务状态、任务缓存等专门技能继续承担各自规则。
- [AGENTS.md](../AGENTS.md)、[使用说明](skills-setup.md)、[阶段状态](development-status.md)、安装清单与验证工具同步增加路由和记录。项目自带技能从十二个增加至十三个。

## 对原资料的修正

- UI 交付适配 Vue 3/Element Plus，统一主题映射；原生 HTML 可用于预览，不成为正式技术栈。模板中的新拟态、外部字体、深色模式和复杂动效改为按需求取舍。
- `ui-design-system/SKILL.md` 引用的 `references/animation-library.md` 未提供；整合后不保留这个失效引用。配色、组件和布局内容改写成与本 CRM 有关的指导，不复制不完整组件代码或占位 SVG。
- 原色表将 `#007AFF` 与白色对比度记为 4.5:1 并标 AA。按 WCAG 相对亮度公式复算约为 4.017:1，未达到普通文字 4.5:1；`#6750A4`/白色约为 6.441:1，`#8E8E93`/白色约为 3.261:1。新指导要求对实际颜色计算，不沿用错误合格标记。
- 响应断言按 `{ code, message, data }` 与实际 HTTP 契约；删除其他项目 jar 名、JobRepository 路径和硬编码启动命令的依赖。金额使用 BigDecimal；null 与参数化、Clock 和业务时区按实际测试设计。
- 不将模板的覆盖率百分比、500 用户、800 TPS、300ms、缓存命中率或预计优化百分比设为项目要求。P95 从样本分布统计，单请求 Duration Assertion 单独解释；并发场景区分预期业务拒绝与非预期故障。
- 并发测试需要任务重叠、异常收集、超时与资源清理，并核对提交后的真实数据库状态。修正仅计成功响应、吞异常或依靠一个共享回滚事务的不足。
- 功能测试复用项目已安装的 Playwright；不自动添加 MCP。审查沿用项目格式化和业务契约，去掉强制行数、评分和“最高五个问题”的输出要求；安全检查不依赖原资料混用的 OWASP 分类编号。

## 验证与使用

运行项目的 `tools/validate_skills.py`，使用本机官方 quick_validate 检查技能元数据、名称及本地引用；结果见 [结构验证记录](skills-validation.json)。来源与已安装文件 SHA256 保留在安装清单中，后续更新需同步。

该验证不证明应用构建、真实页面、数据库并发、JMeter、MCP 或业务验收已运行。本次未创建前后端骨架、主题源码、业务原型或测试套件；这些产出由后续具体开发任务触发。

打开 CRM 工作区后可自然语言提出页面设计任务，或明确写 `$crm-ui-design`。首次出现新技能需以后续会话加载为准；使用方式见 [技能说明](skills-setup.md)。
