# 可靠消息同题实验

Java21/25、Boot3.5.16管理的AMQP client5.25.0、Flyway11.20.3、MySQL8.4.7、RabbitMQ4.2.2-management。Node22.20.0对照为[独立workspace](../nestjs-message-reference/package.json)，amqplib0.10.9、类型0.10.8、mariadb3.5.4。直接使用协议SDK，不声称已经使用RabbitTemplate、Nest RMQ transporter或BullMQ。

仅在一次性独占环境运行。测试会清空`message_lab.processed_events/projection_effects`；不能与另一套实验并行共用数据库。URL保护只限定环回地址及专用名称，不证明该库没有重要数据。不要把生产数据库转发到该地址。每例代理拓扑使用随机`teaching.UUID`；正常关闭删除自己的队列和交换机，进程被强杀时须销毁整个一次性代理容器。

## 准备服务

仓库根目录启动两个独占容器，凭据仅为可丢弃夹具，端口仅绑定本机：

```powershell
docker run --detach --name teaching-message-mysql --publish 127.0.0.1:3306:3306 --env MYSQL_DATABASE=message_lab --env MYSQL_USER=lab --env MYSQL_PASSWORD=lab-only-password --env MYSQL_ROOT_PASSWORD=root-lab-only-password mysql:8.4.7
docker run --detach --name teaching-message-rabbit --publish 127.0.0.1:5672:5672 --env RABBITMQ_DEFAULT_USER=lab --env RABBITMQ_DEFAULT_PASS=lab-only-password --env RABBITMQ_DEFAULT_VHOST=message_lab rabbitmq:4.2.2-management
docker exec teaching-message-mysql mysqladmin ping -h127.0.0.1 -proot-lab-only-password
docker exec teaching-message-rabbit rabbitmq-diagnostics -q ping
```

等待两个检查均返回成功后设置显式环境：

```powershell
$env:MESSAGE_JDBC_URL='jdbc:mysql://127.0.0.1:3306/message_lab?allowPublicKeyRetrieval=true&useSSL=false'
$env:MESSAGE_DATABASE_USER='lab'
$env:MESSAGE_DATABASE_PASSWORD='lab-only-password'
$env:MESSAGE_DATABASE_URL='mysql://lab:lab-only-password@127.0.0.1:3306/message_lab'
$env:MESSAGE_AMQP_URL='amqp://lab:lab-only-password@127.0.0.1:5672/message_lab?heartbeat=5'
npx --yes pnpm@10.34.9 install --frozen-lockfile
npx --yes pnpm@10.34.9 messaging:build
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/messaging-lab/pom.xml verify
npx --yes pnpm@10.34.9 messaging:test
```

Java先执行真实Flyway迁移，Node复用同一表结构，不手写另一份建表脚本。任一命令非零应停止并排错。Linux将`$env:NAME='value'`改为`export NAME='value'`，Wrapper用`./mvnw`；完整实际CI见[messaging.yml](../../.github/workflows/messaging.yml)。本机只有编译/无服务合同测试，真实服务证据单独记入[P5-01](../../docs/progress/P5-01.md)。无服务自检可以运行Java `'-Dtest=MessageContractTest' verify`及Node `node --test --test-name-pattern='restricted envelope' examples/nestjs-message-reference/dist/message.test.js`，不能把它们当代理测试。

## 验收

Java共7项（2合同+5真实服务）；Node共7项（1合同+6真实服务，含连接清理注入）。预期每项无失败/无跳过，日志`MESSAGE_RESULT`可见APPLIED、COMMITTED_UNACKED、DUPLICATE、RETRY和DEAD。断言同时核对真实SQL效果、账本行及DLQ原生`x-death.reason=rejected`，不只检查打印文本。

消息1KiB内、严格UTF-8、恰好三个字段`eventId/kind/taskKey`；幂等键来自正文eventId，SDK messageId每次发布重新生成。重复eventId但不同taskKey永久拒绝。重试attempt0/1/2，至多三次处理**每条重试链**；发送成功但原消息ACK前崩溃会派生重复链，不能据此承诺全局最多三次投递。数据库提交后ACK前关channel为真实代理重投，不把重发副本的redelivered=false当异常。

采用classic队列的基础教学配置，durable/persistent/confirm不能证明物理断电持久性、quorum高可用或DLX绝不丢失。默认classic死信转发没有目标confirm的端到端保证；本例只证明目标可用时真实路由。立即重试不等于退避。无业务HTTP入口、后台消费者常驻服务、生产负载和Outbox，本轮使用basicGet控制单次实验；第39章后续覆盖Outbox窗口。

完成后只销毁自己创建的两个容器：

```powershell
docker rm --force teaching-message-mysql teaching-message-rabbit
```

连接失败先查容器健康、端口占用、专用库/vhost及显式URL，不放宽保护。确认超时表示结果未知，应按可能已接收处理；禁止改成无条件ACK。测试不是生产启动入口。
