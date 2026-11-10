# 毕业项目目录约定

此目录用于Vue3管理端与Spring Boot模块化单体，当前尚无业务应用。已有`deploy/compose/compose.yaml`提供本地MySQL/Redis，运行方式见[工作区指南](../docs/00-introduction/workspace.md)。P6在前置阶段验收后整合业务成果。

| 后续路径 | 职责 |
|---|---|
| `apps/admin-web/` | Vue3 / TypeScript 管理端与 OpenAPI 生成客户端 |
| `apps/admin-server/` | 启动、应用装配与外部入口 |
| `java-modules/common-core/` | 无 Spring Web 耦合的基础模型与工具 |
| `java-modules/common-web/` | 异常映射、响应模型和 Web 配置 |
| `java-modules/common-security/` | 认证授权基础设施 |
| `java-modules/module-system/` | 用户、角色、菜单、权限与字典 |
| `java-modules/module-business/` | 选定的订单或代理商案例 |
| `deploy/compose/` | 本地完整环境、健康检查与停止处理 |
| `deploy/kubernetes/` | 进阶编排示例 |

公共模块禁止反向依赖业务模块；不能把所有 DTO、实体或 SQL 集中放入 `common-core`。Java 由 Maven 管理，前端由 pnpm 管理。

业务范围、API 契约与模块化单体取舍在 P6-01 提交 ADR 后锁定；复用前期示例和部署能力。真实启动、迁移、测试、备份恢复和监控命令在对应实现交付时补充。

见 [实施计划](../JAVA-FOR-NESTJS-IMPLEMENTATION-PLAN.md)、[任务清单](../TASKBOARD.md) 与 [贡献指南](../CONTRIBUTING.md)。
