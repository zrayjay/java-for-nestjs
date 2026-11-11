---
title: '23 · 配置与生命周期'
description: 验证类型化配置、profile覆盖、初始化关闭与最小Actuator暴露。
chapter_id: '23'
teaching_status: in_review
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 23 · 配置与生命周期

## 章节范围与先修

先完成17~18章。Java完整[ApiProperties](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/ApiProperties.java)、[生命周期探针](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/LifecycleConfiguration.java)、[application.yml](../../examples/spring-web-lab/src/main/resources/application.yml)与[Nest配置探针](../../examples/nestjs-reference/src/api/configuration.ts)构成实验。Nest当前使用显式类型化Provider，未安装或执行ConfigModule；本章会说明它的比较位置，不能把概念介绍当实测。

## 知识点

### ch23-kp01 · 类型化配置与启动失败

#### 1. 一句话定义

类型化配置把环境中的文本绑定为具有结构和约束的对象，让必要配置错误尽早在启动时暴露。

#### 2. 为什么需要它

在各处读取字符串环境变量容易散落默认值和转换逻辑。服务已经监听后才发现必要配置为空，会把部署错误变成运行期故障，且不同代码路径可能采用不一致的值。

#### 3. 生活类比

开店前统一核对营业许可证和设备参数，比每个窗口接待时重新翻找纸条可靠。清单写“必填”必须真正检查，不能只在说明文档中标红。

#### 4. Node.js / TypeScript / NestJS 技术对照

Boot ConfigurationProperties支持结构化绑定、类型转换和Validation。Nest常用ConfigModule/ConfigService组织配置，但TS类型本身不验证process.env。本实验用factory Provider调用readConfiguration显式验证并冻结配置对象；它不提供ConfigModule的文件加载或缓存能力。

#### 5. 两端代码对照

Java ApiProperties是lab前缀的record，带Validated与NotBlank，默认label为lesson，由EnableConfigurationProperties启用。BootModelTest传入空白lab.label并沿真实异常因果链找到BindValidationException及NotBlank错误，不接受任意启动异常冒充验证成功。Nest readConfiguration默认lesson，空白LAB_LABEL抛错，配置factory在容器创建阶段执行。

#### 6. 原理与流程图

```text
配置源 → 属性解析/绑定 → 类型与约束检查 → 配置Bean/Provider → 服务使用
                         失败 → 启动失败，不进入正常服务
```

Java绑定失败通过真实上下文验证；Nest当前自动测试直接验证读取函数及正常容器生命周期。不能把Nest函数负例写成已验证全容器失败，后续可补相应启动负例。

#### 7. 类比边界与错误直觉

有类型不代表来源可信；环境变量仍是外部输入。Frozen对象不保护密钥存储或日志。ConfigurationProperties与在字段上到处Value注入并非同一组织方式，后者可用于少量值但会增加分散验证风险。

#### 8. 企业应用场景与选型

连接地址、超时和业务开关适合集中配置并在启动时验证。密钥应来自适当的部署秘密管理，避免提交到公开仓库。动态刷新是额外机制，本例不自动更新已注入对象，不能把修改环境变量当即时生效。

#### 9. 常见错误与排查

值仍为默认时核对属性前缀、属性源和启动命令实际传参。验证不执行时检查Validated、校验依赖和配置注册。启动失败应核对最底层原因是否真是约束错误，而非端口或无关Bean缺失。

#### 10. 实操练习与验收

运行BootModelTest的非法配置用例及Nest配置测试。Java要求真实NotBlank绑定错误，Nest要求空白配置抛出指定错误。手动以空白环境启动Nest观察容器失败可作为新增练习，不提前计作现有自动证据。

### ch23-kp02 · Profile与属性源覆盖

#### 1. 一句话定义

Profile用于按环境激活一组配置或定义，最终属性值还由Boot属性源优先级决定，Profile本身不是全部覆盖规则。

#### 2. 为什么需要它

开发、测试和部署需要不同配置，但复制完整配置文件容易漂移。理解“激活哪个profile”与“哪个来源最终赢”是两件事，才能排查为什么改了YAML却没有生效。

#### 3. 生活类比

同一家店有平日和节假日排班方案，同时总部可能发临时通知。选择节假日方案不表示能覆盖所有更高优先级通知；需要按明文规则确定最终安排。

#### 4. Node.js / TypeScript / NestJS 技术对照

Boot有profile配置文件和Profile条件，Nest通常由ConfigModule加载文件或自定义factory按环境选择。NODE_ENV字符串不会自动让任意Nest Provider遵循Boot profile规则。当前Nest只读取传入环境对象，测试传label为test-profile并不等于执行了Spring式Profile机制。

#### 5. 两端代码对照

Java基础application.yml设置lesson，testresources的[application-test.yml](../../examples/spring-web-lab/src/test/resources/application-test.yml)设置test-profile。WebApiTest用ActiveProfiles("test")，通过真实/lab-info验证最终label。非WebBoot测试同样激活test后从配置Bean读取值。Nest readConfiguration接受环境对象；其测试不修改进程全局环境，避免并行测试相互污染。

#### 6. 原理与流程图

```text
基础配置 + 激活profile的配置 + 更高优先级来源
                 → Environment中的最终属性 → 配置绑定
```

