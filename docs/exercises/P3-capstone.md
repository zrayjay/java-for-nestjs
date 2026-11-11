# P3综合练习：从请求到SQL，再解释失败边界

范围17~28章，复用18/27章。前置：已完成P2，能运行固定Java/Node工具链，并有专用实验MySQL；本机Docker不可用时，可使用仓库真实MySQL CI证据核对技术结果，不能把没有执行的本机SQL写成通过。此练习不是已收集真实读者反馈的证明。

## 独立复现

1. 按[HTTP实验](../../examples/spring-web-lab/README.md)执行Java verify、Node构建测试和共同打包HTTP契约。必须看到两个进程各自通过，而不是只比较两端返回相等。
2. 按[数据实验](../../examples/data-access-lab/README.md)准备空data_lab、显式URL和实验账号，执行完整Java verify，再按[Nest数据实验](../../examples/nestjs-data-reference/README.md)执行data:build/data:test。Flyway先迁移，Prisma只生成客户端。
3. 按[事务实验](../../examples/transaction-lab/README.md)使用另一专用transaction_lab，运行真实Spring/Prisma事务测试，不把两个库混用。
4. 按[故障矩阵](../progress/P3-03.md)执行触发与修复。期望异常应被测试断言，回归命令必须成功。

若需要手动比较两个持久化API，可分别运行Java data-access-lab打包JAR（默认127.0.0.1:8085，自动激活data profile）与Node数据main（默认127.0.0.1:8088）。只使用实验数据；不要同时运行会清空同一表的测试和手动服务。

## 固定预期

| 操作 | 预期 |
|---|---|
| 创建Learn Java/priority2 | 201、返回正id与精确title/details，Location=/tasks/返回id；数据库独立查询存在该行 |
| PUT同id为Learn SQL/priority5 | 200，返回和数据库都更新，不把省略字段当PATCH |
| priority字符串1或1.0000000000000001 | 400 invalid_request，不能先舍入再误接受 |
| 未知constructor根键/子键 | 400，不能先丢弃再返回201 |
| limit缺少/显式空/101 | 缺少用20；空与101为400 |
| DELETE新id再GET | 204空体，再404 not_found；数据库行删除 |
| draft空标题/publish空标题 | 204/400，仅表示校验结果 |
| SQL查询预算 | 3个不同owner：Java逐条4→Join1；Prisma逐条4→批量2；名字保持Alice/Bob/Cara |
| JPA与jOOQ | 托管修改提交写回、detach不写回、merge返回托管副本；真实Schema生成正确查询可编译运行，错误ID类型有精确编译诊断 |

不能通过手写两个相同错误实现满足练习；测试中的状态、字段、计数和数据库结果均有独立预期。全部OpenAPI字节级相等不属于当前合同，方法不支持及路由别名默认差异见[P3-04](../progress/P3-04.md)。

## 必须能解释的机制差异

- 为什么Filter外层经过了，请求仍可能没有执行Controller？Java MVC Interceptor与Nest Interceptor分别能看到什么？
- 为什么JDK和CGLIB都可能绕过目标自调用Advice？如何从真实回滚结果而非方法日志判断事务？
- 为什么close逻辑连接后可再借，而关闭拥有池后应不能借？Prisma Client由谁关闭？
- 为什么修改MyBatis/Prisma普通结果对象不会自动写入，而JPA托管实体可能会？
- 为什么Prisma generate不等于迁移，生成jOOQ字段类型也不保证当前运行库没有漂移？

读者应选择至少3项用自己的话回答，并写出反例；真实试学记录留到P7-03收集。当前技术验证与独立AI审查不能代替该读者效果。
