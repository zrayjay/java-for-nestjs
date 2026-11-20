# 毕业项目

此目录用于Vue3管理端与Spring Boot模块化单体。后端已提供身份、用户与角色管理、菜单和订单审批。[工作区Compose](../docs/00-introduction/workspace.md)提供本地MySQL/Redis，P5的任务API继续使用[独立教学部署](deploy/compose/TEACHING.md)。

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

## 构建与测试后端

后端集成测试使用一次性MySQL、Redis和随机生成的RSA密钥。仓库根目录用JDK 21执行：

```powershell
docker run --rm -d --name capstone-test-mysql -p 127.0.0.1:13319:3306 -e MYSQL_DATABASE=capstone_test -e MYSQL_USER=capstone_test -e MYSQL_PASSWORD=capstone-test-password -e MYSQL_ROOT_PASSWORD=capstone-test-root mysql:8.4.7
docker run --rm -d --name capstone-test-redis -p 127.0.0.1:16319:6379 redis:7.4.6-alpine
docker exec capstone-test-mysql mysqladmin ping -h127.0.0.1 -pcapstone-test-root --silent
docker exec capstone-test-redis redis-cli ping
./mvnw.cmd --batch-mode --no-transfer-progress -f project/pom.xml clean verify
```

等MySQL和Redis探测通过再构建。Linux将`./mvnw.cmd`换成`./mvnw`，测试端口可用`CAPSTONE_TEST_DB_PORT`与`CAPSTONE_TEST_REDIS_PORT`覆盖。测试后可用`docker stop capstone-test-mysql capstone-test-redis`清理测试容器。

