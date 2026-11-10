# 从 NestJS 到 Spring Boot：Java 后端系统教程实施计划

> 文档性质：可执行的课程建设与工程落地计划（不是教程正文）  
> 适用读者：已有 Node.js、TypeScript、NestJS 经验的前端/全栈/后端开发者  
> 适用执行者：维护者、技术作者、Claude Code / Codex / OpenCode 等开发 Agent  
> 版本：v1.5 · 2026-11-11  
> 状态：待实施

---

## 0. 项目目标和不可妥协的原则

### 0.1 最终交付

构建一套**以 NestJS 为认知参照**的 Java / Spring Boot 中文系统教程，包括：

1. 45 章结构化 Markdown 教学文档，按 9 大部分组织。
2. 每个**独立知识点**具有**一句话解释、生活类比、Node.js / NestJS 类比、类比边界、对照代码、原理、真实场景、常见错误、练习和验收**；技术上没有可比对象时，明确标记“无直接等价物”，不得生造映射。
3. 配套可运行示例：Node.js / NestJS 对照实现、Java 独立示例、Spring Boot 企业管理系统。
4. 完整 Java 企业级 Monorepo 示例：Vue3 管理端 + Spring Boot 模块化单体 + MySQL + Redis + Docker + CI。
5. 单元测试、集成测试、接口契约测试、安全测试、部署与故障演练。
6. VitePress 文档站点、流程图、知识图谱、学习路线、术语表和常见误区索引。
7. 自动化质量门禁：文档结构、代码编译、测试、链接、示例与版本一致性检查。

### 0.2 核心教学原则

- **对照迁移，而非零基础灌输**：以现有 TypeScript / NestJS 知识切入，但不假设读者了解 JVM、Spring Bean 或 Maven。
- **每个知识点都类比**：不仅每章一个故事，而是每个独立概念均有准确类比及其局限。
- **代码可验证**：任何展示为完整示例的代码必须在仓库中有可运行对应物；片段明确标记上下文。
- **先模块化单体，后分布式**：不得为了展示微服务而过早拆分服务。
- **解释为什么**：明确机制、技术边界、常见踩坑、权衡和适用条件。
- **兼容性有证据**：Java、Spring Boot、插件等版本以实施时官方兼容矩阵和真实构建测试为准，不在计划中虚构最新版本或兼容保证。
- **文档与示例同步演进**：不得出现文档代码与仓库实际代码长期不一致。

### 0.3 明确不做

- 不将 Java 简单描述为“有类型的 Node.js”；不将 Spring Boot 简单描述为“Java 版 NestJS”。
- 不用 `pnpm` 管理 Java 的 JAR 依赖；不将 Nx/Turbo 作为 Java 依赖管理器。
- 不在第一阶段引入 Spring Cloud 全家桶、Kubernetes 生产集群、复杂 DDD 或过度微服务化。
- 不提供无法运行、未验证的“企业级模板”；不声称示例具有生产安全性却缺少测试与安全审查。
- 不强制所有章节使用不适合的生活类比；遇到无法准确类比的机制，以小模型/反例替代并解释原因。

---

## 1. 目标读者、学习成果与课程路径

### 1.1 入门假设

读者已经能够：

- 使用 TypeScript、Node.js、NestJS 构建 REST API。
- 理解 Controller、Service、DTO、依赖注入、Guard、Interceptor、ORM、JWT。
- 使用 MySQL、Git、pnpm、Docker 的基础操作。

但不要求掌握 Java、JVM、Maven、Spring、Servlet、JDBC 或 Java 并发。

### 1.2 学习成果（分级验收）

| 级别 | 成果 | 必须展示的证据 |
|---|---|---|
| L1：迁移入门 | 使用 Java 编写基本程序，理解 JVM 与 TypeScript 的主要差异 | 语言练习、编译执行、单元测试通过 |
| L2：后端开发 | 使用 Spring Boot 编写可用 REST API，接入 ORM、校验和异常处理 | 用户 CRUD + API 集成测试 |
| L3：企业业务 | 实现 JWT、RBAC、事务、缓存、数据库迁移与审计 | 权限负例、安全测试、事务回滚测试 |
| L4：工程交付 | 组织 Maven 多模块，完成测试、CI、容器化及观测 | CI 绿灯、Docker 一键启动、操作手册 |
| L5：架构进阶 | 解释 JVM/并发、可靠消息、幂等及故障恢复 | 故障注入演练、性能测量报告 |

### 1.3 建议学习顺序

1. Java 运行时与 TypeScript 语义差异。
2. 现代 Java 语言、集合、泛型、异常、并发模型。
3. Maven 与 Java 项目组织。
4. Spring Boot 核心与 NestJS 同题对照。
5. 数据库、ORM、事务及安全。
6. 测试、部署、JVM 诊断。
7. 消息与分布式可靠性、云原生。
8. 贯穿课程的模块化单体项目最终验收。

> 推荐 16 周渐进学习，仅作课程设计时间预算，不作学习结果保证。读者可根据基础与学习时间调整。

