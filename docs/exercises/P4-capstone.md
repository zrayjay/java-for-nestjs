# P4综合练习：拒绝越权、解释失败、连接证据

适用29~37章。使用一次性教学库/Redis/对象服务，真实读者结果留P7-03，不填写虚构分数。先按各实验README完成依赖与环境准备，禁止对业务库运行清理夹具。

## 操作与固定预期

1. Alice合法Bearer访问任务成功，匿名401、Cara写403、Alice读Bob404；Erin同部门能读Alice却不能改删，Alice本人能改删，Admin按全局权限能跨部门。给每个拒绝配合法对照。
2. 固定Clock到Access边界401；仍有效Refresh可轮换；重放旧Refresh后整族失效。凭据版本提升后两旧凭据都拒绝，新登录可用。解释为何不能在事务内抛错回滚撤销。
3. 在受控加载/通知夹具中，同键并发未命中只在本实例合并一次加载；通知失效期间慢读允许返回旧快照，但不得把旧值填回新代缓存。明确这里没有实际任务DB事务集成，并解释多实例/Redis失效失败/外部SQL写入边界。
4. 上传合法受限PNG并由同owner取回；错误MIME/名字/签名/尺寸/重复头/交错/解压膨胀拒绝，跨owner下载拒绝，匿名对象URL403；tEXt标记清除且像素保留。扫描未配置必须显示not_configured。
5. 无服务单测用SDK替身控制审计与补偿双失败，核同一对象键和两因；再用真实服务测试验证正常写读。说明替身和真实协议分别证明什么。
6. 用MockMvc检查实际绑定及无效请求不写入，再运行两个打包进程HTTP契约。Testcontainers实际启动MySQL/Redis，4项无跳过；说明类共享容器与每例数据隔离的区别。
7. 从自己启动的人工进程采集JFR、GC、heap与thread dump。核真实CPU样本、类直方图、HPROF、BLOCKED及锁地址；不得据一个快照宣布泄漏或把人工热点解释为另一个HTTP实验的瓶颈。
8. 携W3C父上下文创建任务：响应traceId继承，SQL span的parent为SERVER span，requestId与SQL审计行一致；匿名即使有合法traceparent仍401。核最终JSON、500与非负耗时，以及凭据/正文/私有路径/异常标记缺席。

## 复现入口

| 实验 | 运行说明/脚本 | 验收证据 |
|---|---|---|
| 安全/授权/观测 | [Java安全](../../examples/security-lab/README.md)、[Node安全](../../examples/nestjs-security-reference/README.md) | [授权矩阵](../progress/P4-03.md) |
| Redis | [缓存实验](../../examples/cache-lab/README.md) | [缓存审查](../reviews/P4-01-cache.md) |
| 文件/扫描/存储 | [文件实验](../../examples/file-lab/README.md) | [上传矩阵](../progress/P4-04.md) |
| 单测/集成 | [34章](../07-testing-jvm/34-unit-testing.md)、[35章](../07-testing-jvm/35-integration-and-contracts.md) | [P4-02](../progress/P4-02.md) |
| 诊断 | [人工夹具](../../examples/jvm-diagnostics/README.md) | [诊断审查](../reviews/P4-02-diagnostics.md) |

## 迁移解释与通过标准

至少独立回答三项：Spring安全链与Nest前置/Guard的边界；ThreadLocal/Context与AsyncLocalStorage的异步传播；Refresh提交后拒绝；MockMvc与真实TCP；Node/JVM采样和monitor不能直接等价；Micrometer Timer与Node有限摘要的单位/能力差异。

操作结果及机制解释分别评估，失败应保留输入、预期、实际及修复回归，附件不得含原始令牌或业务堆转储。没有真实运行、没有合法对照或错误声称生产能力时不能判通过。当前只有作者执行和独立技术审查，不代表真实读者已完成练习。
