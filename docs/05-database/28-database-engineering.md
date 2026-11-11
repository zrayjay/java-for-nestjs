---
title: '28 · 数据库工程化'
description: 以Flyway单一迁移、真实索引计划和可追溯SQL排障建立工程边界。
chapter_id: '28'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / Flyway11.20.3 / MySQL8.4.7 / Prisma7.10.1
---

# 28 · 数据库工程化

## 章节范围与先修

先完成24、27章。源码包括[Flyway配置](../../examples/data-access-lab/src/main/java/dev/javafornestjs/data/DataConfiguration.java)、[V1建表](../../examples/data-access-lab/src/main/resources/db/data-migration/V1__tasks.sql)、[V2索引](../../examples/data-access-lab/src/main/resources/db/data-migration/V2__task_title_index.sql)、[真实SQL测试](../../examples/data-access-lab/src/test/java/dev/javafornestjs/data/DataAccessTest.java)及[CI](../../.github/workflows/data-access.yml)。本章不提供已验证的生产零停机DDL、数据库备份恢复或容量承诺。

## 知识点

### ch28-kp01 · 迁移版本、校验和与单一DDL所有者

#### 1. 一句话定义

数据库迁移把Schema演变保存为有序、可追溯的变更，Flyway通过历史表记录已应用版本与校验信息，已应用脚本应保持不可变。

#### 2. 为什么需要它

开发机手工建列而CI或部署库没有，是代码能编译却无法运行的常见原因。若Hibernate update、Prisma Migrate与Flyway同时改表，又难以判断哪个工具拥有最终结构及失败恢复责任。

#### 3. 生活类比

建筑改造需要按编号保存施工记录，已完成的图纸不能偷偷擦掉再声称从来如此。新需求应追加下一份变更。两家施工队同时改同一堵墙，即使各自工具可靠，也可能互相冲突。

#### 4. Node.js / TypeScript / NestJS 技术对照

Prisma Migrate也管理Schema演变，但不等于Prisma generate。generate只生成客户端，不创建课程数据库表。本例Flyway独占data_lab DDL，JPA validate、Prisma生成模型；不同时执行Prisma Migrate或db push。Flyway历史与Prisma迁移记录也不能互相替换。

#### 5. 两端代码对照

V1创建owners/tasks及检查/外键，V2创建title/id索引。Java迁移器使用专用data_schema_history，cleanDisabled=true。测试检查applied2、validate成功、第二次migrate执行0项。Node运行在Java迁移之后，Prisma模型映射同名表，客户端生成并不作为建库步骤。

#### 6. 原理与流程图

```text
专用库URL验证 → Flyway读取历史 → 校验已执行脚本 → 应用未执行版本
  → 应用SQL测试 → Prisma同题SQL测试
```

新checksum负例把V1拷贝到测试临时目录并改列长度，仅调用validate要求失败，随后原迁移仍可validate。它不修改仓库原脚本，也不repair历史表掩盖错误。

#### 7. 类比边界与错误直觉

MySQL很多DDL存在隐式提交，不能因Flyway有事务概念就承诺任意失败自动回滚所有DDL。cleanDisabled防止当前迁移器clean，不阻止拥有权限的其他SQL删除数据。校验和正确也不证明库没有被外部手工改动，必要时还需结构核查。

#### 8. 企业应用场景与选型

迁移在受控发布步骤执行，应用账号与迁移账号权限可分离。生产变更应评估锁、回填和兼容窗口；扩大列、删列和建大索引不能只按小夹具结果决定。失败后先查实际结构和历史，再制定修复，不先运行repair“让它变绿”。

#### 9. 常见错误与排查

checksum mismatch检查是否修改过已执行脚本；版本冲突检查团队分支合并；表已存在检查是否有第二DDL所有者。不要为“测试方便”把clean放入正常启动，也不要用错库URL进行破坏性清理。本例URL守卫先于池和迁移。

#### 10. 实操练习与验收

空专用库迁移两版本成功，再执行为0项，原validate成功；新临时脚本篡改测试必须失败且原历史不变。新增V3而非改V1是后续练习，不能先将未执行生产迁移标为完成。checksum负例的实际结果以最新CI记录为准。

### ch28-kp02 · 索引与EXPLAIN的证据边界

#### 1. 一句话定义

索引提供额外访问路径，EXPLAIN展示优化器为查询选择或考虑的计划；存在索引不等于每次使用，更不保证整体业务更快。

#### 2. 为什么需要它

“加了索引”是结构变化，“查询变快”是需要测量的结果。小数据量、不同过滤条件、排序和统计信息都可能改变计划；把一个possible_keys字段写成必定走索引会误导优化判断。

#### 3. 生活类比

书籍索引可以帮助定位主题，但只有两页纸时直接翻看可能更快。目录上列出可用索引不等于读者最终使用它。多加索引也要维护，每次改书都增加更新成本。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java与Prisma最终访问相同MySQL，索引机制不因客户端语言改变。Prisma schema的index声明不代表运行generate就建好索引；本例真实索引由Flyway V2创建。MyBatis Wrapper或Prisma where都不能替代数据库执行计划分析。

#### 5. 两端代码对照

