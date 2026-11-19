# 毕业项目

此目录用于Vue3管理端与Spring Boot模块化单体。本阶段的后端骨架提供健康检查。已有`deploy/compose/compose.yaml`提供本地MySQL/Redis，运行方式见[工作区指南](../docs/00-introduction/workspace.md)。P5的认证任务API继续使用[教学部署](deploy/compose/TEACHING.md)。

| 后续路径 | 职责 |
|---|---|
| `apps/admin-web/` | Vue3 / TypeScript 管理端与 OpenAPI 生成客户端 |
| `apps/admin-server/` | 启动、应用装配与外部入口 |
| `java-modules/common-core/` | 无 Spring Web 耦合的基础模型与工具 |
| `java-modules/common-web/` | 异常映射、响应模型和 Web 配置 |
| `java-modules/common-security/` | 认证授权基础设施 |
| `java-modules/module-system/` | 用户、角色、菜单、权限与字典 |
| `java-modules/module-business/` | 内部订单审批 |
| `deploy/compose/` | 本地完整环境、健康检查与停止处理 |
| `deploy/kubernetes/` | 进阶编排示例 |

公共模块禁止反向依赖业务模块；不能把所有 DTO、实体或 SQL 集中放入 `common-core`。Java 由 Maven 管理，前端由 pnpm 管理。

P6-01选择内部订单管理。[项目契约](ARCHITECTURE.md)记录业务范围、模块依赖、数据模型与API，[ADR 0014](../docs/adr/0014-capstone-order-management.md)记录架构和持久层取舍。后续任务按契约实现并记录启动、迁移、测试、恢复和监控命令。

## 构建与启动后端骨架

在仓库根目录使用JDK 21执行：

```powershell
./mvnw.cmd --batch-mode --no-transfer-progress -f project/pom.xml clean verify
java -jar project/apps/admin-server/target/admin-server-0.1.0-SNAPSHOT.jar
```

Linux将`./mvnw.cmd`换成`./mvnw`。应用默认绑定`127.0.0.1:8091`，在另一终端执行`curl.exe -f http://127.0.0.1:8091/actuator/health`，预期200和`{"status":"UP"}`。可通过`CAPSTONE_PORT`调整端口。按Ctrl+C关闭应用。

Windows出现`Unable to establish loopback connection`并伴随`UnixDomainSockets.connect`异常时，按[G0记录](../docs/progress/G0.md#windows最小复现与局部修复)在当前终端为测试JVM指定支持AF_UNIX的临时目录。构建包含真实HTTP启动测试与[Maven Enforcer依赖规则](https://maven.apache.org/components/enforcer/enforcer-rules/bannedDependencies.html)，由[后端CI](../.github/workflows/capstone-backend.yml)在Java 21及25验证。

见 [实施计划](../JAVA-FOR-NESTJS-IMPLEMENTATION-PLAN.md)、[任务清单](../TASKBOARD.md) 与 [贡献指南](../CONTRIBUTING.md)。
