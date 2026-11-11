---
title: '24 · JDBC与连接池'
description: 区分驱动、DataSource、物理连接和逻辑借用，真实验证耗尽与释放。
chapter_id: '24'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / MySQL8.4.7 / Prisma7.10.1
---

# 24 · JDBC与连接池

## 章节范围与先修

先掌握14章依赖和18章容器。本章使用独立data_lab，完整[运行说明](../../examples/data-access-lab/README.md)、[DataConfiguration](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/DataConfiguration.java)、[数据库测试](../../examples/data-access-lab/src/test/java/dev/javafornestjs/data/DataAccessTest.java)及[Prisma客户端](../../examples/prisma-reference/src/data-client.ts)。CI使用真实MySQL，不采用H2，不在缺库时跳过。当前本机只完成编译/URL守卫，SQL证据见[实施记录](../progress/P3-02.md)。

## 知识点

### ch24-kp01 · JDBC Driver与DataSource

#### 1. 一句话定义

JDBC定义Java数据库访问接口，Driver实现具体数据库协议，DataSource提供连接取得入口；连接池是DataSource的一种实现方式。

#### 2. 为什么需要它

把“装了MySQL依赖”“配置了数据源”和“成功执行SQL”混为一谈，会在驱动、URL、账号、网络或迁移错误时乱改业务代码。分清每层责任能缩小故障范围。

#### 3. 生活类比

JDBC像统一的叫车约定，驱动像懂当地路线的承运商，DataSource像叫车入口，连接是实际用于一次对话的车辆。入口存在并不保证有车可用，也不表示目的地允许你进入。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma Client提供模型查询API，不能直接等同于JDBC Driver。Prisma7本例经PrismaMariaDb适配器调用mariadb JS驱动；Java经Connector/J和DataSource。两端都需要连接参数，但SQL生成、错误类型、事务对象与池参数不同，不能复制同名配置值就认为行为相同。

#### 5. 两端代码对照

Java DataConfiguration显式创建HikariDataSource，再注入Flyway和MyBatis。Nest数据参照调用createDataClient，它先验证dataConnection再创建PrismaMariaDb。原有transaction_lab另有独立守卫，未被放宽。

```text
Java: Controller → Service → MyBatis → JDBC/DataSource → Connector/J → MySQL
Node: Controller → Service → Prisma → MariaDb Adapter → mariadb驱动 → MySQL
```

Java [URL守卫](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/DatabaseUrl.java)只接受环回data_lab和明确白名单参数。Node只从已验证URL构造固定连接字段，不把任意URL参数透传给驱动。

#### 6. 原理与流程图

```text
加载驱动依赖 → 验证连接配置 → 创建DataSource/Adapter
  → 请求连接 → 身份/网络/数据库协商 → 创建Statement → 执行SQL
```

DataSource可惰性初始化或提前连接，具体取决于实现和配置。本例Hikari构造可能触发初始化，因此URL守卫必须在构造之前，不能等第一次删除测试数据时才检查库名。

#### 7. 类比边界与错误直觉

驱动版本不必与Server主版本号相同；课程使用BOM管理的Connector/J连接固定MySQL8.4.7。JDBC不是数据库迁移工具，也不保证SQL跨方言完全可移植。URL能解析不等于目标是允许写入的实验库。

#### 8. 企业应用场景与选型

应用通常注入DataSource而非到处DriverManager.getConnection，便于统一池和事务。公开教程的实验密码只适用于临时CI库，生产应使用独立凭据与权限。实验守卫限制环回不是生产连接方案，部署设计应另行处理网络与TLS。

#### 9. 常见错误与排查

No suitable driver先查依赖和URL协议；拒绝连接查服务/端口；Access denied查账号授权；Unknown database查库初始化。不要开启任意Connector/J参数来“试到能连”；dbname等覆盖选项会改变实际目标，本例直接拒绝未审核参数。

#### 10. 实操练习与验收

运行DatabaseUrlTest，要求生产库、远程地址、dbname覆盖和无效端口在连接前失败。然后在专用MySQL按README执行完整verify。能通过URL守卫不等于SQL测试已跑；需要真实HTTP写入后从MySQL读回的独立断言。

### ch24-kp02 · Hikari连接池与背压

#### 1. 一句话定义

连接池复用有限的物理连接，借用方在上限内取得逻辑连接，容量不足时等待至借用超时，而不是无限创建连接。

#### 2. 为什么需要它

数据库连接有成本且数量有限。每个HTTP请求随意开新连接可能把数据库拖垮；无限等池又会让请求堆积。上限、等待与业务超时必须一起设计。

#### 3. 生活类比

停车场只有两个车位，第三辆车只能等待或离开。把停车场入口变宽不能增加车位。池大小是并发资源上限，connectionTimeout是等车位的时间，不是已经开进场后的所有活动时限。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java本例Hikari maximumPoolSize=2、minimumIdle=0、connectionTimeout=500ms。Prisma7的mariadb适配器设置connectionLimit=2。两个2只表示各自池上限，并不证明相同调度、公平性或超时单位。Node事件循环不阻塞在同步Java借用调用上，但数据库连接仍有限。

