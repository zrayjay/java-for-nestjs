# Node可靠消息参照

运行前置、环境、Java Flyway迁移顺序、清理与预期结果统一见[实验README](../messaging-lab/README.md)。本workspace直接使用amqplib，不使用Nest RMQ transporter，包名表示课程的Node参照位置，不表示已有Nest业务服务。

`npx --yes pnpm@10.34.9 messaging:build`编译；`messaging:test`需要真实MySQL/RabbitMQ。不能通过跳过服务测试伪造成功。完整实现为[src/message.ts](src/message.ts)，行为断言为[src/message.test.ts](src/message.test.ts)。
