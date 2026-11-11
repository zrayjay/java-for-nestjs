---
title: '17 · 启动过程与自动配置'
description: 用真实Boot上下文验证启动、条件装配和用户Bean退让。
chapter_id: '17'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 17 · 启动过程与自动配置

## 章节范围与先修

读完第07、11、14章后，本章把main、注解与Maven依赖连成真实启动过程。完整实验见[HTTP实验入口](../../examples/spring-web-lab/README.md)，源码[ApiApplication](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/ApiApplication.java)、[Nest入口](../../examples/nestjs-reference/src/api/main.ts)，测试[BootModelTest](../../examples/spring-web-lab/src/test/java/dev/javafornestjs/web/BootModelTest.java)。数据库和生产认证尚未接入。

## 知识点

### ch17-kp01 · 从main到可接受请求

#### 1. 一句话定义

SpringApplication启动过程准备环境与ApplicationContext、注册并实例化Bean、刷新上下文，在Web应用中启动嵌入式服务器，最后发布相应启动事件。

#### 2. 为什么需要它

遇到“编译成功但服务没起来”，必须区分构建、进程启动、依赖装配、端口监听和业务就绪。一个Java main正常加载，不表示Controller已可访问；Nest的bootstrap被调用也不表示listen已完成。

#### 3. 生活类比

开餐厅要先读取营业安排，配好员工和设备，再开门接客。main是经理到场，容器刷新是人员设备配置，服务器监听是开门。类比只帮助排序：网络端口已经打开也不代表远端数据库或业务资源健康。

#### 4. Node.js / TypeScript / NestJS 技术对照

可比的是Boot入口与NestFactory都组织容器启动。差异一：Boot可按classpath和配置推断Web应用类型，Nest入口显式选择create或createApplicationContext。差异二：SpringApplication.run返回上下文，NestFactory.create后仍需await app.listen。差异三：JAR带有字节码和依赖布局，而Node入口执行编译后的JS；不能把node_modules安装过程等同于容器启动。

#### 5. 两端代码对照

Java完整ApiApplication在run返回后读取实际服务器端口，输出`JAVA_API_PORT`。Nest完整main在await listen后输出`NEST_API_PORT`：

```ts
const app = await createApi(Number(raw))
console.log(`NEST_API_PORT=${new URL(await app.getUrl()).port}`)
```

本仓库[共同验证器](../../scripts/verify-http-contract.mjs)不是只寻找日志里的“Started”，而是先等待端口标志，再向真实端口发送HTTP并检查响应。Java非Web实验使用SpringApplicationBuilder指定WebApplicationType.NONE，不能据此宣称服务器启动通过。

#### 6. 原理与流程图

```text
main → 准备Environment → 创建/刷新Context → 创建Bean与Web服务器
     → runner等启动回调 → ready事件 → main打印实际端口 → HTTP验证
```

这是排查顺序的压缩图，不是所有内部回调的完整时序表。ApplicationReadyEvent比Bean初始化更晚；本实验只证明探针construct、init、ready的相对次序，不对所有Bean之间排序作承诺。

#### 7. 类比边界与错误直觉

“ready”是框架启动阶段事件，不是业务SLA。一个启动阶段未访问的外部服务可能在第一个请求时才失败。端口设置为0是让操作系统选择可用端口，不能对外请求端口0。异常被catch后仅打印而退出码仍为0，会误导自动化；Node入口因此明确设置非零exitCode。

#### 8. 企业应用场景与选型

HTTP服务使用Web启动，批处理与CLI可用非Web上下文复用DI。启动检查适合发现必要配置缺失；不要把耗时且不稳定的外部扫描塞入每个Bean构造器。就绪探针、存活探针和业务依赖探针应分开设计，本章仅提供最小健康入口。

#### 9. 常见错误与排查

先确认JDK与class版本，再确认主类、classpath和打包产物，然后查最初的Caused by。端口占用应检查实际监听者，不要靠反复换依赖解决。Nest入口未await listen时检查异步调用链；Spring因Bean缺失失败时检查定义来源和扫描边界，见第18章。

#### 10. 实操练习与验收

按HTTP实验README构建并运行`pnpm http:verify`。预期两个独立进程都通过契约。运行BootModelTest观察非Web上下文仍产生ready并可显式关闭。故意把Java端口设成被占用端口，要求能指出服务未成功监听，而不是根据main存在判断成功；该手动占用练习不计入已有自动测试证据。