---

## 2. 教学写作规范：每个知识点均须类比

### 2.1 知识点的粒度

“章节”与“知识点”必须区分。例如《Spring IoC》至少拆为 Bean 定义、组件扫描、构造器注入、Bean 作用域、生命周期、循环依赖、容器启动等独立知识点。**每个知识点都执行本节模板**，不可仅在章首放一个统一比喻。

### 2.2 强制十段式知识点模板

每个 `###` 级独立知识点按以下顺序编写：

1. **一句话定义**：准确解释“是什么”，尽可能在 60 字内。
2. **为什么需要它**：提出 NestJS 开发者熟悉的实际问题。
3. **生活类比**：通俗场景说明参与者及其关系；不可掩盖机制差异。
4. **Node.js / TypeScript / NestJS 技术对照**：明确相似概念、不同概念或“无直接等价物”。
5. **两端代码对照**：先提供读者熟悉的 TS/NestJS，再提供 Java/Spring Boot；标注依赖、文件路径、运行命令及输出。
6. **原理与流程图**：说明真实执行链路、生命周期、线程/代理/数据流；复杂机制使用 Mermaid 或独立 SVG。
7. **类比边界与错误直觉**：明确“相似在哪里”“不同在哪里”“不能得出什么结论”。
8. **企业应用场景与选型**：说明何时使用、何时不用，必要时给出替代方案。
9. **常见错误与排查**：提供可复现现象、根因、修复步骤、验证方式。
10. **实操练习与验收**：提供基础题、迁移题、思考题及可执行验收命令。

### 2.3 示例：Spring IoC / DI（片段规范）

**定义**：Spring 容器负责 Bean 的创建、配置、注入及生命周期管理。

**生活类比**：餐厅统一为厨师提供食材与器具，厨师专注做菜。餐厅是容器，厨师是 Service，食材是依赖。

**NestJS 对照**：NestJS `@Injectable()` + Provider 容器与 Spring `@Service` + ApplicationContext 都能管理依赖；但模块可见性、作用域、Bean 后置处理和代理机制并不相同。

**代码对照**：

```ts
// examples/nestjs-reference/.../user.service.ts
@Injectable()
export class UserService {
  constructor(private readonly repo: UserRepository) {}
}
```

```java
// examples/spring-boot-examples/.../UserService.java
@Service
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) {
        this.repo = repo;
    }
}
```

**流程**：容器创建 → 识别 Bean 定义 → 解析依赖 → 构造/初始化 Bean → 对外提供实例。

**边界**：生活类比无法表达 Bean 生命周期回调、作用域及 AOP 代理；禁止由此推断 Spring Bean 与 NestJS Provider 完全等价。

**练习**：实现一个 Service 依赖 Repository，测试注入成功，再演示缺失 Bean 导致的启动错误及诊断。

### 2.4 必须重点制作的对照专题

| Java / Spring 概念 | 生活类比建议 | NestJS / Node 对照 | 必须标出的边界 |
|---|---|---|---|
| JVM | 程序运行的执行平台 | Node.js + V8（仅粗略类比） | JVM 执行字节码及 JIT，Node/V8 模型不同 |
| Java 字节码 | 中间语言指令 | TS 编译产物 JS（不同层级） | JS 源码不是 JVM 字节码 |
| `record` | 已定型的数据卡片 | TS interface + 对象（部分用途） | Java record 是运行时真实类型 |
| 泛型 | 带类型的容器标签 | TS 泛型 | Java 通常类型擦除；TS 类型多在编译期消失 |
| `Optional` | 明确表达可能空的盒子 | `T \| undefined` | Optional 不等同 JS undefined |
| Stream | 数据加工流水线 | `map/filter/reduce` | 惰性、中间/终止操作与复用规则不同 |
| Checked Exception | 必须显式回应的风险告知 | JS throw/try-catch | Java 编译器强制规则不同 |
| 平台线程 | 实际工作人员 | Node Worker Thread（粗略） | 线程、事件循环和进程池不能混同 |
| 虚拟线程 | 轻量任务席位 | async I/O 所解决的部分问题 | 不是 Promise；不消除 CPU 瓶颈 |
| Maven | 采购+装配流水线 | pnpm + scripts（粗略） | Maven 有生命周期、插件、JAR 坐标 |
| Maven Reactor | 多车间按依赖装配 | pnpm workspace 依赖构建 | 构建模型和发布方式不同 |
| Spring Bean | 受容器管理的对象 | Nest Provider | 作用域、代理、可见性不同 |
| IoC / DI | 餐厅中央供给 | Nest DI 容器 | IoC 不是简单的全局单例 |
| Spring AOP | 统一的服务入口检查 | Nest Interceptor（部分场景） | Spring 代理、自调用等边界不同 |
| Servlet Filter | 大门安检 | Express Middleware | 挂载层级、执行位置不同 |
| `@Transactional` | 要么全成要么撤销的交易 | Prisma `$transaction` | 传播、代理、连接绑定、异常回滚不同 |
| MyBatis Mapper | SQL 翻译窗口 | Prisma Client / Repository（粗略） | Mapper 并非完整 ORM |
| JPA Persistence Context | 一次会话中的对象管理台 | Prisma Client（有限类比） | 自动脏检查、实体状态不同 |
| Spring Security | 多层门禁系统 | Nest Guard + Auth | 核心入口是过滤器链，不仅是 Guard |
| Redis 缓存 | 高频数据的临时货架 | Node Redis 客户端 | 一致性、失效和并发问题跨语言相同 |
| RabbitMQ | 带签收和重投的物流系统 | BullMQ / MQ 使用经验 | 交付不等于恰好执行一次 |
| Kubernetes | 多服务器调度中心 | 无 Java 专属对应 | 平台管理容器而非业务事务 |

