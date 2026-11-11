---
title: '26 · JPA / Hibernate / jOOQ'
description: 真实验证托管实体、脏检查与生成Schema查询类型，比较而不混用持久化模型。
chapter_id: '26'
teaching_status: in_review
baseline: Java21 / Spring Boot3.5.16 / Hibernate6.6.53.Final / jOOQ3.19.35 / Prisma7.10.1
---

# 26 · JPA / Hibernate / jOOQ

## 章节范围与先修

先完成24章JDBC；25章是业务主路径，本章只做隔离比较。完整[实体](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/jpa/TaskEntity.java)、[JPA测试](../../examples/data-access-lab/src/test/java/dev/javafornestjs/data/DataAccessTest.java)、[Schema生成测试](../../examples/data-access-lab/src/test/java/dev/javafornestjs/data/GeneratedSchemaTest.java)与[Prisma参照](../../examples/nestjs-data-reference/src/tasks.ts)均使用同一专用MySQL表。Flyway拥有DDL，Hibernate仅validate，不执行create/update。依赖版本由Boot管理，不独立升级。

## 知识点

### ch26-kp01 · JPA规范、Hibernate实现与持久化上下文

#### 1. 一句话定义

JPA定义实体持久化API，Hibernate是本实验使用的实现，EntityManager的持久化上下文管理实体身份和状态，它不是普通SQL结果列表。

#### 2. 为什么需要它

同一行在一个上下文里多次find可能返回同一对象，实体修改也可能被自动检测。把JPA当MyBatis换一套注解，会误解对象共享、生命周期和数据库写入时机。

#### 3. 生活类比

一个档案管理员在本轮工作里为同一档案维护同一份工作副本，避免不同人员拿着相互冲突的版本。结束本轮工作后，副本不再由这位管理员跟踪。这个“本轮”对应持久化上下文，不是整个JVM。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma返回JS数据对象，不提供相同的JPA身份映射/自动脏检查约定。MyBatis TaskRow也是查询结果，修改字段不自动持久化。Nest Provider单例是容器作用域，与EntityManager持久化上下文不同；不应把EntityManager当全局共享状态对象服务所有并发请求。

#### 5. 两端代码对照

Java测试显式建立LocalContainerEntityManagerFactoryBean，扫描TaskEntity并设置hbm2ddl.auto=validate。persist提交后，同一manager.find(id)断言与原task是同一实例。PrismaTasks每次显式调用findUnique/updateMany；本例不声称两个查询结果有Java身份映射语义。

#### 6. 原理与流程图

```text
数据库主键 → 当前上下文查找身份
         → 已托管：返回该实例
         → 未托管：加载行并登记实体
```

上下文身份不代表数据永远最新。另一个事务更新后，已托管对象未必自动刷新；需要明确刷新/清理与隔离级别，不能靠再次find猜测已重新发SQL。

#### 7. 类比边界与错误直觉

Entity不是请求DTO，也不应任意暴露懒加载关联。一级上下文不是跨进程缓存；二级缓存另有配置。本实验没验证关系懒加载或二级缓存，不用单个实体实验推广到全部ORM行为。

#### 8. 企业应用场景与选型

以对象状态变化为主、团队理解实体边界的业务可考虑JPA。SQL报表与复杂投影可能更适合显式SQL工具。不要在同一写入流程同时混入多个持久化模型而不处理缓存和事务一致性，本章故意隔离测试。

#### 9. 常见错误与排查

实体扫描失败检查包范围和Entity标注。Schema validation失败应对照数据库列类型及迁移，不要立刻切成ddl-auto=update。直接查询没读到最新外部变化时检查当前上下文是否已有实体及事务隔离。

#### 10. 实操练习与验收

运行JPA真实数据库用例，要求persist获得主键且find返回相同对象。解释这个同一性为什么只在当前上下文成立。把Hibernate validate关掉不能作为Schema不匹配的修复方案，必须先明确迁移与映射的责任。

### ch26-kp02 · 脏检查、detach与merge

#### 1. 一句话定义

托管实体的变化可在flush时同步为SQL，detach后对象不再受跟踪，merge把分离状态复制到托管实例并返回该实例。

#### 2. 为什么需要它

一个setter可能最终引发UPDATE，而另一个对象的相同setter却没有任何SQL。若不理解实体状态，容易把“函数执行了”误认为“数据库已写入”，或在merge后继续操作错误对象。

#### 3. 生活类比

管理员会记录桌面工作副本的修改；拿出办公室的副本不再自动同步。重新提交时，管理员把内容复制进受管档案，并给你受管版本。merge不是把手中的纸瞬间变成同一份档案。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma与MyBatis要求显式更新调用，修改普通对象title不会自动UPDATE。JPA setter本身也不必立即发SQL，flush和事务提交才是重要边界。Spring事务代理、EntityManager上下文和数据库事务分别承担不同职责，不能仅用“有Transactional”推断任何对象都托管。

#### 5. 两端代码对照

Java真实测试在事务内改托管实体title，提交后JdbcTemplate独立读回Dirty checked；detach后再改并提交，数据库仍旧值；merge返回对象与原对象不是同一实例，提交后数据库变成Detached change。Node PrismaTasks.replace显式await updateMany再读取结果，没有自动跟踪本地对象。