Windows出现`Unable to establish loopback connection`并伴随`UnixDomainSockets.connect`异常时，按[G0记录](../docs/progress/G0.md#windows最小复现与局部修复)在当前终端为测试JVM指定支持AF_UNIX的临时目录。构建包含真实HTTP启动测试与[Maven Enforcer依赖规则](https://maven.apache.org/components/enforcer/enforcer-rules/bannedDependencies.html)，由[后端CI](../.github/workflows/capstone-backend.yml)在Java 21及25验证。

## 启动本地应用

在独立的开发库配置MySQL连接。首次启动由Flyway建表，并按环境变量创建首位管理员。应用重启时保留已有账号与会话。

| 环境变量 | 用途 |
|---|---|
| `CAPSTONE_JDBC_URL`、`CAPSTONE_DATABASE_USER`、`CAPSTONE_DATABASE_PASSWORD` | 数据库连接。使用单独的项目库。 |
| `CAPSTONE_SIGNING_KEY`、`CAPSTONE_VERIFICATION_KEY` | RSA私钥（PKCS#8）和公钥（X.509）的`file:` URI。 |
| `CAPSTONE_ADMIN_USERNAME`、`CAPSTONE_ADMIN_PASSWORD` | 空库首启的管理员登录名和密码。密码至少12个字符，最多72个UTF-8字节。 |
| `CAPSTONE_PORT` | HTTP端口，默认8091。 |
| `CAPSTONE_MENU_CACHE_ENABLED` | 设为`true`启用Redis菜单定义缓存，默认从MySQL读取。 |
| `CAPSTONE_REDIS_HOST`、`CAPSTONE_REDIS_PORT` | 缓存地址，默认`127.0.0.1:6379`。 |

本地演示密钥可复用[教学KeyTool](../examples/security-lab/src/main/java/dev/javafornestjs/security/KeyTool.java)。在仓库根目录用分离的临时目录生成：

```powershell
$keyTool = (Resolve-Path examples/security-lab/src/main/java/dev/javafornestjs/security/KeyTool.java).Path
New-Item -ItemType Directory -Force target/capstone-local | Out-Null
Push-Location target/capstone-local
try {
    java $keyTool
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
    $env:CAPSTONE_SIGNING_KEY = ([Uri](Resolve-Path target/demo-keys/private.pem).Path).AbsoluteUri
    $env:CAPSTONE_VERIFICATION_KEY = ([Uri](Resolve-Path target/demo-keys/public.pem).Path).AbsoluteUri
} finally {
    Pop-Location
}
java -jar project/apps/admin-server/target/admin-server-0.1.0-SNAPSHOT.jar
```

KeyTool保留已生成的文件，可直接将环境变量指向原密钥。应用默认绑定`127.0.0.1`。在另一终端设置同一管理员登录名和密码环境变量，再验证身份接口：

```powershell
$api = 'http://127.0.0.1:8091'
Invoke-RestMethod "$api/actuator/health"
$credentials = @{ username = $env:CAPSTONE_ADMIN_USERNAME; password = $env:CAPSTONE_ADMIN_PASSWORD }
$session = Invoke-RestMethod "$api/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body ($credentials | ConvertTo-Json)
Invoke-RestMethod "$api/api/v1/me" -Headers @{ Authorization = "Bearer $($session.accessToken)" }
```

健康检查预期返回`{"status":"UP"}`，身份接口预期返回当前管理员及`system:admin`权限。接口与错误语义见[项目契约](ARCHITECTURE.md#http-契约)。

## 验证角色与菜单

初始库含`ORDER_CLERK`与`ORDER_REVIEWER`角色。使用管理员令牌读取`GET /api/v1/roles`，通过`PUT /api/v1/users/{id}/roles`为普通用户分配角色。登录为该用户，`GET /api/v1/me`返回业务能力，`GET /api/v1/me/menus`只返回当前可访问的导航。普通用户直接访问角色或菜单管理接口返回403。

在同一部门使用两个账号分别领取录单和复核角色。录单员用`POST /api/v1/orders`创建草稿，提交时向`POST /api/v1/orders/{id}/submit`发送当前`version`。复核员读取订单后调用`/approve`并提交所读版本。历史接口`GET /api/v1/orders/{id}/history`依次记录创建、提交和批准。跨部门访问返回404，已过期版本返回409。完整输入及负例见[项目契约](ARCHITECTURE.md#http-契约)。

平台管理员可用`GET /api/v1/audit-events?resourceType=ORDER&resourceId={id}`查找关联的写入事件。响应头`X-Request-Id`与事件的`requestId`对应。Redis短时不可用时，菜单从MySQL回源，请求继续按实时用户权限过滤。

## 管理端与 OpenAPI

在后端启动后，仓库根目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm --filter @java-for-nestjs/admin-web dev
```

打开`http://127.0.0.1:5174`。开发服务将`/api`代理至`http://127.0.0.1:8091`，可用`CAPSTONE_BACKEND_URL`覆盖。生产构建用同源`/api`。运行`pnpm --filter @java-for-nestjs/admin-web build`生成客户端、检查类型并构建Vue。

[OpenAPI快照](apps/admin-web/openapi.json)来自实际Spring运行物。后端集成测试从`/v3/api-docs`获取契约并与快照比较。变更API后，在测试服务就绪时执行以下命令更新快照及格式：

```powershell
./mvnw.cmd --batch-mode --no-transfer-progress -f project/pom.xml '-Dcapstone.updateOpenApi=true' '-Dtest=CapstoneIdentityTest#generatedOpenApiMatchesTheCommittedClientContract' '-Dsurefire.failIfNoSpecifiedTests=false' verify
pnpm exec prettier --write project/apps/admin-web/openapi.json
pnpm --filter @java-for-nestjs/admin-web build
```

服务运行时如需查看契约，可设置`CAPSTONE_OPENAPI_ENABLED=true`。默认关闭契约端点。生成的客户端位于`project/apps/admin-web/src/api/generated`，每次构建由锁定的生成器重建。

见 [实施计划](../JAVA-FOR-NESTJS-IMPLEMENTATION-PLAN.md)、[任务清单](../TASKBOARD.md) 与 [贡献指南](../CONTRIBUTING.md)。
