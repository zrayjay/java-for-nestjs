---
title: '25 · MyBatis / MyBatis-Plus'
description: 用真实Mapper SQL、参数化Wrapper、分页与查询计数理解数据映射边界。
chapter_id: '25'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / MyBatis-Plus3.5.18 / Prisma7.10.1
---

# 25 · MyBatis / MyBatis-Plus

## 章节范围与先修

先完成24章连接生命周期。完整[Mapper](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/TaskMapper.java)、[XML SQL](../../examples/data-access-lab/src/main/resources/mapper/data/TaskMapper.xml)、[Repository适配器](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/MyBatisTasks.java)、[Java测试](../../examples/data-access-lab/src/test/java/dev/javafornestjs/data/DataAccessTest.java)与[Prisma同题测试](../../examples/nestjs-data-reference/src/tasks.test.ts)为证据。运行命令见[SQL实验README](../../examples/data-access-lab/README.md)。

## 知识点

### ch25-kp01 · Mapper、映射与XML SQL

#### 1. 一句话定义

MyBatis把Mapper方法与SQL、参数绑定和结果映射连接起来，MyBatis-Plus在其上提供常用CRUD与其他扩展，但业务仍要理解实际SQL。

#### 2. 为什么需要它

Java接口没有方法实现却能查数据库，原因是框架注册了Mapper代理及SQL映射。若误把接口当普通类new出来，或者只检查Java编译不检查XML，运行时才会发现找不到statement或字段映射错误。

#### 3. 生活类比

Mapper像按编号接单的查询窗口，接口方法是单据名称，XML是对应执行说明，结果映射是把查询回执填回对象。编号相同但说明书放错目录，窗口仍不知道该执行什么。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma Client从schema生成模型查询API；MyBatis Mapper可由接口和XML表达自定义SQL。两者都能参数化，但MyBatis不会自动提供JPA式持久化上下文和脏检查。MyBatis-Plus BaseMapper的通用CRUD也不等于Prisma全部关系查询与迁移能力。

#### 5. 两端代码对照

TaskMapper扩展BaseMapper并声明listLimited/ownerName/joinedOwners，XML namespace与完整接口名一致，statement id匹配方法。listLimited使用`LIMIT #{limit}`，不是字符串拼接。PrismaTasks使用dataTask.findMany和orderBy/take；二者都由现有Controller→Service调用，Repository适配器把表行转成稳定HTTP资源体。

#### 6. 原理与流程图

```text
Service → Repository适配器 → Mapper代理 → statement定位
  → 参数绑定 → JDBC执行 → 行映射为TaskRow → 转TaskItem/HTTP视图
```

MyBatisRow的ownerId用于数据库映射，HTTP不直接公开它。数据库实体与请求DTO分离，避免客户端越权写入内部字段。

#### 7. 类比边界与错误直觉

Mapper代理不是“拥有数据库实体状态的对象”，返回的普通TaskRow被修改后不会自动写回。XML SQL也不会因用了XML就安全，安全关键在参数绑定和动态SQL规则。表名、排序列等标识符不能随意使用用户文本拼接。

#### 8. 企业应用场景与选型

团队熟悉SQL、复杂报表或希望明确控制查询时可选择MyBatis；简单CRUD可用Plus减少重复。不要为了复用把跨业务SQL都放进公共core。测试应验证映射、约束及事务，不以Mock Mapper返回列表证明数据库行为。

#### 9. 常见错误与排查

Invalid bound statement检查XML路径、namespace、id与资源打包。字段为空检查别名与下划线映射。记录映射需要核对构造参数顺序或名称，本例OwnedTask有真实Join结果断言，不只验证SQL没有异常。

#### 10. 实操练习与验收

执行真实HTTP创建任务，从JDBC直接读title，再HTTP替换/删除并确认数据库实际变化。移走XML会导致运行失败而非编译失败，说明为什么Maven compile不足以验收Mapper。故障练习后恢复资源再verify。

### ch25-kp02 · Wrapper与分页不是SQL理解的替代品

#### 1. 一句话定义

Wrapper构造查询条件，分页插件为相应查询生成分页及计数SQL，参数安全、排序和总数语义仍需要明确。

#### 2. 为什么需要它

链式API容易让开发者忽视真正发送了哪些SQL。忘装分页拦截器可能查出全部数据；没有稳定排序可能让页间重复或遗漏；把原始SQL片段接进Wrapper也会重新引入注入风险。

#### 3. 生活类比

Wrapper像结构化点菜单，分页像按页取号。菜单上只能选择允许项目，不代表可以在备注中塞入任意厨房指令。每页取号前要有明确排序，号码队列变化时也不能保证跨页快照不变。

#### 4. Node.js / TypeScript / NestJS 技术对照

LambdaQueryWrapper引用Java getter减少列名字符串错误；Prisma where由生成模型提供字段类型。Plus分页需要同版jsqlparser依赖和PaginationInnerInterceptor；Prisma skip/take直接表示偏移分页，但total仍需另外查询。两端API形状不同，不把Plus Page对象直接当公共HTTP契约。

#### 5. 两端代码对照

