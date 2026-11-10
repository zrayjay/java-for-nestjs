# 第18章IoC/DI实验

仓库根执行，完整JDK21/25、Boot3.5.16、Nest11.2.9及Node22.20.0；无需Docker/HTTP/数据库。Windows将Wrapper换为`.\mvnw.cmd`。

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/spring-boot-examples -am verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
```

Spring新增5项测试，使用真实AnnotationConfigApplicationContext，验证构造注入/不同容器单例、缺依赖失败、prototype/固定注入/ObjectProvider、prototype不自动销毁，以及init/postprocessor/JDK代理/self invocation/destroy事件。显式配置位于dev.javafornestjs.ioc，与既有Greeting应用扫描包分离。

Nest新增5项测试，使用真实TestingModule，验证同题Hello @ 09:00、未export令牌失败、TRANSIENT/REQUEST/ContextId、request对象不参与应用销毁Hook、单例init/close Hook。测试通过才算这些行为的证据，不把不同框架的同名作用域认作等价。

预期缺失依赖的警告是被assertThrows/assert.rejects捕获的负例；正常verify/test必须退出0。不得删除负例或默默将其当成跳过。容器入口/测试可以查Bean，业务Greeter只声明构造依赖。

prototype标记对象只持有内存状态，不模拟真实连接泄漏。Spring生命周期日志精确对应本例显式initMethod及处理器，不保证所有@PostConstruct、代理或后处理器顺序。Nest使用显式ContextId的小容器，不冒充真实HTTP请求生命周期。

四组合基础CI自动执行相同Java/TS测试。正文及迁移边界见[第18章](../../docs/04-spring-boot/18-dependency-injection.md)。
