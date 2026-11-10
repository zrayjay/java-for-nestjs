# 第06~12章语言实验

Java21语言级别、完整JDK21/25、Node22.20.0、TS5.9.3，全部无需Spring启动、网络服务或数据库。工作目录为仓库根：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/java-basics/language-lab -am verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
```

Linux改为`./mvnw`。TS生产/测试文件为`examples/nestjs-reference/src/language/`。Java业务/机制在LanguageLab、受控并发在ConcurrencyLab。第08章复用generics-lab，不复制擦除实验。

共同语义：有效Account id/roles快照；Payment描述card:1234与cash:20；过滤[1,10,20]后加倍求和60；按u1查找成功、missing不存在；两个独立任务按提交顺序收集结果[10,20]。不同语义单独断言：Java int溢出与JS number、Java值键与JS对象身份键、Stream惰性与Array eager、Optional与undefined、checked与JS Error、Java suppressed异常与普通finally覆盖错误、运行时注解与本项目legacy TS decorator。

Java11运行测试+6真实编译负例；负例必须命中预期诊断类别、Fixture.java与有效位置，Maven整体仍退出0。TS8新增运行测试及strictNullChecks编译负例，构建必须执行，不用跳过类型检查将负例变绿。

G2另补[无框架综合练习](../../../docs/exercises/P2-capstone.md)，组合输入不变量、ID去重、客户分组、受检查法与稳定排序；Java/TS各新增2测试，故本模块共19 Java、Nest参照共23 Node测试。这里不是生产订单/货币/幂等架构，拒绝重复ID和int32数量上限是明确的小模型契约。

虚拟线程测试实际执行Thread.isVirtual，并用闩锁确保两任务都进入后才放行，无sleep、无速度基准。结果10/20来自各自任务返回，按Future取得顺序收集；不声称完成顺序。平台线程池测试证明等待超时不会自动取消任务，最后释放闩锁、等待完成并关闭executor。另验证ExecutionException.getCause保留原异常。没有主动取消/打断、生产容量、pinning性能或外部IO测试，不能扩写为这些能力已验证。

Account的Java List.copyOf与TS Object.freeze/数组复制只对本例字符串元素提供所需不可变边界，不是任意嵌套对象的深冻结。注解本身不执行鉴权；测试读取标记不表示运行了授权框架。
