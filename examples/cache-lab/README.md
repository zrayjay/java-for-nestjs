# Redis缓存实验（实施中）

独立Java模块使用Boot管理的Spring Data Redis/Lettuce；Node同题为安全workspace中的cache.ts/cache.spec.ts，ioredis固定5.8.2。当前是缓存原语及真实Redis实验，尚未接入任务HTTP入口，授权回调测试不是实际数据库撤权证明。

使用可丢弃的环回Redis7.4.6，从根目录运行：

```powershell
$env:CACHE_REDIS_URL='redis://127.0.0.1:6379'
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/cache-lab/pom.xml verify
pnpm security:build
node --test examples/nestjs-security-reference/dist/cache.spec.js
```

测试只删除随机命名空间，不执行FLUSHDB。两端各5项：命中/空值/作用域/实际过期、命中时授权拒绝、本机并发合并及失败重试、受控旧查询回填竞态、客户端断开故障显式传播。CI配置已写，尚待实际结果。

每次read先调用当前授权回调；scope必须由可信服务端根据数据库规则生成，不能直接使用客户端输入。相同scope/id仅适用于共享相同可见字段与数据范围的请求，所有者字段、脱敏策略不同必须分键。scopedLoad仍需SQL作用域条件，缓存不能弥补遗漏授权。

空值编码N，正值V前缀；正TTL5秒、空TTL1秒，额外抖动0~20%。空值缓存缓解重复相同不存在键的穿透，不解决随机键攻击；单进程singleflight缓解本实例热键击穿，不是分布式锁；TTL错峰缓解同时到期，不承诺消除雪崩。

业务集成必须在成功数据库提交后对所有受影响作用域调用invalidateAfterCommit。Lua原子递增epoch并删value；回填只允许原epoch，避免写后失效被早先查询覆盖。已开始的读仍可返回旧快照。数据库提交与Redis失效不是跨系统原子事务：进程崩溃、Redis中断、外部SQL写入仍可能保留旧值至TTL；当前故障显式传播，没有重试队列或强一致承诺。epoch不设TTL，避免旧读跨到期重用epoch；仅适合有界教学键，生产需规划回收和持久性边界。两个语言使用不同namespace，不能声称已实现跨语言共享缓存失效。
