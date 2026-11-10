# Java运行与多模块最小示例

根Maven Reactor包含`greeting-core`、`greeting-cli`及使用同一核心库的Spring示例。核心库不依赖Spring；CLI用普通Java对象，Spring示例通过构造器注入装配。

## 编译和运行

要求JDK21或25。从仓库根目录执行：

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
java -jar examples/java-basics/greeting-cli/target/greeting-cli-0.1.0-SNAPSHOT.jar NestJS
```

Windows PowerShell将`./mvnw`换为`.\mvnw.cmd`。预期输出`Hello, NestJS!`。省略名字同样默认NestJS；输入`1reader`等非法值应非零退出并说明名字规则。

共享契约：名字必须为1~32个ASCII字母、数字、下划线或连字符，首字符为字母，不做隐式trim；空白、换行、非ASCII和超长输入均拒绝。规则只是本最小示例的业务约束，不代表通用姓名规则。

`greeting-core`的JUnit测试检查成功与边界输入；`greeting-cli`产出包含核心库的可执行JAR。根Reactor还构建[Spring容器版本](../spring-boot-examples/README.md)，可与[Nest版本](../nestjs-reference/README.md)对照。

Wrapper固定Maven3.9.11并校验发行包SHA-256。首次需要网络；已有Maven安装不是前提。完整工作区说明见[运行指南](../../docs/00-introduction/workspace.md)。