Java测试用LambdaQueryWrapper.eq(title,攻击字符串)要求空结果，再对3行数据执行Page(2,2)并按id升序，断言total3、第二页仅Gamma。Node同样测试精确攻击字符串过滤和skip2/take2。SQL绑定使字符串成为值，不成为OR条件。

#### 6. 原理与流程图

```text
结构化条件 → 生成SQL占位符 + 绑定值
分页请求 → count查询（按配置）+ 带limit/offset的数据查询
```

count和数据查询可能不是同一快照；本实验静态夹具不证明并发写入时total与数据永远一致。大偏移性能也未因测试3行而得到证明。

#### 7. 类比边界与错误直觉

Lambda字段引用不是任意SQL类型安全证明；apply/last等原始SQL扩展仍可能危险。页码2与skip2仅在pageSize2时对应。公开HTTP列表当前只提供limit截断，底层分页实验不代表API已新增total/page字段。

#### 8. 企业应用场景与选型

后台小规模列表可用偏移分页；深分页、持续更新数据或严格遍历应评估游标与索引。对外排序字段用允许列表映射到已知列。输入限制既要控制页大小，也要结合查询成本，不能只看返回行数。

#### 9. 常见错误与排查

分页无效先查拦截器和独立解析器依赖。total异常查Join、group与count优化策略，不要先手工改总数。SQL注入负例成功不代表所有动态表名/列名都安全，应定位每个原始SQL入口。

#### 10. 实操练习与验收

3行数据第二页仅Gamma、total为3、攻击字符串结果为空且表仍3行。说明这些断言分别验证分页语义与参数绑定。不要把它们当做全表百万行性能验收。

### ch25-kp03 · 用实际SQL计数识别N+1

#### 1. 一句话定义

N+1是先查N个主对象，再逐个补查关联数据的访问模式，可以通过Join或批量读取减少往返，但方案语义和成本必须核对。

#### 2. 为什么需要它

循环中的单次查询在代码上很小，HTTP响应字段也完全正确，却可能产生大量数据库往返。只看最终JSON测试无法发现这个问题；需要观测实际查询数量。

#### 3. 生活类比

先拿到三张订单，再分别跑三趟查询负责人，合计四趟；一次带负责人信息的查询可能只要一趟，也可以先拿订单再批量查三位负责人共两趟。少跑趟不代表携带的数据越多越好。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java MyBatis不自动决定关系加载，逐行ownerName和XML Join都由程序明确表达。Prisma可做关系查询或批量API，本例显式两批读取，不依赖未经验证的relationLoadStrategy。Nest async/await不会自动消除数据库N+1；顺序await恰能稳定演示多次往返。

#### 5. 两端代码对照

Java [SqlCounter](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/SqlCounter.java)在StatementHandler.prepare计数，3个不同owner的列表加逐条补查为4，joinedOwners为1，并比较相同负责人顺序。Prisma query事件只统计SELECT：逐条4次，findMany主记录再findMany owners为2，最终名字都应Alice/Bob/Cara。

#### 6. 原理与流程图

```text
N+1: tasks一次 → owner1 → owner2 → owner3 = 4
Join: tasks JOIN owners一次              = 1
批量: tasks一次 → owners WHERE id IN...一次 = 2
```

本例故意使用不同owner，避免一级缓存或批处理碰巧降低重复读取而掩盖模式。Java计数是实际prepare，不是凭方法调用次数猜测。

#### 7. 类比边界与错误直觉

Join可能放大一对多结果行，分页和去重需要额外设计。批量IN也有参数数量和内存限制，不能无限收集ID。查询次数少不必然更快，还需检查传输量、索引、锁和执行计划。

#### 8. 企业应用场景与选型

列表页关联展示通常应避免逐行补查。按关系基数和需要字段选择Join、分批查询或预计算。对关键接口建立查询预算，但测试需要稳定夹具和明确统计范围，不能把所有后台连接命令也算业务SELECT。

#### 9. 常见错误与排查

计数不稳定先查缓存、并发测试、懒加载与日志过滤范围。Prisma findUnique可能有批处理优化，本例使用顺序await和不同ID固定实验。不能仅在日志里搜“SELECT”次数便推断一条业务请求，必须隔离对应时间段和请求。

#### 10. 实操练习与验收

Java真实计数4→1，Prisma真实SELECT4→2，两个优化版本必须保留相同负责人结果。能解释为什么没有把Prisma两次查询写成“一次Join”，并能指出这些小夹具不证明生产延迟提升比例。

## 章末验收与审查记录

上述Mapper、分页、N+1双端SQL已在Linux21/25的MySQL8.4.7通过，见[记录](../progress/P3-02.md)。本章draft，独立审查待完成。官方参考：[MyBatis Mapper XML](https://mybatis.org/mybatis-3/sqlmap-xml.html)、[MyBatis-Plus分页](https://baomidou.com/en/plugins/pagination/)、[Wrapper](https://baomidou.com/en/guides/wrapper/)、[Prisma查询优化](https://www.prisma.io/docs/orm/prisma-client/queries/query-optimization-performance)。
