# 真实MySQL数据访问实验（实施中）

独立Maven项目，依赖根工程spring-web-lab的普通JAR，复用其Controller/Service并替换Repository。MyBatis是业务主路径，JPA和jOOQ只是隔离对照。SQL只允许本地data_lab专用实验库；测试会删除data_tasks中的数据，请勿用于真实业务库。URL守卫在创建池之前执行，拒绝其他库、远程主机和未审核参数；不会修改原有transaction_lab守卫。

基线：Boot3.5.16、Java21编译/Temurin21和25运行、MyBatis-Plus3.5.18、MySQL8.4.7、Flyway11.20.3、Hibernate6.6.53.Final、jOOQ3.19.35（后两者由Boot管理）。Flyway独占DDL，JPA仅validate。

先在仓库根安装所需模块：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/spring-web-lab -am install -DskipTests
```

上一步仅准备复用JAR，不是测试通过证据。完整HTTP测试由Foundation执行；本模块的数据库验证如下：

```powershell
$env:DATA_JDBC_URL='jdbc:mysql://127.0.0.1:3306/data_lab?allowPublicKeyRetrieval=true&useSSL=false'
$env:DATA_DATABASE_USER='lab'
$env:DATA_DATABASE_PASSWORD='lab-only-password'
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/data-access-lab/pom.xml clean verify
```

需要已存在且可用的MySQL8.4.7空data_lab及仅用于该库的账号。缺少环境变量或数据库时应失败，不跳过、不换H2。Windows本机的Docker限制仍未解决，本机目前仅编译/打包及两项URL守卫通过；完整SQL已在Linux Temurin21/25通过，见[实施记录](../../docs/progress/P3-02.md)，新增负例以对应后续修订为准。

实验覆盖：真实HTTP→Service→Mapper→MySQL；XML绑定参数、Lambda Wrapper、分页；3个不同owner的N+1四次SQL与Join一次SQL；Hikari两连接耗尽/归还；Flyway历史/校验/幂等；EXPLAIN possible_keys与实际索引；JPA同上下文身份/脏检查/分离/合并；jOOQ从迁移后MySQL生成Java源码、编译正确类型查询并真实执行，再验证错误字段类型不能编译。不提供生产容量或任意SQL计划最优承诺。
