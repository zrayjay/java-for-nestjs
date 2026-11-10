# Spring容器最小示例

这是P0-05的无Web控制台装配示例，不是已完成的IoC章节或毕业项目。它复用`greeting-core`，通过Bean配置和构造器注入提供`GreetingService`。

从仓库根目录运行：

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
java -jar examples/spring-boot-examples/target/spring-boot-examples-0.1.0-SNAPSHOT.jar NestJS
```

Windows使用`.\mvnw.cmd`。预期输出`Hello, NestJS!`；非法名字非零退出。契约与[普通Java版本](../java-basics/README.md)相同。JUnit使用真实Spring上下文验证依赖装配与输入拒绝，运行后关闭容器。

该程序不监听网络、不依赖数据库。Web、安全、事务等能力继续由[独立兼容探针](../compatibility-probe/README.md)验证，不能因本示例成功就宣称HTTP或数据库测试已通过。