真实测试先插Alpha，再EXPLAIN按title查找，检查possible_keys含ix_data_tasks_title，SHOW INDEX核对title/id两列。它没有断言当前一定选择该key，更没有用一次小表计划证明大表最优。Node过滤/分页测试使用相同表结构；未单独将驱动差异伪装成索引差异。

#### 6. 原理与流程图

```text
查询谓词/排序 + 可用索引 + 统计信息 → 优化器估算 → 计划
计划 + 实际数据/缓存/锁/并发 → 实际执行表现
```

possible_keys是候选，key是选中索引；rows通常是估计，不能当实际扫描精确行数。EXPLAIN ANALYZE会实际执行相应查询并展示运行信息，使用时也需评估影响，本实验尚未把它作为已执行证据。

#### 7. 类比边界与错误直觉

联合索引顺序有意义，不能看到title/id就认定任意id条件都受益。索引会增加写入维护与存储成本。SELECT返回少不代表扫描少，函数、隐式转换、排序与偏移可能增加工作量。

#### 8. 企业应用场景与选型

针对真实查询、数据分布和负载设计索引，保留变更前后SQL、参数、计划及测量条件。线上添加索引需评估DDL锁和回滚/清理计划。教程夹具只验证机制，不为生产索引方案背书。

#### 9. 常见错误与排查

possible_keys为空时查列、类型、函数和索引左前缀；选ALL时检查表大小与选择性，不能立刻强制索引。两次测试差异巨大时记录缓存、数据量和并发，而非只保留最快一次结果。

#### 10. 实操练习与验收

能解释possible_keys与key差别，找到V2实际索引及顺序，运行已有真实EXPLAIN测试。另行扩展到代表性大数据量需要重新记录环境和计划，不沿用本章通过结果宣称性能提升百分比。

### ch28-kp03 · 慢SQL排查与Schema交付顺序

#### 1. 一句话定义

慢SQL排查需要把请求、查询次数、连接等待、SQL执行与锁等证据关联起来，并把Schema变更纳入可复现的发布流程。

#### 2. 为什么需要它

HTTP慢可能根本不是单条SQL慢，而是N+1、池等待或锁冲突。只盯着一条查询改索引可能无效；部署先启动新代码后补列，还会在流量进入时产生结构不匹配错误。

#### 3. 生活类比

就餐等待可能发生在排队、传单、烹饪或送餐，厨房最快不代表总等待最短。改厨房布局还要协调营业时间，不能让菜单已经承诺新菜而设备尚未安装。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java Statement.prepare计数和Prisma query事件都提供局部证据，但含义和覆盖范围不同。服务端慢日志与应用事件也不同：前者依MySQL阈值/配置记录服务器观察，后者可能含客户端等待。本例未启用生产慢日志，不能把查询计数当慢日志截图。

#### 5. 两端代码对照

Java N+1真实4→1，Node批量4→2；池耗尽测试表明即使没发出第三条SQL，也可能在借连接阶段失败。CI先安装复用HTTP产物，执行Java迁移及SQL验证，再执行Node/Prisma真实SQL。JPA与生成jOOQ同样建立在已迁移Schema上。

#### 6. 原理与流程图

```text
请求慢 → 先定位：入口排队 / 池等待 / SQL次数 / 单条SQL / 锁与网络
发布   → 核对Schema版本 → 受控迁移 → 验证 → 启动/切流 → 观察
```

教程CI验证空实验库和固定迁移版本，不模拟滚动发布的旧新应用共存。生产expand/contract需要额外兼容测试，不能以此图代替实际演练。

#### 7. 类比边界与错误直觉

日志可能含敏感参数，排障必须脱敏和控制访问。重试不一定安全，写入要考虑幂等与事务终局。Migration成功也不意味着客户端生成类型已经同步，必须同时验证生成和编译。

#### 8. 企业应用场景与选型

为关键请求保留关联ID、查询预算、池指标、慢SQL与计划入口。选择适当保留期和采样，避免全量参数日志造成数据泄漏或负担。数据库备份/恢复及告警演练在交付阶段另做，本章不提前声称完成。

#### 9. 常见错误与排查

先看池是否耗尽，再看SQL次数和单条耗时；锁等待不能仅靠加索引假定消失。Schema错误检查应用版本、迁移历史及实际库名，特别防止本地连对库、CI连另一库。不要通过关闭validate和放宽URL守卫掩盖环境差异。

#### 10. 实操练习与验收

针对“列表慢”分别列出池耗尽、N+1和单条计划三种证据路径，指出已有测试覆盖哪一类。按CI顺序复现迁移→Java→Node，缺库应失败。报告中记录尚未验证的慢日志/滚动发布/恢复演练，而非填“均通过”。

## 章末验收与审查记录

迁移幂等、真实索引候选、双端SQL流程及checksum负例已过Linux21/25，见[记录](../progress/P3-02.md)。本章draft，独立审查待完成。官方参考：[Flyway验证](https://documentation.red-gate.com/flyway/reference/commands/validate)、[MySQL EXPLAIN](https://dev.mysql.com/doc/refman/8.4/en/explain.html)、[慢查询日志](https://dev.mysql.com/doc/refman/8.4/en/slow-query-log.html)、[Prisma Migrate概览](https://www.prisma.io/docs/orm/prisma-migrate)。
