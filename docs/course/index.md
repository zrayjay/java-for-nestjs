---
title: 课程目录
description: 从NestJS迁移到Java与Spring Boot的45章学习地图。
---

<!-- 由 scripts/generate-course-index.mjs 从 chapter-map.yml 生成。请修改数据源后重新生成。 -->
# 课程目录

45章，9个部分。从运行时差异出发，走向可交付的Java后端。

::: info 建设状态
当前已验收 4/45 章。计划索引不计作完成章节。第02、08、18、27章作为首轮试点；先完成样章及独立审查，再扩展其余内容。
:::

[了解如何学习](../00-introduction/learning-guide.md) · [查看已验证的技术版本](../00-introduction/compatibility-matrix.md)

## Part 01 · 运行时与工具入门 {#part-01}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 01 | [Java 生态地图](../../docs/01-java-runtime/01-java-ecosystem.md) | JDK/JRE/JVM、Java SE、Spring、Spring Boot | Node、V8、npm、NestJS | 待独立审查 |
| 02 | [Node.js 与 JVM 运行模型](../../docs/01-java-runtime/02-runtime-model.md) | 编译、字节码、JIT、GC、事件循环与线程 | V8 / Event Loop | 已验收 |
| 03 | [开发环境与调试](../../docs/01-java-runtime/03-development-environment.md) | JDK 安装、IDEA/VS Code、断点、调试器 | nvm/fnm、VS Code | 待独立审查 |
| 04 | [Java 编译和执行](../../docs/01-java-runtime/04-compile-and-run.md) | `javac`、`java`、JAR、Classpath、Module Path | `tsc`、Node ESM/CJS | 待独立审查 |
| 05 | [内存与对象模型](../../docs/01-java-runtime/05-memory-model.md) | 堆/栈、引用、GC、类加载初识 | JS GC、对象与引用 | 待独立审查 |

## Part 02 · 现代 Java 语言 {#part-02}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 06 | 类型系统 | 原始类型、引用类型、装箱、`var`、可空性 | TS 类型系统 | 待编写 |
| 07 | 类、接口与 record | 封装、继承、多态、抽象类、record、sealed | TS class/interface/type | 待编写 |
| 08 | [泛型](../../docs/02-java-language/08-generics.md) | 类型擦除、边界、通配符、PECS | TS Generics | 已验收 |
| 09 | 集合与数据结构 | List/Set/Map、相等性、HashMap | JS Array/Map/Set | 待编写 |
| 10 | Lambda 与 Stream | 函数式接口、流管道、Optional | JS map/filter/reduce | 待编写 |
| 11 | 异常与注解 | checked/unchecked、反射、注解 | JS Error、Decorators | 待编写 |
| 12 | 并发与异步 | Thread、线程池、Future、虚拟线程 | Event Loop、Promise、Worker | 待编写 |

## Part 03 · Java 工程化 {#part-03}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 13 | pnpm 到 Maven | 仓库、坐标、传递依赖、范围 | package.json / lockfile | 待编写 |
| 14 | `pom.xml` 深入 | 生命周期、插件、BOM、冲突解决 | pnpm scripts / overrides | 待编写 |
| 15 | Maven 与 Gradle | Reactor、Gradle 任务图、缓存、选型 | Turbo/Nx 构建编排 | 待编写 |
| 16 | Java Monorepo | 模块化单体、Maven 多模块、依赖边界 | pnpm Workspace | 待编写 |

## Part 04 · NestJS 到 Spring Boot {#part-04}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 17 | 启动过程与自动配置 | SpringApplication、Starter、Auto Configuration | NestFactory/bootstrap | 待编写 |
| 18 | [IoC、DI 与 Bean](../../docs/04-spring-boot/18-dependency-injection.md) | 容器、构造器注入、作用域、生命周期 | Providers/Modules | 已验收 |
| 19 | Controller / REST API | MVC、路由、参数绑定、响应、异常状态 | Controller/Decorator | 待编写 |
| 20 | DTO 与参数校验 | Bean Validation、嵌套校验、校验分组 | DTO/Pipes/class-validator | 待编写 |
| 21 | 请求处理链 | Servlet、Filter、Interceptor、Security Filter | Middleware/Guard/Interceptor | 待编写 |
| 22 | AOP、代理与异常 | JDK/CGLIB 代理、自调用、Advice、ControllerAdvice | Interceptor/ExceptionFilter | 待编写 |
| 23 | 配置与生命周期 | Profiles、Properties、Configuration、Actuator | ConfigModule/Lifecycle Hooks | 待编写 |

