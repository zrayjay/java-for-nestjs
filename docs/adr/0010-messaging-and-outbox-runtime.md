# ADR-0010：P5可靠消息与Outbox运行物

2026-11-16，G4技术准入后采用。保留模块化单体主线，不为展示消息过早拆成生产微服务。新增独立messaging-lab及Node同题workspace，真实MySQL/RabbitMQ CI，不进入无服务根Reactor。

教学代理选择RabbitMQ4.2.2-management，官方rabbitmq-server的v4.2.2标签commit为3844659bba39e96026745429a7511f75836a7349。Java使用Boot3.5.16 BOM管理的AMQP client5.25.0；Node amqplib0.10.9及@types0.10.8，不未经迁移验证跟进当前2.4主版本。协议层直接SDK便于展示channel/confirm/ack边界，不冒称已使用Spring RabbitTemplate或Nest RMQ transporter。MySQL8.4.7、Flyway11.20.3及Node mariadb3.5.4沿用已验证基线。本机Docker registry查询超时，不算Broker运行证据；首次服务CI需实际验证镜像与协议。

P5-01采用持久化消息、durable队列/交换机、publisher confirm及mandatory return；确认只代表代理接收，不保证路由或消费完成。消费者在同一MySQL事务写消费账本和业务计数，commit后ack；唯一consumer/event键去重，重复ID但不同taskKey拒绝而非悄悄改业务。提交后/ack前关闭channel实测重投与幂等，不宣称全局恰好一次。

重试最多3次处理，失败事务回滚后以增加attempt头的副本重新发布并等confirm，再ack原消息；中间崩溃可重复，去重仍必需。超过上限或永久非法消息basicReject(requeue=false)，由真实DLX路由至DLQ，检查x-death。当前是显式有界立即重试，不假装退避/延迟插件、quorum高可用、代理磁盘断电或毒消息自动修复均已实现。报文只含受限字符串eventId/kind/taskKey，1KiB上限，不引入未经验证的浮点金额合同。

P5-02随后增加MySQL事务Outbox，业务提交与待发布行同事务，发送/confirm后再标记；故障窗口允许重复，由消费者去重。租约、超时和熔断的作用域须明确，不把本地超时称取消代理操作，也不以租约证明跨系统原子性。尚未实施的部分单独交付及验证。

所有数据库/代理入口只允许专用环回教学库message_lab及临时命名空间，夹具只清自己的资源。真实服务、失败/恢复日志及双端行为分别记录，独立审查和适用CI后进入in_review；G5另验。