例如普通运行时命令行`--lab.label=cli`可覆盖配置文件值。完整优先级包含测试属性等更多来源，应查所用Boot版本官方表，本章不凭简单图宣称穷尽所有顺序。

#### 7. 类比边界与错误直觉

Profile不等于安全边界，也不应把生产密钥写进application-prod.yml提交。测试目录的文件不是生产JAR配置文件；当前test-profile证据说明测试运行时行为，不代表打包后内置了该环境。

#### 8. 企业应用场景与选型

Profile适合成组启用开发工具、测试替身和环境相关配置。业务特性开关可用独立属性，避免几十种Profile组合。发布时应明确激活参数，并记录实际脱敏配置来源以便排障。

#### 9. 常见错误与排查

先确认当前激活Profile，再检查CLI、环境变量、测试属性是否覆盖文件。修改testresources后只启动生产JAR不会生效，需说明运行classpath区别。不要为查看一个label开放全部env端点；它可能包含敏感信息。

#### 10. 实操练习与验收

WebApiTest中/lab-info必须返回test-profile。手动启动生产JAR加`--lab.label=cli`后应看到cli；这是可追加的独立练习，不把未执行命令记录为通过。解释为什么Nest测试同名字符串不是Boot Profile兼容性证明。

### ch23-kp03 · 初始化、关闭与Actuator

#### 1. 一句话定义

生命周期钩子在容器创建、初始化和关闭等阶段执行资源管理，Actuator则提供可配置的运行状态端点，二者相关但不互相替代。

#### 2. 为什么需要它

连接池、后台任务和客户端需要可靠初始化与释放。一个健康端点返回UP不保证关闭时资源被释放；反之，有destroy钩子也不保证进程被强杀时一定执行。

#### 3. 生活类比

开店准备、宣布营业和关店清理是不同阶段，门口“营业中”牌子不会替员工关煤气。突然停电可能根本来不及按关店流程执行，必须设计额外的恢复与一致性策略。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring本例使用@Bean的initMethod="start"、destroyMethod="stop"以及ApplicationReadyEvent，Nest使用OnModuleInit、OnApplicationBootstrap、OnModuleDestroy。Spring还支持PostConstruct/PreDestroy注解，但本例未采用。它们可比较“初始化/就绪/清理”的目的，但不存在所有Bean之间一一对应的全局顺序。Nest enableShutdownHooks处理支持的信号，显式app.close在测试中更可控；Windows进程信号行为也不能照搬Linux假设。

#### 5. 两端代码对照

Java BootModelTest实际启动非WebBoot上下文，观察construct/init/ready，try-with-resources关闭后再断言destroy。Nest api.test创建真实HTTP应用，持有LifecycleProbe，app.close后断言construct/init/ready/destroy。Java配置仅公开Actuator health/info，HTTP测试核对/actuator/health为UP、/actuator/env为404；Nest自定义/health只是最小JSON，不是Actuator复刻。

#### 6. 原理与流程图

```text
构造 → 依赖注入/初始化 → 应用启动完成 → 服务阶段
                                 → 正常关闭 → 销毁钩子
                                 → 强杀/崩溃 → 不保证销毁钩子
```

这里的ready相对本探针init更晚，不意味着不同对象的钩子都服从同一顺序。关闭服务器和释放外部连接也需要具体组件协议，本例尚未接数据库。

#### 7. 类比边界与错误直觉

健康UP是已配置指标的聚合结果，不代表所有业务路径可用。公开Actuator不应等同于公开所有管理端点。本例只证明env未公开，不能据此宣称完整运维安全审计已完成。

#### 8. 企业应用场景与选型

初始化适合验证必要资源，关闭适合停止接受任务、等待在途处理、释放连接。可靠业务一致性不能仅依赖PreDestroy保存关键数据；应使用持久化与恢复机制。管理端点应配合网络隔离、授权和脱敏，后续安全/诊断章节进一步实现。

#### 9. 常见错误与排查

测试挂住时检查是否遗漏app.close或Context.close。未出现destroy时先看对象作用域及是否正常关闭；第18章已演示request scope并不自动拥有应用销毁钩子。探针返回UP但业务失败时检查是否根本没有为该依赖配置健康指标。

#### 10. 实操练习与验收

两端生命周期断言都必须在close之后检查destroy。真实HTTP核对health与env暴露差异。讨论强杀无法保证清理时应如何设计业务恢复；不使用“加一个destroy日志”作为恢复方案验收。

## 章末验收与审查记录

本机新增Java模块14项、Nest全量27项与扩展打包共同HTTP契约通过；两轴增量独立审查及Windows/Linux×Temurin21/25修订CI通过。当前in_review，待用户验收，详见[审查归档](../reviews/P3-01.md)与[实施证据](../progress/P3-01.md)。本章不据此宣称SQL、生产认证或读者效果完成。

官方参考：[Boot外部配置](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)、[Profiles](https://docs.spring.io/spring-boot/3.5/reference/features/profiles.html)、[Actuator端点](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)、[Nest生命周期](https://docs.nestjs.com/fundamentals/lifecycle-events)、[Nest配置](https://docs.nestjs.com/techniques/configuration)。