## Part 05 · 数据访问 {#part-05}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 24 | JDBC 与连接池 | Driver、DataSource、HikariCP、连接生命周期 | Prisma 连接池概念 | 待编写 |
| 25 | MyBatis / MyBatis-Plus | Mapper、Wrapper、XML SQL、分页、N+1 防范 | Prisma Client | 待编写 |
| 26 | JPA / Hibernate / jOOQ | Entity、Persistence Context、脏检查、类型安全 SQL | Prisma ORM 与查询 API | 待编写 |
| 27 | [事务机制](../../docs/05-database/27-transactions.md) | ACID、传播、隔离、代理陷阱、回滚规则 | `$transaction` | 已验收 |
| 28 | 数据库工程化 | Flyway、索引、执行计划、慢 SQL、schema 管理 | Prisma Migrate | 待编写 |

## Part 06 · 安全与企业业务 {#part-06}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 29 | Spring Security | FilterChain、Authentication、SecurityContext | Nest Passport / Guard | 待编写 |
| 30 | JWT 与会话 | Access/Refresh Token、过期、轮换、撤销、CSRF 取舍 | Nest JWT | 待编写 |
| 31 | RBAC 权限系统 | 用户/角色/权限、按钮/接口/数据权限、越权防范 | Nest RBAC | 待编写 |
| 32 | Redis 与缓存设计 | TTL、穿透/击穿/雪崩、一致性、并发 | ioredis / cache-manager | 待编写 |
| 33 | 企业基础能力 | 文件、对象存储、审计、OpenAPI、脱敏 | Multer / Swagger | 待编写 |

## Part 07 · 测试与诊断 {#part-07}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 34 | 单元测试 | JUnit、Mockito、AssertJ、测试隔离 | Jest / Vitest | 待编写 |
| 35 | 集成与契约测试 | MockMvc、Testcontainers、API 合约、测试数据 | Supertest / e2e | 待编写 |
| 36 | JVM 性能诊断 | JFR、GC、heap dump、thread dump、压测 | Node profiling | 待编写 |
| 37 | 可观测性 | Actuator、Micrometer、Tracing、结构化日志 | OpenTelemetry / Pino | 待编写 |

## Part 08 · 分布式与云原生 {#part-08}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 38 | 消息队列 | RabbitMQ/Kafka、确认、重试、DLQ、幂等 | BullMQ、Node MQ 客户端 | 待编写 |
| 39 | 分布式可靠性 | 超时、熔断、Outbox、最终一致性、租约 | Agent Worker 设计 | 待编写 |
| 40 | 容器与编排 | Docker、Kubernetes、健康检查、滚动更新 | Node Docker 部署 | 待编写 |
| 41 | 自动交付 | GitLab CI/GitHub Actions、镜像、配置、回滚 | 现有 Node CI/CD | 待编写 |

## Part 09 · 最终工程实战 {#part-09}

| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |
|---|---|---|---|---|
| 42 | 需求与架构设计 | 模块边界、数据库设计、ADR、API 契约 | NestJS 项目拆分 | 待编写 |
| 43 | Spring Boot 业务实现 | 用户、认证、RBAC、订单/代理商、审计、测试 | NestJS 对照实现 | 待编写 |
| 44 | Vue3 前后端联调 | OpenAPI、生成 TS Client、错误码、权限菜单 | 既有 Vue3 管理端 | 待编写 |
| 45 | 生产交付与验收 | Docker Compose、CI、监控、备份、故障演练 | Node 项目生产部署 | 待编写 |

## 从已有成果开始

- [学习指南](../00-introduction/learning-guide.md)：先修知识与能力验收。
- [运行兼容探针](../00-introduction/run-probe.md)：真实数据库、缓存、事务与认证验证。
- [图表说明](../00-introduction/diagrams.md)：了解教程使用的流程图及阅读方式。