### 2.5 质量规则

- 禁用“完全相同”“就是 Java 版 XX”等缺乏边界的论断。
- 核心概念优先给**现实直觉**，再给**精确技术解释**。
- 类比须有映射清单（A 对应 B），并包含至少一条失效边界。
- 代码须符合各生态惯用写法；不得为了显得相似而刻意写出反模式。
- 类比服务于理解；对于安全、事务、并发，不得以类比替代严谨因果解释。
- 对于跨版本特性，标注最小版本或适用版本，不可暗示所有版本均可用。

---

## 3. 45 章课程总目录与章节产物

每章最低交付：`README.md`（教程正文）、对应示例代码、至少一项自动化测试或可复现练习、章末验收题；如无需新增代码，必须记录原因和可检查的验证资料。

### Part 01：运行时与工具入门（5 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 01 | Java 生态地图 | JDK/JRE/JVM、Java SE、Spring、Spring Boot | Node、V8、npm、NestJS |
| 02 | Node.js 与 JVM 运行模型 | 编译、字节码、JIT、GC、事件循环与线程 | V8 / Event Loop |
| 03 | 开发环境与调试 | JDK 安装、IDEA/VS Code、断点、调试器 | nvm/fnm、VS Code |
| 04 | Java 编译和执行 | `javac`、`java`、JAR、Classpath、Module Path | `tsc`、Node ESM/CJS |
| 05 | 内存与对象模型 | 堆/栈、引用、GC、类加载初识 | JS GC、对象与引用 |

### Part 02：现代 Java 语言（7 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 06 | 类型系统 | 原始类型、引用类型、装箱、`var`、可空性 | TS 类型系统 |
| 07 | 类、接口与 record | 封装、继承、多态、抽象类、record、sealed | TS class/interface/type |
| 08 | 泛型 | 类型擦除、边界、通配符、PECS | TS Generics |
| 09 | 集合与数据结构 | List/Set/Map、相等性、HashMap | JS Array/Map/Set |
| 10 | Lambda 与 Stream | 函数式接口、流管道、Optional | JS map/filter/reduce |
| 11 | 异常与注解 | checked/unchecked、反射、注解 | JS Error、Decorators |
| 12 | 并发与异步 | Thread、线程池、Future、虚拟线程 | Event Loop、Promise、Worker |

### Part 03：Java 工程化（4 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 13 | pnpm 到 Maven | 仓库、坐标、传递依赖、范围 | package.json / lockfile |
| 14 | `pom.xml` 深入 | 生命周期、插件、BOM、冲突解决 | pnpm scripts / overrides |
| 15 | Maven 与 Gradle | Reactor、Gradle 任务图、缓存、选型 | Turbo/Nx 构建编排 |
| 16 | Java Monorepo | 模块化单体、Maven 多模块、依赖边界 | pnpm Workspace |

### Part 04：NestJS 到 Spring Boot（7 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 17 | 启动过程与自动配置 | SpringApplication、Starter、Auto Configuration | NestFactory/bootstrap |
| 18 | IoC、DI 与 Bean | 容器、构造器注入、作用域、生命周期 | Providers/Modules |
| 19 | Controller / REST API | MVC、路由、参数绑定、响应、异常状态 | Controller/Decorator |
| 20 | DTO 与参数校验 | Bean Validation、嵌套校验、校验分组 | DTO/Pipes/class-validator |
| 21 | 请求处理链 | Servlet、Filter、Interceptor、Security Filter | Middleware/Guard/Interceptor |
| 22 | AOP、代理与异常 | JDK/CGLIB 代理、自调用、Advice、ControllerAdvice | Interceptor/ExceptionFilter |
| 23 | 配置与生命周期 | Profiles、Properties、Configuration、Actuator | ConfigModule/Lifecycle Hooks |

