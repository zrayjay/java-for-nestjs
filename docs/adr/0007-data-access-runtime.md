# ADR-0007：P3真实数据访问实验

2026-11-11，实施采用。保持根Reactor的内存HTTP快速验证，另建examples/data-access-lab，以普通JAR依赖spring-web-lab复用Controller/Service/Repository契约；真实MyBatis适配器接MySQL8.4.7。独立SQL实验不进入需要无数据库运行的根Reactor，但由专用CI强制执行，不允许缺库跳过或用H2替代。

数据库仅允许环回地址、明确data_lab库及精确白名单连接参数，先检查URL再创建池；迁移、清理和故障测试仅使用该专用库。不会放宽既有transaction_lab连接守卫。Flyway11.20.3是唯一DDL所有者，JPA使用validate，Prisma只生成客户端，不并行Migrate。

主路径固定MyBatis-Plus3.5.18（Boot3 Starter），分页插件的jsqlparser与其同版。Hikari/JDBC、JPA/Hibernate与jOOQ从Boot3.5.16管理；实施前Maven属性解析确认Hibernate6.6.53.Final、jOOQ3.19.35，不独立升级。Mapper/XML/Wrapper/分页/N+1、池耗尽释放、Flyway版本校验与执行计划分开验证。数据库约束及真实SQL结果是证据，不以模拟Mapper返回值替代。

JPA和jOOQ是隔离比较，不把它们一起加入生产业务主路径。jOOQ若演示生成Schema类型，必须实际从迁移后MySQL生成并编译，不能把动态DSL字符串称为完整Schema类型安全。Nest/Prisma对应同名tasks输入/输出和持久化行为；既有事务参照保留独立入口，避免新实验污染其夹具。

本机Docker限制未解决，先编译与URL负例，真实SQL在Linux Temurin21/25的MySQL服务CI执行并记录链接。所有性能例子说明数据量、查询计数与计划，不声称生产吞吐或用一次EXPLAIN证明所有场景优化。
