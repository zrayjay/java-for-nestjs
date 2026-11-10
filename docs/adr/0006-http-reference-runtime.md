# ADR-0006：P3双端HTTP参照基线

2026-11-11，实施采用。G2技术准入已通过，按原P3范围推进启动/REST/校验/请求链/代理/配置，再接数据库。不改变现有问候入口，也不将新的HTTP样例当生产权限系统。

Java新增独立根Reactor模块spring-web-lab，继续Boot3.5.16/BOM；使用web、validation、security、actuator及test Starter，OpenAPI继续兼容矩阵已验证的springdoc2.10.0。普通JAR保留供后续集成测试消费，Boot可执行JAR另附exec classifier。默认使用内存仓库，数据访问阶段再接独立受控MySQL实验，不能用内存测试替代真实SQL。

Nest参照固定@nestjs/platform-express11.2.9，与现有core/common11.2.9同线；class-validator0.15.1、class-transformer0.5.1；Swagger选11.4.8。实施前npm元数据确认Swagger最新12.1.1要求Nest12，而11.4.8 peer为Nest^11.0.1，因此不按latest升级。测试用Node22内置fetch调用真实环回HTTP，不为此增加Supertest；Java另用MockMvc核对框架链路及随机端口HTTP核对外部行为。

共同资源契约为tasks：CRUD、嵌套priority、非法输入与错误码；示例title限制ASCII以保持语言/长度规则明确，不代表生产国际化方案。校验分组用独立draft/publish练习验证，不暗示两端分组继承完全同构。配置与生命周期、Spring自动装配、JDK/CGLIB代理分别提供隔离实验。

所有HTTP样例仅作路由/校验/链路教学，绑定环回地址；P3尚不提供JWT/RBAC生产权限保证，P4再实现安全矩阵。公开的文档站与教程源码授权不等于自动部署本机API服务。精确依赖进入锁文件/兼容矩阵，最终构建、HTTP/SQL与CI结果分别记录，不预写通过。