### ch17-kp02 · Starter、自动配置与条件退让

#### 1. 一句话定义

Starter主要组织依赖，自动配置根据classpath、属性、已有Bean等条件注册默认定义，用户配置可使这些默认定义退让。

#### 2. 为什么需要它

导入web Starter后服务器出现，不是Maven“执行了服务器”，而是运行时依赖及自动配置满足条件。理解依赖和注册的区别，才能解释为什么删掉一个依赖会改变上下文，以及为什么自定义Bean后默认实现消失。

#### 3. 生活类比

Starter像设备套餐清单，自动配置像“店里没有咖啡机时提供默认型号”的安装规则。购买套餐不等于安装完成；自带机器会让默认安装退让。条件是在容器配置阶段评估，不是每个HTTP请求都重新选一次机器。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest Module通过imports/providers等显式声明装配，动态模块可以按参数返回不同配置，因此可比较“提供默认能力”的目的。但Nest import一个npm包不会自动扫描该包里的任意Provider；Boot Starter也不等同于Nest @Module。Spring的条件注解和自动配置导入机制需要单独理解，不能把@Module重命名成@Configuration就认为迁移结束。

#### 5. 两端代码对照

Java完整文件[FeatureAutoConfiguration](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/feature/FeatureAutoConfiguration.java)使用AutoConfiguration、ConditionalOnProperty和ConditionalOnMissingBean，默认返回message为auto的FeatureGreeting；资源[AutoConfiguration.imports](../../examples/spring-web-lab/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)列出自动配置类。测试通过AutoConfigurations.of隔离条件行为，再用真实Boot启动核对默认导入。

Nest [API模块](../../examples/nestjs-reference/src/api/api.ts)显式列出TaskService和TaskRepository；这是可运行的显式装配对照，不冒充Boot自动配置复刻。第18章继续讲注册与模块可见性。

#### 6. 原理与流程图

```text
解析自动配置候选 → 检查属性等条件 → 检查是否已有目标Bean
  → 没有：注册默认FeatureGreeting
  → 已有：默认定义退让，保留用户Bean
```

用户Bean退让不是创建两个实例后挑一个。测试断言只有一个FeatureGreeting并且message为custom。配置类排序和条件所见定义会影响复杂场景，因此自动配置应使用官方约定的导入机制，而不是随意扫描所有库包。

#### 7. 类比边界与错误直觉

自动配置不等于无配置；默认值、条件和用户覆盖共同构成配置。`@SpringBootApplication`的组件扫描与自动配置导入是相关但不同的机制。`@ConditionalOnMissingBean`不意味着动态热切换：容器启动后添加对象不会自动重跑整个配置阶段。

#### 8. 企业应用场景与选型

内部公共库可以提供可覆盖的默认监控或客户端Bean。业务实现通常仍由应用明确配置。优先使用框架提供的Starter和配置扩展点，避免自建“扫描所有实现再按名称选一个”的隐式规则；跨Boot主版本升级必须重查Starter坐标和兼容性。

#### 9. 常见错误与排查

默认Bean没出现时检查属性是否禁用、用户定义是否已满足条件、依赖和导入资源是否存在。可用`--debug`查看条件报告，但应按具体配置名找匹配结果，不把所有negative matches都当错误。测试只注册普通配置而漏掉自动配置导入时，不应凭测试容器的结果推断完整应用行为。

#### 10. 实操练习与验收

运行BootModelTest：默认auto、`lab.feature.enabled=false`时不存在、用户custom时唯一实例三种情况必须成立。删除imports资源会影响真实Boot导入，而显式AutoConfigurations.of的隔离测试仍可能通过；解释为什么两种测试都需要。恢复修改后重新verify，不能把故障注入留在main。

## 章末验收与审查记录

本机已执行Java模块verify，13项测试通过；Nest全量25项通过；打包进程共同契约通过。当前为draft，独立审查及新增CI矩阵证据尚需补齐，未作课程验收通过声明。官方参考：[Boot启动](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html)、[自动配置](https://docs.spring.io/spring-boot/3.5/reference/using/auto-configuration.html)、[创建自动配置](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html)、[Nest第一步](https://docs.nestjs.com/first-steps)。