### Part 05：数据访问（5 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 24 | JDBC 与连接池 | Driver、DataSource、HikariCP、连接生命周期 | Prisma 连接池概念 |
| 25 | MyBatis / MyBatis-Plus | Mapper、Wrapper、XML SQL、分页、N+1 防范 | Prisma Client |
| 26 | JPA / Hibernate / jOOQ | Entity、Persistence Context、脏检查、类型安全 SQL | Prisma ORM 与查询 API |
| 27 | 事务机制 | ACID、传播、隔离、代理陷阱、回滚规则 | `$transaction` |
| 28 | 数据库工程化 | Flyway、索引、执行计划、慢 SQL、schema 管理 | Prisma Migrate |

### Part 06：安全与企业业务（5 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 29 | Spring Security | FilterChain、Authentication、SecurityContext | Nest Passport / Guard |
| 30 | JWT 与会话 | Access/Refresh Token、过期、轮换、撤销、CSRF 取舍 | Nest JWT |
| 31 | RBAC 权限系统 | 用户/角色/权限、按钮/接口/数据权限、越权防范 | Nest RBAC |
| 32 | Redis 与缓存设计 | TTL、穿透/击穿/雪崩、一致性、并发 | ioredis / cache-manager |
| 33 | 企业基础能力 | 文件、对象存储、审计、OpenAPI、脱敏 | Multer / Swagger |

### Part 07：测试与诊断（4 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 34 | 单元测试 | JUnit、Mockito、AssertJ、测试隔离 | Jest / Vitest |
| 35 | 集成与契约测试 | MockMvc、Testcontainers、API 合约、测试数据 | Supertest / e2e |
| 36 | JVM 性能诊断 | JFR、GC、heap dump、thread dump、压测 | Node profiling |
| 37 | 可观测性 | Actuator、Micrometer、Tracing、结构化日志 | OpenTelemetry / Pino |

### Part 08：分布式与云原生（4 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 38 | 消息队列 | RabbitMQ/Kafka、确认、重试、DLQ、幂等 | BullMQ、Node MQ 客户端 |
| 39 | 分布式可靠性 | 超时、熔断、Outbox、最终一致性、租约 | Agent Worker 设计 |
| 40 | 容器与编排 | Docker、Kubernetes、健康检查、滚动更新 | Node Docker 部署 |
| 41 | 自动交付 | GitLab CI/GitHub Actions、镜像、配置、回滚 | 现有 Node CI/CD |

### Part 09：最终工程实战（4 章）

| 编号 | 章节 | 核心知识点 | 对照重点 |
|---|---|---|---|
| 42 | 需求与架构设计 | 模块边界、数据库设计、ADR、API 契约 | NestJS 项目拆分 |
| 43 | Spring Boot 业务实现 | 用户、认证、RBAC、订单/代理商、审计、测试 | NestJS 对照实现 |
| 44 | Vue3 前后端联调 | OpenAPI、生成 TS Client、错误码、权限菜单 | 既有 Vue3 管理端 |
| 45 | 生产交付与验收 | Docker Compose、CI、监控、备份、故障演练 | Node 项目生产部署 |

---

## 4. 技术基线与版本治理

### 4.1 技术选择（基线候选，实施时核验）

| 层级 | 首选 | 说明 |
|---|---|---|
| Java | Java 21 LTS 起步；提供 25 LTS 的兼容验证 | 以第三方依赖支持和构建测试为准 |
| Spring | Spring Boot 3.5.x 作为兼容性优先基线；评估 Boot 4.x 分支 | 不直接假设全部 Starter 已适配 |
| 构建 | Maven Wrapper + Maven 多模块 | 首版不强制 Gradle/Nx |
| ORM | MyBatis-Plus（主线）；JPA/jOOQ 独立比较示例 | Spring Boot Starter 兼容性先验证 |
| 数据迁移 | Flyway | 不依赖 ORM 自动修改生产表结构 |
| 安全 | Spring Security + JWT | 采用最小权限与显式路由授权 |
| 存储 | MySQL + Redis | Compose 供本地使用，生产需独立策略 |
| API | Spring MVC + OpenAPI | API 契约可自动验证 |
| 测试 | JUnit、Mockito、Testcontainers | 测试隔离、真实 DB 场景 |
| 前端 | Vue3 + TypeScript + Vite + pnpm | 与现有认知一致 |
| 文档 | VitePress + Markdown + Mermaid | 复杂图可补充 SVG |
| 交付 | Docker Compose + CI，进阶增加 K8s | 可部署、可复现 |

### 4.2 版本锁定流程

P0-06安全补充（2026-11-10）：依赖审计发现文档构建链存在高危项，按`docs/adr/0002-documentation-build-security.md`保留VitePress1.6.4稳定版，使用经验证的Vite6.4.4及已修复lodash-es覆盖版本。必须记录审计、Windows/Linux构建及浏览器回归证据；不改变45章范围或Java/Spring主线。此项为v1.1相对v1.0的修订。

P1-04参照基线补充（2026-11-10）：按`docs/adr/0003-transaction-reference-runtime.md`为第27章锁定Prisma7.10.1稳定版及同版官方MySQL/MariaDB适配器、JS驱动mariadb3.5.4，使用既定MySQL8.4.7。Flyway SQL是实验表结构唯一来源；独立Linux CI执行真实双端事务断言，禁止以mock或仅编译代替。此项为v1.2的参照运行时扩充，不采用Prisma8 RC、不改变Java21/Boot3.5主线、45章范围或毕业项目架构。

