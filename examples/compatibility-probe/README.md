# 技术基线集成探针

此工程用于 P0-02 验证组件能否在同一进程工作，不是课程正文或生产模板。版本见 [兼容矩阵](../../docs/00-introduction/compatibility-matrix.md)，取舍见 [ADR-0001](../../docs/adr/0001-technology-baseline.md)。

## 运行

要求 Java 21 或 25、Maven 3.9.11，以及正在运行的 Linux 容器 Docker 环境。Windows 使用 Docker Desktop 的 Linux containers 模式。首次执行需要下载 Maven 依赖及 MySQL/Redis/Ryuk 镜像。

在仓库根目录执行：

```sh
./mvnw --batch-mode --no-transfer-progress -f examples/compatibility-probe/pom.xml clean verify
```

Windows 使用仓库根目录的 `.\mvnw.cmd`。统一Wrapper固定Maven3.9.11；探针仍是独立POM，不进入无Docker的根Reactor。不能跳过测试后宣称集成通过。

预期：4 项测试，0 失败、0 错误、0 跳过，并输出 `BUILD SUCCESS`。测试会创建临时 MySQL 和 Redis 容器，测试结束自动清理。

## 检查内容

1. 未认证访问 `/probe` 返回 401。
2. Basic 认证后访问 `/probe` 返回 Flyway 迁移后的 MyBatis 查询结果及 Redis 内容。
3. 事务中插入后抛异常，确认数据回滚。
4. `/v3/api-docs` 成功生成并包含 `/probe` 路由。

Basic用户及明文测试口令仅供此隔离探针。它们是公开测试数据，不代表后续JWT、安全配置或生产密钥。匿名OpenAPI端点也仅为验证用途。

## Boot 4 对比

独立POM切换Boot、MyBatis-Plus Starter和springdoc主版本；保留同一测试契约与Testcontainers 1.21.4，避免把对照差异藏进测试工具升级中：

```sh
./mvnw --batch-mode --no-transfer-progress -f examples/compatibility-probe/pom-boot4.xml clean verify
```

Boot4使用结果只对该探针有效，不代表45章、全部Starter或毕业项目已迁移。两套POM共用输出目录，勿在同一工作目录同时构建。

## 排查

先用JDK自身的HTTP服务端/客户端隔离网络问题（仓库根目录）：

```sh
java examples/compatibility-probe/tools/HttpClientProbe.java
```

成功时输出`NATIVE_HTTP_OK`及真实JDK版本。它不替代Spring、数据库、安全或事务集成测试。Foundation在Windows/Linux的Java21/25组合运行该回归。

本机Windows若报`Unable to establish loopback connection`，可先验证套接字目录：

```powershell
java '-Djdk.net.unixdomain.tmpdir=D:\JERRY\java-for-nestjs' examples/compatibility-probe/tools/HttpClientProbe.java
```

这里是本次实测可用的仓库路径，使用时须换成你本机可写、支持AF_UNIX的目录，不保证所有D盘或路径均适用。此参数只影响当前Java进程，不修改全局配置。实测本机默认C盘临时目录失败，指定上述目录后Java21/25真实HTTP响应通过；这是已验证的路径绕行，操作系统侧原因尚未确认。

完整Maven集成测试需要把同一属性传给fork的测试JVM；在当前PowerShell临时设置`JAVA_TOOL_OPTIONS`，执行后恢复原值，不能只给Maven进程设置`MAVEN_OPTS`。仍须有可用的Linux Docker引擎，禁止跳过失败测试。过程及命令见[G0验收记录](../../docs/progress/G0.md)。

- 缺少Docker或容器引擎不可用：先运行 `docker version`，确认服务端可用且为Linux容器模式；测试不会自动跳过。
- Maven依赖下载失败：保存错误并确认仓库网络；解决后重试，不把已通过测试等同于完整 `verify` 成功。
- 在Linux Maven容器中访问Docker Desktop的同级容器时，挂载Docker socket并设置 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`；原生主机运行通常无需该覆盖。
- 运行结束的Redis重连日志及Mockito动态Agent提示不等于断言失败，以Surefire结果和完整构建退出码为准；后续扩展Mockito测试前应按官方指导配置静态Agent。

测试结果位于 `target/surefire-reports/`。Linux CI 在 [兼容验证工作流](../../.github/workflows/compatibility.yml) 执行Java21/25主线矩阵；它不替代P0-06的全仓库质量门禁。
