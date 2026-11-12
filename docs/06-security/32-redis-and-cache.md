---
title: '32 · Redis 与缓存设计'
description: 在真实Redis中验证TTL、空值缓存、本机并发合并和写后失效竞态。
chapter_id: '32'
teaching_status: draft
baseline: Java21 / Boot3.5.16 / Redis7.4.6 / ioredis5.8.2
---

# 32 · Redis 与缓存设计

## 章节范围与先修

先完成23/27/31章。独立[Java缓存原语](../../examples/cache-lab/src/main/java/dev/javafornestjs/cache/ScopedCache.java)、[Node同题](../../examples/nestjs-security-reference/src/cache.ts)与[运行说明](../../examples/cache-lab/README.md)使用真实Redis。当前尚未接入任务HTTP入口：授权回调用于演示边界，不能当成实际数据库撤权实验。先准备可丢弃的环回Redis及Node构建，再在仓库根目录运行本章命令。

## 知识点

### ch32-kp01 · TTL、空值缓存与键的范围

#### 1. 一句话定义

缓存把可重建结果保存有限时间，TTL约束存活期，空值缓存避免反复查询同一个不存在的键；键必须区分数据可见范围。

#### 2. 为什么需要它

热数据重复查询会消耗数据库连接，反复不存在的查询同样可能耗尽连接。只缓存正值不能缓解重复空查询，忽略部门或脱敏策略的键还可能把一个用户可见内容给另一个用户。

#### 3. 生活类比

临时货架的标签写着部门和货号，保鲜期限到就撤架；“暂时无货”也贴短期标签。货架标签对应scope/id，保鲜期对应TTL，无货标签对应负缓存。现实标签可能被人错贴，类比不能证明程序已正确授权。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring Data Redis的StringRedisTemplate与ioredis都是客户端，TTL由Redis执行。本例使用显式客户端与编码，没有接入Spring Cache注解或Nest cache-manager，不把同题实验描述成这些封装的完整教程。

#### 5. 两端代码对照

Java使用`Duration`配置，Node使用安全整数毫秒。两端都把null/empty编码为`N`，正字符串编码为`V`前缀，避免空字符串与不存在混淆。核心操作为：

```java
long ttl = value.isPresent() ? positiveMillis : negativeMillis;
long extra = jitter.applyAsLong(ttl);
```

```ts
const ttl = value === null ? this.negativeMs : this.positiveMs
const extra = this.jitter(ttl)
```

Lua用PSETEX写入实际TTL。测试正值5秒、负值1秒、额外20%，检查PTTL分别落在`(0,6000]`与`(0,1200]`。通过PEXPIRE触发真实服务器过期，限时轮询确认不存在，然后检查加载计数增加；没有把应用内假时钟当Redis到期。

#### 6. 原理与流程图

每次先执行授权回调，再按namespace/scope/id读取；命中解码返回，未命中进入加载协调。正负缓存都设TTL，不永久保存“无货”。scope必须由可信服务端产生；相同scope仅允许共享相同字段与数据范围，不能使用请求参数直接决定授权。

#### 7. 类比边界与错误直觉

空值缓存缓解相同不存在键的穿透，不解决不断变换随机键的攻击；仍需入口校验、限流和容量约束。TTL是最大存活期，不等于数据库提交后立刻同步。授权不能缓存成长期“允许”。

#### 8. 企业应用场景与选型

适用于昂贵且可短暂陈旧的查询。用户权限、实时额度等强约束不应直接采用本模型。键还应考虑租户、语言与字段脱敏策略；本例只演示部门范围字符串，不代表已经实现租户隔离。

#### 9. 常见错误与排查

查PTTL为-1说明没有设置过期，-2表示键不存在。命中率高但数据泄漏应先查键与授权，不能靠缩短TTL掩盖。无货查询持续打库时检查是否真的缓存N，以及客户端是否改变了scope/id。

#### 10. 实操练习与验收