正式编码前，创建 `docs/00-introduction/compatibility-matrix.md`，记录：

- JDK / Spring Boot / Maven / MyBatis-Plus / Security / OpenAPI / Flyway / Testcontainers 版本及依据。
- 官方支持范围、最低运行版本、已知冲突与解决方式。
- 在 Windows 11 + Linux CI 上跑通的结果。
- 升级前的回归测试清单。

所有代码示例需标注所属基线；需要 Boot 4 特性的示例独立放置，不与兼容性基线混杂。

---

## 5. 仓库结构与源码管理

```text
java-for-nestjs-developers/
├── README.md
├── CONTRIBUTING.md
├── LICENSE
├── docs/
│   ├── index.md
│   ├── .vitepress/
│   ├── 00-introduction/
│   │   ├── learning-guide.md
│   │   ├── analogy-standard.md
│   │   ├── compatibility-matrix.md
│   │   └── glossary.md
│   ├── 01-java-runtime/       # 第 01~05 章
│   ├── 02-java-language/      # 第 06~12 章
│   ├── 03-build-tools/        # 第 13~16 章
│   ├── 04-spring-boot/        # 第 17~23 章
│   ├── 05-database/           # 第 24~28 章
│   ├── 06-security/           # 第 29~33 章
│   ├── 07-testing-jvm/        # 第 34~37 章
│   ├── 08-cloud-native/       # 第 38~41 章
│   ├── 09-enterprise-project/ # 第 42~45 章
│   └── appendices/
├── examples/
│   ├── java-basics/
│   ├── nestjs-reference/
│   └── spring-boot-examples/
├── project/
│   ├── apps/
│   │   ├── admin-web/
│   │   └── admin-server/
│   ├── java-modules/
│   │   ├── common-core/
│   │   ├── common-web/
│   │   ├── common-security/
│   │   ├── module-system/
│   │   └── module-business/
│   ├── deploy/
│   │   ├── compose/
│   │   └── kubernetes/
│   ├── pom.xml
│   └── pnpm-workspace.yaml
├── scripts/
│   ├── check-doc-structure.mjs
│   ├── check-example-links.mjs
│   └── verify-compatibility.mjs
├── .github/workflows/       # 若采用 GitLab，等价配置 .gitlab-ci.yml
└── IMPLEMENTATION-PLAN.md
```

### 5.1 模块边界要求

- `admin-server`：启动、应用装配、外部入口；不堆积全部业务实现。
- `common-core`：无 Spring Web 耦合的基础模型与工具。
- `common-web`：异常映射、响应模型、Web 配置。
- `common-security`：认证与授权共性基础设施。
- `module-system`：用户、角色、权限、字典。
- `module-business`：订单或代理商等领域案例。
- **禁止公共模块反向依赖业务模块**，避免循环依赖。
- **禁止将所有实体、DTO 和 SQL 放进 `common-core`**。
- 前端 `pnpm` 与后端 Maven 独立管理依赖；后续是否引入 Nx 需 ADR 决策。

### 5.2 教程、示例和提交的映射

- 每章文档保存为 `docs/<part>/<nn>-<slug>.md`。
- 文中出现的完整代码指向 `examples` 或 `project` 中的真实文件。
- 示例需要“运行命令、预期输出、失败排查”；高价值代码用测试验证。
- 通过 `chapter-map.yml`（实施阶段创建）记录章节 → 文件 → 测试 → 技术标签的对应关系。

---

## 6. 分阶段实施任务（按依赖顺序执行）

> 状态定义：`todo` → `in_progress` → `review` → `done` / `blocked`。每个阶段完成并通过验收后再进入下一阶段。

### P0：基线、仓库与模板（必须最先完成）

**任务**

- [ ] P0-01 建立文档与代码仓库、许可证、README、贡献规范。
- [ ] P0-02 确定 Java / Spring Boot / 插件兼容矩阵，提交 ADR。
- [ ] P0-03 配置 VitePress、中文导航、章节索引、搜索和 Mermaid 支持。
- [ ] P0-04 创建知识点十段式 Markdown 模板和对照代码模板。
- [ ] P0-05 初始化 Maven Wrapper、pnpm Workspace、Docker Compose。
- [ ] P0-06 创建 CI 基础任务：文档构建、Java 编译、JS 检查、测试。
- [ ] P0-07 编写 `chapter-map.yml` 和自动校验器最小版本。

**验收**：文档站可以构建；Java 示例可以编译；示例运行命令可复现；CI 基础流水线通过；兼容矩阵有来源。

### P1：教学样章试点（先验证写作方式）

**优先样章**