#### 5. 两端代码对照

Java DataAccessTest真实借出两个连接，第三次getConnection应抛SQLTransientConnectionException，然后归还前两个再借用执行SELECT1。Node配置固定connectionLimit2，当前没有把Java500ms耗尽测试照抄成已验证的Node超时机制；Node SQL测试关注真实Prisma查询和关闭。

#### 6. 原理与流程图

```text
借用请求 → 有空闲连接：交付逻辑句柄
         → 无空闲且可扩容：创建连接
         → 已达上限：等待 → 有归还则借出 / 超时则失败
```

池等待超时、JDBC网络超时、SQL执行超时、事务超时和HTTP请求超时是不同计时器；本实验只对第一种注入故障，不宣称其他时限已设置。

#### 7. 类比边界与错误直觉

max2是故障演示参数，不是生产推荐值。增大池不一定提高吞吐，可能增加数据库争用。短HTTP请求也可能被长事务占用连接阻塞；CPU线程数不能直接换算为数据库池大小。

#### 8. 企业应用场景与选型

根据数据库承载、实例数量、事务持有时间和负载测量配置池。多个应用实例的总连接数需要合计；单实例每池设置100，不代表集群只开100。必要时限制并发入口，避免把所有背压留给数据库。

#### 9. 常见错误与排查

池超时先查看active/idle/waiting、连接持有时长、慢SQL和未关闭资源。不要先把connectionTimeout改到几分钟掩盖泄漏。池中存在空闲连接也不保证后续SQL快，锁等待和执行计划需要另查。

#### 10. 实操练习与验收

运行耗尽测试，要求借出2条时active=2，第三借用按配置失败，释放后active=0并可再执行SELECT1。测试不对某个机器的毫秒耗时下性能结论，也不使用sleep猜测数据库并发状态。

### ch24-kp03 · 逻辑连接归还与资源所有权

#### 1. 一句话定义

池化连接close通常归还逻辑句柄，关闭DataSource才结束所拥有池的生命周期；Statement和ResultSet也应在正确作用域释放。

#### 2. 为什么需要它

不关闭借用句柄会耗尽池，过早关闭共享DataSource则让其他请求失败。资源管理应按所有权设计，而不是在每个方法finally里关掉所有能找到的对象。

#### 3. 生活类比

用完共享汽车要归还钥匙，而不是把整个租车公司关门。停车票、钥匙与公司营业执照是不同层次的资源，释放顺序和责任人也不同。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java try-with-resources按作用域关闭ResultSet、Statement和Connection；Spring事务内的连接通常由事务基础设施管理，不能擅自关闭底层共享资源。Prisma查询不暴露相同JDBC句柄，应用拥有的Client在关闭时disconnect；Nest PrismaTasks实现onModuleDestroy释放自己持有的Client。

#### 5. 两端代码对照

DataAccessTest嵌套try-with-resources运行SELECT1；DataConfiguration的Bean配置destroyMethod="close"。额外的独立池关闭测试正常关闭拥有池，再断言isClosed且不能继续借用。Node数据测试finally关闭app并disconnect，客户端初始化失败路径也应执行最终释放，不让错误测试悬挂。

#### 6. 原理与流程图

```text
借连接 → 创建Statement → 得到ResultSet
  → 关闭ResultSet → 关闭Statement → Connection.close归还池
应用结束 → 所有者关闭DataSource / PrismaClient
```

连接归还时池会按实现恢复必要状态；不要依赖未明确约定的会话变量残留。事务提交/回滚与close的关系见第27章。

#### 7. 类比边界与错误直觉

逻辑句柄isClosed不等于数据库所有物理连接都消失。关闭客户端不应作为可靠业务提交手段。进程强杀未必执行销毁钩子，关键数据必须通过事务持久化，不能等PreDestroy补救。

#### 8. 企业应用场景与选型

应用级共享DataSource由容器管理，方法借用资源由方法管理。长时间导出、游标和流式读取应明确持有连接的时间，避免边下载边长期占池。为每个请求新建PrismaClient或Hikari池通常会破坏共享预算。

#### 9. 常见错误与排查

测试进程不退出检查未关闭Client或后台资源；Connection is closed检查句柄是否越出作用域。用完事务对象后把它存全局属于生命周期错误，不应靠捕获异常重试旧句柄解决。

#### 10. 实操练习与验收

区分“借用归还”与“拥有池关闭”两个测试：前者之后还能借用，后者之后必须失败。Node真实SQL测试结束应正常退出。对外报告必须注明独立池关闭负例是哪次CI执行，不把仅编译写成真实连接释放通过。

## 章末验收与审查记录

Java11项（含2项URL守卫）及Node3项已通过Linux21/25，含新增池关闭负例，见[证据](../progress/P3-02.md)。本章draft，独立审查待完成。官方参考：[JDBC基础](https://docs.oracle.com/javase/tutorial/jdbc/basics/)、[HikariCP参数](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)、[Prisma连接管理](https://www.prisma.io/docs/orm/prisma-client/setup-and-configuration/databases-connections/connection-management)。