基础题：解释N、V与不存在三种状态。迁移题：将Java Duration换为Node毫秒并保留边界。思考题：随机不存在id为什么能绕过负缓存收益？按README准备后执行：

```powershell
$env:CACHE_REDIS_URL='redis://127.0.0.1:6379'
.\mvnw.cmd -f examples/cache-lab/pom.xml '-Dtest=RedisCacheTest#positiveAndNullHitsHaveBoundedTtlAndScopeIsolation' test
node --test --test-name-pattern='positive and null' examples/nestjs-security-reference/dist/cache.spec.js
```

两端检查加载次数、不同scope隔离、实际PTTL及真实过期后重载；不要只看Redis中曾经出现一个键。

### ch32-kp02 · 热键击穿、并发合并与错峰

#### 1. 一句话定义

singleflight让同一实例内同一代热键的并发未命中共享一个加载结果；TTL抖动让不同键的到期时间不完全一致。

#### 2. 为什么需要它

热键过期时几十个请求同时查库，是击穿；大量键同一时刻过期可能引发雪崩。两者都不能仅用更长TTL彻底解决，尤其数据库故障时不能无限积压请求。

#### 3. 生活类比

一个窗口缺货时只派一位工作人员去仓库，其余人在本窗口等待结果；各货架采用略有差异的保鲜期。工作人员对应leader，等候人对应共享Future/Promise，到期错峰对应TTL抖动。另一家店仍可能同时派人，这正是单进程边界。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java用ConcurrentHashMap与CompletableFuture协调虚拟线程，Node用Map与Promise协调异步调用。Node单线程并不意味着没有逻辑并发，await Redis或数据库时其他请求仍会进入。两端都不是Redis分布式锁。

#### 5. 两端代码对照

```java
var existing = flights.putIfAbsent(flightKey, mine);
if (existing != null) { return existing.join(); }
```

```ts
const existing = this.flights.get(key)
if (existing) return existing
```

leader在真正加载前再查一次缓存，避免先前未命中者在旧leader结束后重复加载。成功与失败都清理本机flight；失败不写负缓存，下一次可重试。测试20个并发读共享一次加载，另测数据库加载异常后恢复。

#### 6. 原理与流程图

授权→读epoch/value→未命中→争当本机leader→二次读→加载→条件回填→完成Future/Promise→finally清理。flight键包含epoch，写后新一代请求不能加入旧加载。所有调用者进入flight前都执行授权，不让等待者借用leader身份。

#### 7. 类比边界与错误直觉

多个实例各有自己的Map，会各查一次库。抖动只是降低同时到期概率，不证明消除雪崩。加载器无界耗时也会让等待者堆积；当前原语没有完整请求超时、熔断或容量控制，不应照搬为生产中间件。

#### 8. 企业应用场景与选型

单实例热点查询可先用简单合并，扩展后根据成本选择预热、限流或跨实例协调。跨实例锁还需要租约、超时、故障恢复与锁所有权，不能用一个SET NX就宣布实现一致性。随机抖动生产可注入`ThreadLocalRandom`/`Math.random`，当前测试固定20%以检查边界，不测试概率分布。

#### 9. 常见错误与排查

加载异常后永远失败检查flight是否清理；偶发重复加载检查leader二次读取与键一致性。权限拒绝不应转换成共享“无货”，否则会污染其他用户。并发统计要计加载器调用次数，不能只计HTTP响应数。

#### 10. 实操练习与验收

基础题：区分穿透、击穿、雪崩。迁移题：解释Future.join与await Promise的线程行为差异。思考题：部署两个进程后预期最多有几次同代加载？

```powershell
.\mvnw.cmd -f examples/cache-lab/pom.xml '-Dtest=RedisCacheTest#concurrentMissesShareOneLoadAndErrorsCanRetry' test
node --test --test-name-pattern='concurrent local misses' examples/nestjs-security-reference/dist/cache.spec.js
```

确认20个结果一致且loads=1，失败后下一次恢复；这是本实例证据，不是分布式协调证明。

### ch32-kp03 · 写后失效、旧回填与一致性边界