- [ ] P1-01 第 02 章《Node.js 与 JVM 运行模型》。
- [ ] P1-02 第 08 章《泛型：TS 泛型与 Java 类型擦除》。
- [ ] P1-03 第 18 章《IoC、DI 与 Bean》。
- [ ] P1-04 第 27 章《Spring 事务与 Prisma `$transaction`》。

**验收**：四章全部使用十段式模板；每个知识点有类比及失效边界；代码可运行；读者能够指出至少 3 个关键差异；审稿无“伪等价”映射。

> **闸门（v1.3）**：四篇样章的技术、结构、独立Review、适用实验与CI通过且反馈已修订模板后，允许依次实施后续章节。真实读者能指出至少3个差异单独保留为未验证，纳入P7-03，最终发布前必须闭环；不得以作者或AI审稿替代。按用户再次要求继续实施的指令采用此分离，见[ADR-0004](docs/adr/0004-reader-feedback-and-phase-execution.md)。这不自动验收任何章节或批量生成占位正文。

### P2：Java 语言与工程化（章节 01~16）

- [ ] P2-01 完成 01~05，建立编译、运行、内存的可视化示例。
- [ ] P2-02 完成 06~12，增加 TS/Java 同题练习与单测。
- [ ] P2-03 完成 13~16，Maven Reactor 多模块示例可运行。
  Gradle任务图与缓存比较使用独立9.2.1小实验，选择及边界见[ADR-0005](docs/adr/0005-gradle-comparison-runtime.md)，不迁移Maven主线。
- [ ] P2-04 校验全章节术语统一、跨章链接与章末练习。

**验收**：读者无需 Spring 即可用 Java 完成中等复杂度数据处理、泛型、异常和并发练习；Maven 能正确构建共享模块与调用方。

### P3：Spring Boot 与数据访问（章节 17~28）

双端HTTP参照固定版本及隔离边界见[ADR-0006](docs/adr/0006-http-reference-runtime.md)，继续Boot3.5.16/Nest11主线，不因npm最新Swagger要求Nest12而隐式升级。

- [ ] P3-01 完成 17~23，建立 Controller → Service → Mapper 的主路径。
- [ ] P3-02 完成 24~28，接入 MySQL、Flyway 和 ORM。
- [ ] P3-03 演示与验证：Bean 缺失、循环依赖、事务自调用、异常回滚、连接池耗尽等典型场景。
- [ ] P3-04 NestJS 对照端提供同名资源和匹配 API 行为。

**验收**：两端 API 契约一致；事务和校验负例覆盖；能讲清 Filter、AOP、事务代理与数据库连接边界。

### P4：安全、业务、测试与诊断（章节 29~37）

- [ ] P4-01 完成 29~33，独立实现 JWT + RBAC + Redis + 审计。
- [ ] P4-02 完成 34~37，单测、集成测试、JVM 性能诊断与观测。
- [ ] P4-03 设计授权测试矩阵：未登录、无权限、跨租户/跨部门、Token 失效、刷新重放。
- [ ] P4-04 文件上传实现尺寸、类型、路径、安全扫描接口等基础安全措施（按实际能力标注）。

**验收**：越权请求被拒绝；关键业务具备单测+集成测试；可观察请求错误、耗时及关联日志；不存在硬编码生产密钥。

### P5：分布式与云原生（章节 38~41）

- [ ] P5-01 可靠消息演示：消费重试、重复投递、死信与幂等。
- [ ] P5-02 事务性 Outbox 及故障注入：数据库提交成功但消息发送失败。
- [ ] P5-03 Docker Compose 可一键启动整个示例；编写健康检查和停止处理。
- [ ] P5-04 K8s 示例展示 Deployment、Service、探针、资源限制、滚动发布。
- [ ] P5-05 CI 构建、测试、镜像扫描、发布与回滚演练。

**验收**：故障演练有日志、预期和实际结果；重试不会重复产生关键业务副作用；容器停止时可优雅处理正在进行的请求。

### P6：企业级 Monorepo 毕业项目（章节 42~45）

- [ ] P6-01 编写 ADR：为什么选模块化单体、Maven 多模块、MyBatis-Plus。
- [ ] P6-02 Java 服务实现用户、角色、菜单、按钮权限、订单/代理商示例。
- [ ] P6-03 Vue3 管理端接入 OpenAPI 生成的 TS Client；完成联调。
- [ ] P6-04 构建 CI 全链路、Docker Compose 部署、备份恢复与监控。
- [ ] P6-05 按验收矩阵完成安全、事务、性能与故障测试。

**验收**：从空机器按 README 可启动、执行测试、登录、完成业务流程、构建产物并验证故障恢复；每一步可复现。

### P7：全书审校与发布

- [ ] P7-01 全站技术校对：官方资料、概念边界、示例版本。
- [ ] P7-02 文档一致性检查：术语、代码、链接、目录、章节依赖。
- [ ] P7-03 阅读体验测试：找 1~3 名已有 NestJS 经验的读者试学样章。
- [ ] P7-04 修复反馈并生成变更日志、课程版本、学习路线图。
- [ ] P7-05 发布教程站点与 GitHub Releases。

