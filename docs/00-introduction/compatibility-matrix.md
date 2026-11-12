# 技术兼容矩阵

核验日期：2026-11-09。基线决策：[ADR-0001](../adr/0001-technology-baseline.md)。本表区分官方声明、锁定版本与实测证据，不声称使用最新版本或具备生产安全保证。

## Java主线

| 组件 | 主线锁定 | 版本来源 / 约束 | 本次验证范围 |
|---|---|---|---|
| Java语言级别 | 21 | 以21编译；25作为兼容运行与构建目标 | 分环境记录见下表 |
| Spring Boot | 3.5.16 | starter-parent统一管理Spring与插件 | 真实Web上下文与4项集成测试 |
| Spring Security | 6.5.11 | Boot 3.5.16 BOM | 匿名401、认证后200；不代表JWT/RBAC完成 |
| Maven | 3.9.11 | 独立固定构建工具；Wrapper由P0-05统一交付 | clean verify含打包 |
| Gradle对照 | 9.2.1 | 独立任务图/本地缓存小实验，见[ADR-0005](../adr/0005-gradle-comparison-runtime.md) | Windows/Microsoft21加已知临时套接字目录修正后任务/缓存通过；新矩阵步骤待CI |
| MyBatis-Plus | 3.5.18 | mybatis-plus-spring-boot3-starter | MySQL查询、写入与回滚 |
| springdoc OpenAPI | 2.10.0 | Boot3.5对应2.8~2.10系列 | 生成文档并包含实际路由 |
| Flyway | 11.20.3 | 显式覆盖Boot默认11.7.2；core与mysql模块同版 | 空库迁移及MySQL8.4集成 |
| MySQL Server | 8.4.7 | 测试镜像mysql:8.4.7 | 迁移、Mapper查询和事务 |
| MySQL Connector/J | 9.7.0 | Boot BOM管理，不要求与Server同主版本 | 连接MySQL8.4.7 |
| Redis Server | 7.4.6 | 测试镜像redis:7.4.6-alpine | Spring Data Redis读写 |
| Testcontainers | 1.21.4 | Boot BOM，junit-jupiter/mysql坐标 | 临时MySQL/Redis、无跳过测试 |
| JUnit Jupiter | 5.12.2 | Boot BOM | 4项集成断言 |
| 编译 / 测试插件 | Compiler 3.14.1 / Surefire 3.5.6 | Boot parent管理 | Java21 release编译与JUnit执行 |

版本证据来自实际POM及[Boot 3.5.16 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom)。不要独立覆写Spring Framework/Security等被管理组件，除非提交兼容验证和ADR。

## 文档与前端候选

| 工具 | 本次决策 | 验证状态 |
|---|---|---|
| VitePress | 1.6.4稳定线，ESM配置 | P0-03已在Windows11/Node22.20.0完成根路径与子路径构建 |
| Node | 22.20.0 | 本机文档类型检查、构建及浏览器验证通过；不同于Java环境结果 |
| pnpm | 10.34.9，packageManager与锁文件固定 | 通过npx运行已锁定CLI，绕过本机损坏的全局12.5.1启动器，不修改全局安装 |
| Vue / TypeScript / vue-tsc | 3.5.45 / 5.9.3 / 3.1.8 | 组成文档主题，类型检查与SSR构建通过；不代表P6管理端基线已确定 |
| Mermaid | 11.12.2，客户端按需加载 | 流程图、时序图、浅/深主题切换及子路径均已浏览器验证 |
| Vite / lodash-es | overrides固定6.4.4 / 4.18.1 | P0-06修复high审计项；超出VitePress声明的Vite5范围，见[ADR-0002](../adr/0002-documentation-build-security.md)及[回归记录](../progress/P0-06.md) |

