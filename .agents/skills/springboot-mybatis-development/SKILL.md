---
name: springboot-mybatis-development
description: 为本 CRM 实现或修复 Java 17、Spring Boot 3、MyBatis-Plus、MySQL 接口与数据库变更。修改后端服务、SQL、事务、乐观锁或迁移时使用；不用于纯前端或其他技术栈。
---

# CRM Java 后端开发

读取 `AGENTS.md` 和 [业务决策](../../../docs/business-decisions.md) 中与本次变更有关的条目。数据库/金额/并发改动再读 [一致性规则](references/consistency.md)。

## 实现约束

- Controller 校验与响应，Service 编排业务和事务，Mapper 访问数据。DTO 白名单接收可编辑字段；身份、归属、确认人、版本结果由后端决定，防止批量赋值越权。
- 使用 Java 17、Spring Boot 3 对应的 jakarta API 和 Spring Security 6 配置；实际依赖从构建文件和锁定版本核对，不复制旧版本教程。
- 固定响应和分页契约；校验异常、权限拒绝、并发冲突有稳定错误语义。业务失败不能统一吞掉为成功。
- 变更表结构同步迁移/初始化、Entity、Mapper、DTO/VO 与相关查询。现有数据库与空数据库分别考虑，禁止无授权清空重建。
- 核心操作的归属变更、状态联动、必要记录采用同一业务事务。提交后处理缓存，数据库回滚时不能保留已更新的缓存结果。
- MyBatis-Plus 乐观锁插件不会自动保证所有自定义 SQL 正确；查看实际 UPDATE 条件及影响行数。固定状态、归属、version 条件，并定义冲突结果。
- 新查询从创建时包含数据范围、逻辑删除和必要关联约束，详情/更新入口与列表一致。

## 环境与验证

检查实际 pom、wrapper 和 profile 后选择命令。Maven 当前用 JDK 17，而 PATH 的 java 用 JDK 25，见 [环境记录](../../../docs/environment.md)。使用正确 JDK 启动，不改系统配置来绕过。

普通接口检查参数、响应和既有测试；资金、并发和状态变更增加有意义的数据库/接口验证。模拟 Mapper 的单元测试不能证明数据库条件更新、唯一键或事务隔离成立。

需要代码评审时按 [CRM 审查清单](../crm-verification/references/review-checks.md) 核对受影响代码；测试设计衔接 [CRM 验证](../crm-verification/SKILL.md)。保留项目真实格式化约定，不为通用行数阈值增加无必要抽象或更换架构。

交付说明包含实际命令、迁移影响、失败回滚/并发语义及未覆盖限制；同步相关需求记录。