**验收**：所有 CI 通过、章节完整、例子可运行、无已知高危安全缺陷；发布版本有明确支持矩阵与限制说明。

---

## 7. 单章 Definition of Done（DoD）

某章节满足以下全部要求才允许标记 `done`：

- [ ] 知识点全部拆分，章节范围与先修依赖明确。
- [ ] **每个知识点**均有定义、动机、生活类比、技术对照、边界。
- [ ] 同题对照代码可构建；对关键输出提供测试或日志截图/命令记录。
- [ ] 对核心执行链路给出机制解释和流程图。
- [ ] 至少一个企业场景、一个反例或常见错误及排查步骤。
- [ ] 练习和验收有明确通过条件；答案与提示不直接泄露在练习前。
- [ ] 版本、依赖、运行环境、相关官方链接可追溯。
- [ ] 文档无断链、错别字与跨章概念冲突。
- [ ] 独立 Review 通过，不只是编写 Agent 自评通过。
- [ ] 更新目录、章内导航、章节映射和阶段进度。

### 7.1 类比质量评分（每项 0~2 分，共 10 分）

| 评分项 | 2 分标准 |
|---|---|
| 通俗易懂 | 不依赖新术语即可理解直觉 |
| 技术准确 | 类比不违背真实机制 |
| 映射清晰 | 明确哪些角色/动作对应哪些组件 |
| 边界明确 | 指出至少一条类比失效处 |
| 迁移有效 | 能连接 Node/NestJS 的已知经验 |

建议每个核心知识点 ≥ 8/10，**技术准确性或边界为 0 时直接不通过**。分数仅辅助 Review，不能取代事实核验。

---

## 8. 测试与 CI 质量门禁

| 门禁 | 自动化方式 | 阻断条件 |
|---|---|---|
| 文档构建 | VitePress build | 构建失败 |
| Markdown / 链接 | Markdownlint + 内部链接校验 | 断链、结构错误 |
| 知识点模板 | frontmatter/结构检查器 | 核心十段式缺项 |
| Java 编译 | Maven Wrapper `verify` | 编译或测试失败 |
| Java 格式/静态检查 | Spotless/Checkstyle（择一） | 严重规范问题 |
| TS 对照代码 | `pnpm install --frozen-lockfile` + build/test | 构建或测试失败 |
| API 契约 | OpenAPI / HTTP 集成测试 | 路由、字段、错误码不一致 |
| 安全 | 依赖安全扫描 + 授权负例 | 高危未处理、越权成功 |
| 容器 | Compose 启动与健康检查 | 无法启动、探针失败 |
| 架构边界 | Maven 依赖与架构规则测试 | 依赖环、跨层违规 |
| 版本治理 | compatibility matrix 检查 | 示例版本未经验证或漂移 |

> 类比是否准确、教学是否容易理解、架构取舍是否合理需要人工/独立 Agent 审核，不能仅通过结构化脚本决定。

---

## 9. Agent 协作与执行规则

### 9.1 建议职责

- **Tech Lead / Planner**：管理章节依赖、技术基线、ADR、阶段拆解与最终裁决。
- **Writer**：写教学正文，确保每知识点十段式模板。
- **Java Implementer**：实现 Maven/Spring Boot 示例及对应测试。
- **NestJS Implementer**：实现或校正 TS/Nest 对照代码。
- **Independent Reviewer**：核查技术事实、伪等价、兼容性、代码与安全。
- **QA**：运行 CI、复现练习、接口对照测试和故障演练。

小团队可以由同一执行者承担多角色，但 Review 必须保持独立验收标准。

### 9.2 Git 工作流（每项任务独立分支）

1. 从最新 `main` 创建任务分支，例如 `feat/chapter-018-spring-ioc`。
2. 只修改该任务对应的文档、示例、测试与必要的公共配置。
3. 本地运行与任务相关测试、结构检查及文档构建。
4. 创建 PR：写明目标、对照知识点、验证命令、截图/日志、风险。
5. 独立审查：技术正确性、类比边界、代码一致性和安全。
6. CI 全绿且通过验收后合并到 `main`；下一任务再从最新 `main` 开分支。
7. 不自动跳过 Review，不因为 Agent 声称“已完成”而视为通过。

### 9.3 任务状态与进度记录

建立 `TASKBOARD.md` 或项目看板，每个任务包含：`id`、章节、负责人、依赖、状态、验收项、PR、测试证据、风险、阻塞原因。

跨章节公共能力先独立实施，避免多个 Agent 同时编辑根 `pom.xml`、`package.json` 或导航配置引起冲突。并行任务优先安排在**不同章节、不同示例模块、无共享写入冲突**的范围内。

### 9.4 编写 Agent 的标准任务提示词

