# 运行示例工作区

要求Node22.20.0、JDK21或25。Java与TS分别由Maven和pnpm管理依赖。数据库服务另由Docker Compose管理，控制台示例不依赖数据库。

## 安装、构建与测试

在仓库根目录执行，Windows将`./mvnw`换成`.\mvnw.cmd`：

```sh
npx --yes pnpm@10.34.9 install --frozen-lockfile
./mvnw --batch-mode --no-transfer-progress clean verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
npx --yes pnpm@10.34.9 examples:verify
```

最后一步实际启动普通Java JAR、Spring JAR和Nest CLI，检查各自9项共同契约。预期输出`通过：3个独立运行物 × 9项共同契约。`。没有构建产物时直接报错，不使用假结果替代。

## 分别运行

```sh
java -jar examples/java-basics/greeting-cli/target/greeting-cli-0.1.0-SNAPSHOT.jar NestJS
java -jar examples/spring-boot-examples/target/spring-boot-examples-0.1.0-SNAPSHOT.jar NestJS
npx --yes pnpm@10.34.9 examples:start NestJS
```

均输出`Hello, NestJS!`。输入`1reader`应失败并提示规则；JS与Java异常类型可以不同。工作区黑盒检查同时验证非零退出和错误原因，避免把意外崩溃计为正确拒绝。

## 模块职责

| 路径 | 职责 |
|---|---|
| 根pom.xml | 聚合教学示例，继承兼容基线与插件版本 |
| examples/java-basics/greeting-core | 不依赖Spring的共享规则 |
| examples/java-basics/greeting-cli | 普通Java调用方，可执行JAR |
| examples/spring-boot-examples | Spring容器装配，可执行JAR |
| examples/nestjs-reference | pnpm叶工作区，Nest独立上下文与测试 |
| examples/compatibility-probe | 独立数据库/Web兼容验证，不进入无Docker的根Reactor |
| project/deploy/compose | 本地MySQL/Redis开发服务 |

根Reactor不是毕业项目，`project/`的业务模块由P6实现。没有让pnpm管理JAR，也没有引入Nx或微服务拆分。

## 本地数据库与缓存

要求Docker服务端可用，使用Linux containers模式。查看配置并启动：

```sh
docker compose -f project/deploy/compose/compose.yaml config --quiet
docker compose -f project/deploy/compose/compose.yaml up -d --wait --wait-timeout 180
docker compose -f project/deploy/compose/compose.yaml ps
```

默认项目名`java-for-nestjs-dev`，MySQL监听本机13306，Redis监听本机16379；只绑定环回地址。用户名、数据库和本地测试口令见`project/deploy/compose/.env.example`。需要修改时复制为同目录`.env`，不提交实际环境文件。默认口令仅供本地开发，不作为生产凭据。

MySQL健康检查执行真实SQL，Redis执行PING。使用完毕运行：

```sh
docker compose -f project/deploy/compose/compose.yaml down
```

该命令保留命名卷中的数据。不要在有需保留数据时随意删除卷。

## Wrapper与排查

Maven Wrapper3.3.4的only-script模式固定Maven3.9.11并校验SHA-256，不提交Wrapper JAR。来源为[Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)，保留Apache许可头。

Windows脚本包含两处兼容修正：普通目录的Target可能为空，先判空；无私有仓库凭据时优先使用Windows curl下载，避免某些代理拒绝WebClient请求。认证下载仍保留原实现，校验逻辑不变。升级Wrapper时重新核验这两项。

- 找不到Java：配置当前终端的JAVA_HOME或PATH，确认是JDK；不需要全局Maven。
- PowerShell传带点的Maven属性时给整个参数加引号，如`'-Dmaven=3.9.11'`。
- 下载失败：保留错误并检查网络；不能把已编译部分当成完整verify成功。
- Docker未运行：先启动Docker Desktop，再确认`docker version`包含服务端。本机无法启动时，只记录配置检查，不宣称本地容器已通过。
- 原生Java HTTP Client环回限制仍见[兼容矩阵](compatibility-matrix.md)；本控制台示例不替代相关Web验证。