#### 1. 一句话定义

数据库提交后失效缓存，并以epoch阻止先前查询把旧结果重新填回；数据库与Redis仍是两个不能由本例原子提交的系统。

#### 2. 为什么需要它

读请求先取旧数据库快照，写请求提交并删缓存，旧读随后回填，会把刚删除的旧值复活。只讲“写后DEL”遗漏了真实竞态；先删再提交还可能使新读再次装入旧数据库值。

#### 3. 生活类比

货架更新时换一张批次牌，搬运员出发时记住批次，回来发现牌已换就不能上架。批次牌对应epoch，搬运对应慢查询，上架前核对对应Lua比较。牌只约束上架，不能让已经拿到旧货的人自动退货。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java `redis.execute`与ioredis `eval`执行相同Lua：失效脚本原子INCR epoch与DEL value，回填脚本比较epoch后PSETEX。`{scope:id}`让同一对象两键使用同一Redis Cluster hash tag；CI只验证单节点，没有验证Cluster部署。

#### 5. 两端代码对照

两端回填共用的Lua语义：

```lua
local current = redis.call('GET', KEYS[1]) or '0'
if current ~= ARGV[1] then return 0 end
redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[3])
return 1
```

Java `invalidateAfterCommit(scope,id)`与Node同名方法只提供调用点，不自动参加数据库事务；当前实验用受控门闩模拟提交后的通知，尚未接入实际任务事务。真实集成须在成功提交后调用并覆盖全部受影响scope，不能在事务内部冒称已经提交。

#### 6. 原理与流程图

读A记录epoch0并等待→写提交→失效变epoch1→读B加载new并写入→放行读A→A回填因epoch不符被拒→后续仍读new。A本身仍返回old，这是已开始读取的快照边界。授权回调每次执行；命中后撤权演练抛拒绝且不调用加载器，回调不是数据库授权实现。

#### 7. 类比边界与错误直觉

提交后进程崩溃、Redis不可用、外部SQL修改和遗漏scope均可能造成陈旧至TTL；epoch方案不提供跨系统强一致。epoch不设TTL避免代号重用，却会保留键，只适合有界教学键。Redis数据丢失/恢复及驱逐epoch也会破坏代号假设，生产须单独设计持久性与回收。

#### 8. 企业应用场景与选型

短期陈旧可接受时用TTL兜底，关键状态应查权威数据库。可靠失效可结合事务Outbox及幂等消费，但本章尚未实现。Java/Node使用独立namespace，仅证明同题行为，未形成两种服务共用缓存协议。

#### 9. 常见错误与排查

写失败却缓存变化说明通知过早；写成功仍旧先查失效是否执行、scope是否完整、是否有慢读回填。Redis错误当前显式向调用者传播，不暗中绕过缓存查库，也没有宣称完整HTTP503策略。不要在连接异常时返回上次缓存值并称其授权有效。

#### 10. 实操练习与验收

基础题：画出旧回填的时间线。迁移题：分别指出Spring提交回调与Prisma事务返回后适合通知的位置。思考题：DB提交后、失效前进程退出如何恢复？

```powershell
.\mvnw.cmd -f examples/cache-lab/pom.xml verify
node --test examples/nestjs-security-reference/dist/cache.spec.js
```

各5项全过，重点检查old在途读允许返回、后续new不被覆盖、命中授权拒绝及断开客户端故障。说明测试没覆盖实际DB写后集成、跨实例、Cluster与Redis服务宕机恢复。

## 章末验收与审查记录

本章draft。[Redis43181272467](https://github.com/zrayjay/java-for-nestjs/actions/runs/43181272467)在Linux Temurin21/25每组Java5项、Node5项真实Redis通过。独立审查待完成，未声称任务HTTP集成或G4通过。官方参考：[Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/)、[Redis EXPIRE](https://redis.io/docs/latest/commands/expire/)、[Redis Lua原子执行](https://redis.io/docs/latest/develop/programmability/eval-intro/)、[ioredis](https://github.com/redis/ioredis)。