```text
你正在实施《从 NestJS 到 Spring Boot》教程中的指定任务。

先阅读 IMPLEMENTATION-PLAN.md、compatibility-matrix.md、analogy-standard.md、chapter-map.yml 和已有代码。

任务：<TASK_ID / CHAPTER>
Base Branch：main
目标：<本章交付内容>

必须遵守：
1. 按知识点拆分，不得只在章节级写一个类比。
2. 每个知识点必须包含：一句话定义、动机、生活类比、NestJS/Node 对照、双端代码、原理与流程、类比边界、场景、错误排查、练习验收。
3. 无准确技术对应时标记“无直接等价物”；禁止虚构概念映射。
4. 提交的完整示例必须能运行；文档中的路径和命令真实存在。
5. 使用兼容矩阵锁定版本；任何版本改动需先说明原因并验证。
6. 不扩大任务范围，不无故修改其他章节/公共配置。
7. 必须执行相关测试并报告真实结果；未运行不得声称通过。
8. 输出变更文件、类比检查表、构建/测试结果、剩余风险与待审问题。

完成后创建 PR 供独立 Review；不要绕过质量门禁或自行宣布合并。
```

### 9.5 审查 Agent 的标准提示词

```text
作为独立技术审查者，对本次教程任务进行验收。
重点审查：
- 每个独立知识点是否都包含类比及边界，而非章节层面敷衍。
- TypeScript/NestJS 与 Java/Spring 之间是否存在错误等价。
- 机制说明是否准确（JVM、代理、事务、线程、Security Filter 等）。
- 文中示例是否与仓库代码一致、是否能实际运行。
- 安全、事务、并发、错误处理是否存在真实缺陷。
- 版本兼容、链接、章节前置条件和测试证据是否完整。

按严重程度输出问题，每个问题给出文件位置、可验证证据、风险、建议修复。
不以代码风格偏好代替真实问题；不因作者声称测试通过就默认通过。
```

---

## 10. 风险、依赖和应对措施

| 风险 | 表现 | 应对 |
|---|---|---|
| 类比误导 | 把 Promise 等同虚拟线程 | 强制“类比边界”；独立技术 Review |
| 章节重复 | IoC、Bean、AOP 多章反复 | 术语表 + 知识点归属表 + 跨章链接 |
| 样例无法运行 | 文档复制代码失败 | 示例仓库映射 + CI 编译/测试 |
| Spring 版本不兼容 | Starter 与 Boot 不匹配 | 兼容矩阵 + 锁版本 + 独立升级分支 |
| 多 Agent 冲突 | 多人改根配置/导航 | 任务依赖 + 文件所有权 + 单写者策略 |
| 项目过度复杂 | 初学阶段引入微服务 | 模块化单体为主线；进阶另开专题 |
| 安全示例误导 | 明文密码、弱 JWT、无权限校验 | 安全基线与负面用例测试 |
| 分布式过度承诺 | 宣称消息“恰好一次” | 幂等、重放、故障注入与边界说明 |
| 测试不稳定 | 依赖共享数据库和时序 | Testcontainers、隔离数据、明确等待条件 |
| 文档站与代码漂移 | 改实现不更新正文 | 章节映射、变更检查、CI 阻断 |

---

## 11. 实施优先级与首个迭代

**优先级顺序：P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7。**

第一轮不直接生成 45 章正文。先完成以下可独立验收的最小交付：

1. 仓库骨架 + README + VitePress 基础导航。
2. Java / Spring Boot / ORM 兼容矩阵和基线 ADR。
3. 统一知识点模板、类比评分表和章节映射文件。
4. 四篇试点样章：运行时、泛型、IoC、事务。
5. 对应的 NestJS/Java 双端代码与测试。
6. 首轮独立 Review + 修订课程模板。

**第一迭代成功标准**：新的作者或 Agent 不需要再次口头解释规则，只依赖仓库文档就能新增一个符合规范、可运行、可验证的教学章节。

---

## 12. 发布验收清单

- [ ] 45 章全部存在且有完整正文，不只是占位文件。
- [ ] 每章均通过知识点结构、类比准确性和技术审查。
- [ ] 示例可在支持的 JDK、Node、数据库版本上复现。
- [ ] 所有代码构建、测试、文档构建、接口契约检查通过。
- [ ] NestJS 与 Spring Boot 对照具有一致的业务语义，差异清晰标注。
- [ ] 模块化单体完整可启动，有数据库迁移和基础 RBAC。
- [ ] 安全授权、事务回滚、缓存及消息异常路径均有验证。
- [ ] Docker、CI、故障演练和问题排查文档完整。
- [ ] 新读者能够找到学习入口、进度路径、术语解释和项目运行方式。
- [ ] 发布日志标记技术版本、已知限制、后续升级计划。

---

## 13. 最终执行指令

以本文作为**唯一的实施范围与验收基线**，优先建立仓库、兼容矩阵、知识点模板和四篇试点样章。完成并验证试点后，按章节依赖分批推进。每一任务都必须提交可运行示例和审查证据，完成测试及独立 Review 后方可合并。任何对技术基线、课程目录、强制类比模板和毕业项目架构的重大调整，先提交 ADR 并更新本计划，不得在实现中暗改。
