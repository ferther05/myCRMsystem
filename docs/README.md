# 项目资料导航

按下面的顺序查看即可，不需要每次阅读所有材料。

1. [精简数据库预览](../database/schema-preview.md)：18 张表的用途、主要字段和核心关系。
2. [项目范围](project-scope.md)：六个必选模块、技术方向与本轮简化原则。
3. [业务决策](business-decisions.md)：已经确认的设计偏好和仍需统一的业务细则。
4. [开发状态](development-status.md)：阶段 0～8 的实际进度。
5. [开发前准备](development-readiness.md)、[环境说明](environment.md)、[Redis 使用](redis-setup.md)：开工条件与实际工具状态。
6. [需求覆盖](requirements-traceability.md)：功能与毕业材料的对应关系。
7. [Skills 使用](skills-setup.md)、[技能适配记录](skills-adaptation.md)：已安装能力及调用方式。
8. [后端启动与开发](../backend/README.md)、[后端接口约定](backend-api.md)、[骨架验证记录](backend-verification.md)：当前后端的实际使用方式和验证范围。
9. [登录与首次改密验证](auth-verification.md)：认证接口、JWT、种子密码核对、并发改密与事务回滚的验证结果。
10. [全项目接口说明与请求示例](api/00-全项目接口说明与请求示例.md)、[基础健康检查](api/01-基础健康检查.md)、[账号认证与密码](api/02-账号认证与密码.md)：接口固定编号、请求/响应、规则与前端页面对应；后续功能都在 `docs/api/` 按模块维护。
11. [前端启动与开发](../frontend/README.md)、[页面与主题约定](ui/frontend-foundation.md)、[前端联调验证](frontend-verification.md)：当前前端的使用方式、用户选择的 A 风格和真实浏览器结果。

原始参考文件及全文提取保留在 `reference/`，不改写原件。来源材料中的建议不等同于用户已经授权执行；当前实施方案以用户指示和业务决策为准。

后续需求、测试证据说明和部署手册继续放在 `docs/`，接口说明及请求示例统一在 `docs/api/` 同步维护总文档与模块文档；数据库设计和 SQL 放在 `database/`；大批量临时输出放在 `output/`。

新增 [模块学习与操作说明](module-guides/README.md)，其中 [第一个模块](module-guides/01-用户与权限管理.md) 按技术、操作与后续效果说明用户权限功能。接口分别见 [账号申请与管理](api/03-内部账号申请与管理.md)、[个人资料](api/04-个人资料维护.md)、[角色与数据范围](api/05-角色与数据范围.md)；验证见 [用户权限验证](user-permissions-verification.md)。