#### 6. 原理与流程图

```text
托管实体修改 → flush脏检查 → SQL → commit
detach → 普通对象修改 → 没有自动SQL
分离对象 → merge复制状态 → 返回托管副本 → flush/commit
```

flush不等于commit；后续回滚仍可撤销当前事务写入。外部独立连接在未提交前的可见性取决于隔离，测试在提交后再用JDBC读回。

#### 7. 类比边界与错误直觉

merge不是“任何旧对象都可以无条件覆盖最新状态”的安全策略。并发编辑通常需要Version乐观锁或其他冲突处理，本例没有Version字段，不宣称已经防止丢失更新。detach也不是删除数据库行。

#### 8. 企业应用场景与选型

明确聚合边界和事务工作单元，减少随意传递托管实体到异步任务或视图。批量处理需控制上下文大小，必要时flush/clear，但频率必须按测量决定。不能为消除内存占用随便清掉仍需要的托管状态。

#### 9. 常见错误与排查

修改不落库检查对象是否托管、事务是否提交、是否在只读或错误上下文。merge后继续改原对象时检查是否应使用返回值。看到UPDATE不代表已提交，先检查事务终局再查数据库独立读数。

#### 10. 实操练习与验收

四个断言必须成立：托管修改写回；分离修改不写回；merge返回不同实例且受管；合并提交后数据库更新。与Prisma显式update对照，说明为何不能只替换方法名来迁移ORM工作单元。

### ch26-kp03 · jOOQ生成Schema类型与SQL表达

#### 1. 一句话定义

jOOQ可从数据库Schema生成Java表/字段类型，再用这些类型构造SQL，把部分列名和类型错误前移到编译期，但不保证运行数据库永远与生成版本一致。

#### 2. 为什么需要它

字符串SQL中的列名和类型常到运行时才报错。生成模型能改善可发现性，但如果只调用DSL.field("title")等动态API，就不能宣称已经获得完整Schema级编译检查。

#### 3. 生活类比

按真实仓库货架清单生成带类型的领料表，领整数编号的栏位不能填字符串。清单过期后，表格仍可能编译通过却找不到实际货架，所以生成过程必须和迁移版本协调。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma也从Schema生成查询类型，但API与执行模型不同；jOOQ更贴近SQL表达。JPA关注实体状态，jOOQ不因此自动提供相同持久化上下文。Java泛型能检查Field类型，不能证明用户权限、业务规则或任何原始SQL片段安全。

#### 5. 两端代码对照

GeneratedSchemaTest先用真实迁移后的MySQL元数据生成Tables.DATA_TASKS，实际javac编译查询`ID.eq(id)`并执行，读取Typed query。随后编译`ID.eq("not-an-int")`要求失败。Prisma数据测试则先generate再用dataTask.findMany；二者都先生成，但没有混淆JDBC元数据与Prisma schema来源。

#### 6. 原理与流程图

```text
Flyway迁移MySQL → jOOQ读取元数据 → 生成Java Schema → javac编译查询 → JDBC执行
                                 → 类型错误查询 → 编译失败
```

本测试将源码与类文件输出到target，不提交机器生成的临时产物。它实际验证正确查询和错误查询，不通过只检查文件存在推断类型安全。

#### 7. 类比边界与错误直觉

生成类型不检查所有运行时值是否合法，不防止过期Schema、不替代事务或授权。原始SQLAPI可绕过部分类型约束。选择SQLDialect.MYSQL也不表示任何版本或商业版功能都可用，应核对当前jOOQ版本的数据库支持。

#### 8. 企业应用场景与选型

SQL复杂且希望保留组合表达与类型提示时可评估jOOQ。生成步骤应固定元数据源和迁移版本，避免开发者连各自不同的本地库生成相互冲突代码。JPA、MyBatis、jOOQ可共存但需要清晰边界，本课程主路径仍是MyBatis。

#### 9. 常见错误与排查

找不到生成列先查迁移/Schema过滤及生成来源。Java编译通过但运行列不存在时查生成版本和目标库是否一致。错误类型用例意外编译通过时检查是否已经退回Object/raw类型或动态字段，不能修改断言装作通过。

#### 10. 实操练习与验收

真实MySQL生成、正确查询编译执行、错误ID类型编译失败三项必须都有证据。仅运行generate不够。说明Prisma字段类型来自schema文件，而本实验jOOQ类型来自迁移后的数据库，两种来源都可能漂移，必须纳入CI。

## 章末验收与审查记录

真实MySQL8.4.7、Linux Temurin21/25每组11Java项（8数据、1Schema生成、2URL守卫）与3Node项通过，受控计划及精确故障诊断增补亦通过。两轴独立审查无剩余阻断，当前in_review，未自动用户验收，详见[实施证据](../progress/P3-02.md)和[审查归档](../reviews/P3-02.md)。本机不冒充SQL执行，生产安全/容量/读者效果仍不在当前通过范围。

官方参考：[Jakarta Persistence3.1](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1)、[Hibernate6.6指南](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html)、[jOOQ代码生成](https://www.jooq.org/doc/3.19/manual/code-generation/)、[Prisma Client生成](https://www.prisma.io/docs/orm/prisma-client/setup-and-configuration/generating-prisma-client)。