VitePress1文档的Node下限与VitePress2文档不同，应查阅[稳定版安装指南](https://vuejs.github.io/vitepress/v1/guide/getting-started)，不要混用next/alpha的要求。

## 官方兼容声明

P4缓存参照（2026-11-12）：独立cache-lab使用Boot管理的Spring Data Redis，Node ioredis5.8.2，服务镜像redis:7.4.6。[Redis43181272467](https://github.com/zrayjay/java-for-nestjs/actions/runs/43181272467)在Temurin21/25各Java5项及Node5项真实服务通过，包含PTTL/服务器到期、空值、作用域键、本机合并、旧回填竞态及断开客户端。尚未接入实际任务HTTP/事务，未验证Redis服务宕机恢复或分布式协调；独立审查待完成。

P4认证参照（2026-11-12）：Boot3.5.16管理Spring Security6.5.11，其oauth2-jose制品声明选定Nimbus9.37.4（本机实际测试classpath核对），不声称Boot BOM直接管理每个传递依赖。Node jose6.2.17、bcryptjs3.0.3和显式reflect-metadata0.2.2。[Security43170418413](https://github.com/zrayjay/java-for-nestjs/actions/runs/43170418413)在Linux Temurin21/25各完整通过Java19项/Node19项，含V2凭据版本绑定与并发/缺声明负例；新增合法作用域写入对照以其后提交单独记录。Redis/上传与G4尚未据此通过。

P3 HTTP参照（2026-11-11）：Nest common/core/platform-express固定11.2.9，Swagger11.4.8，class-validator0.15.1及class-transformer0.5.1，见[ADR-0006](../adr/0006-http-reference-runtime.md)。Windows/Microsoft JDK21的Spring Web新增13项测试通过；Nest真实环回HTTP测试通过；两个独立打包进程的共同HTTP契约通过。它们使用内存仓库，尚不代表数据库、JWT或RBAC验证。

第27章新增独立参照（[ADR-0003](../adr/0003-transaction-reference-runtime.md)）：Prisma CLI / Client / @prisma/adapter-mariadb均固定7.10.1，JS驱动mariadb3.5.4，数据库MySQL8.4.7。Node22.20.0满足^22.12约束。Flyway11.20.3独占表结构迁移，不同时用Prisma Migrate。[事务CI 42777751014](https://github.com/zrayjay/java-for-nestjs/actions/runs/42777751014)在Linux Temurin21/25各通过Java9项及Prisma7项真实SQL测试和2项连接守卫；本机仅编译/生成/守卫通过，不冒充本机数据库验证。后续增补断言以[P1-04记录](../progress/P1-04.md)为准。

- [Boot3.5系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)：该版本线支持本课程选择的Java21/25，构建工具满足其最低要求。
- [MyBatis-Plus安装说明](https://baomidou.com/en/getting-started/install/)：Boot3和Boot4使用不同Starter坐标，不能只改Boot parent。
- [springdoc兼容矩阵](https://springdoc.org/v2/faq.html)：Boot3.5选择2.x对应范围，Boot4选择3.x。
- [Testcontainers环境要求](https://java.testcontainers.org/supported_docker_environment/)与[Windows说明](https://java.testcontainers.org/supported_docker_environment/windows/)：Windows使用Linux容器环境，不能以Windows containers模式替代。
- [Maven3.9.11发布说明](https://maven.apache.org/docs/3.9.11/release-notes.html)：固定已验证版本，不声称它是最新版本。

## 分环境实测

| 栈 | 环境 | JDK | 结果 |
|---|---|---|---|
| Boot3主线 | 本机Docker Linux / Maven3.9.11 | Temurin21.0.9 | 4项通过、无跳过，clean verify成功 |
| Boot3主线 | 本机Docker Linux / Maven3.9.11 | Temurin25.0.1 | 4项通过、无跳过，clean verify成功 |
| Boot3主线 | Windows11原生 / Maven3.9.11 / Docker Desktop | JBR25.0.4.1 | 失败：JDK HTTP Client初始化环回连接异常 |
| Boot3主线 | Windows11原生 / Maven3.9.11 / Docker Desktop | Microsoft21.0.13.0.2 | 同类环回异常；4项错误，0跳过，不计通过 |
| Boot3主线 | Ubuntu24.04 GitHub Actions | Temurin21.0.13 / 25.0.5 | 两个组合各4项通过、完整verify成功；见CI证据 |
| Boot4对比 | 本机Docker Linux | Java21 | 依赖下载403，未进入测试，不能判定兼容或不兼容 |
| Boot4对比 | Ubuntu24.04 GitHub Actions | Temurin21.0.13 / 25.0.5 | 两个组合各4项通过、完整verify成功；补齐Flyway Starter后复测 |

CI证据：[运行42603068673](https://github.com/zrayjay/java-for-nestjs/actions/runs/42603068673)，代码提交`e22e54b`，四个组合全部成功。本机Docker Linux与Linux CI是不同证据；Windows原生运行与Windows宿主上的Linux容器也不混同。完整过程见[P0-02验证记录](../progress/P0-02.md)。

## 已知问题与处理

1. Boot默认Flyway11.7.2虽能迁移，但对MySQL8.4输出支持范围警告；改用11.20.3后在同一数据库版本重新运行，警告消失且4项测试及打包通过。后续升级应同步core与mysql模块。
2. 首次verify的测试通过，但打包插件依赖下载失败；重新解析依赖并完成整个verify才计为通过。
3. Testcontainers容器结束时Redis客户端可能输出重连日志；只在断言及完整退出码成功时记录通过，不删除或隐藏失败输出。
4. Mockito动态Agent和Maven/JDK25的Unsafe提示属于后续工具链维护事项；当前没有Mock测试，不据此声称Mockito全能力兼容。
5. 本机Windows默认临时目录下，JDK HTTP Client仍因UnixDomainSockets连接返回`Invalid argument: connect`而失败；操作系统侧根因尚未确认。G0复测发现将进程属性`jdk.net.unixdomain.tmpdir`指向本机实测可用的仓库目录后，Java21/25真实HTTP200及响应体断言通过。完整Spring/数据库复测随后因Docker Desktop自己的套接字目录错误阻塞，仍不计作完整Windows兼容通过。命令、范围及独立发现见[G0验收记录](../progress/G0.md)，不得以Linux结果或最小HTTP回归代替该项。

## Boot4评估边界

独立`pom-boot4.xml`选择Boot4.1.2、MyBatis-Plus Boot4 Starter3.5.18、springdoc3.2.0。为保持比较变量一致，暂显式固定Testcontainers1.21.4和Flyway11.20.3；这不同于Boot4默认管理的Testcontainers2.0.5/Flyway12.4.0。

首轮CI两项数据库断言失败，原因是仅添加Flyway库未装配Boot4的迁移自动配置，表未创建。按照[Boot4数据库初始化说明](https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html)增加`spring-boot-starter-flyway`后，Java21/25均复测通过。这是本次实际确认的迁移差异。

即使对比探针通过，也仅证明同一小型API/数据库/缓存组合可运行。Boot4默认测试栈、Jackson3迁移、全部课程和生产行为仍需独立验证；本次不改变Boot3主线。

## 升级回归清单

- [ ] 记录官方支持范围、精确版本及发布来源，提交ADR。
- [ ] Windows11原生和Linux CI分别在Java21/25运行完整verify，禁止跳过测试。
- [ ] 验证Flyway空库迁移、已有库升级、Mapper与事务回滚。
- [ ] 验证认证拒绝、API响应、OpenAPI生成和Redis读写。
- [ ] 执行后续章节交付的JWT/RBAC/缓存/消息故障及安全回归。
- [ ] 构建VitePress，检查链接、章节映射、版本标注及TS锁文件。
- [ ] 记录失败、未测范围和独立审查结果，再更新基线。
