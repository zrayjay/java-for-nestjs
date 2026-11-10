# 第27章真实MySQL事务实验

Java21/25，Boot3.5.16、Flyway11.20.3、MySQL8.4.7；Prisma参照固定7.10.1及mariadb3.5.4。独立项目未加入无需数据库的根reactor。技术选择见[ADR-0003](../../docs/adr/0003-transaction-reference-runtime.md)。

这是专用测试库实验：会删除transaction_lab中tx_lab_entries的测试行并重置tx_lab_balances。代码拒绝外部主机或其它数据库名。Java URL仅允许无查询参数或下面精确的`allowPublicKeyRetrieval=true&useSSL=false`，拒绝包括`dbname`覆盖在内的其它连接属性。不要复用业务库；下面公开凭据只用于本地隔离容器。

## 准备独立服务

```sh
docker run --rm --name java-for-nestjs-tx-lab -p 127.0.0.1:13307:3306 -e MYSQL_DATABASE=transaction_lab -e MYSQL_USER=lab -e MYSQL_PASSWORD=lab-only-password -e MYSQL_ROOT_PASSWORD=root-lab-only-password mysql:8.4.7
```

在另一个终端等数据库启动就绪，再于仓库根设置当前进程环境。PowerShell：

```powershell
$env:TX_JDBC_URL = 'jdbc:mysql://127.0.0.1:13307/transaction_lab?allowPublicKeyRetrieval=true&useSSL=false'
$env:TX_DATABASE_USER = 'lab'
$env:TX_DATABASE_PASSWORD = 'lab-only-password'
$env:TX_DATABASE_URL = 'mysql://lab:lab-only-password@127.0.0.1:13307/transaction_lab'
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/transaction-lab/pom.xml clean verify
npx --yes pnpm@10.34.9 transactions:build
npx --yes pnpm@10.34.9 --filter @java-for-nestjs/prisma-reference test:unit
npx --yes pnpm@10.34.9 transactions:test
```

Linux用export设置同名变量，Wrapper改为`./mvnw`。Flyway必须先创建表；Prisma仅生成类型化Client，不运行db push或Prisma Migrate。完成后仅停止本实验容器：`docker stop java-for-nestjs-tx-lab`；--rm会移除它自己的临时容器/匿名存储，不操作既有课程数据卷。

## 验收矩阵

以下为测试必须满足的结果；实际通过证据以事务CI为准，不把本机编译成功当成SQL测试成功。

| 场景 | Spring期望 | Prisma参照期望 |
|---|---|---|
| 正常返回 | commit行存在 | commit行存在 |
| 未捕获运行时错误 | 无行 | 无行 |
| checked异常默认规则 | checked-default行保留 | 无直接checked异常规则；抛出Error使callback拒绝并回滚 |
| 显式checked rollbackFor | 无行 | 参照callback拒绝回滚 |
| 错误事务边界 | target自调用使runtime行按自动提交保留 | 错用根client使outside行保留；成因不同 |
| REQUIRED内层异常被外层捕获 | rollback-only导致UnexpectedRollbackException，全回滚 | 本例捕获应用Error后callback正常返回，两个成功写入提交 |
| 内层独立提交，外层回滚 | REQUIRES_NEW内层保留 | 显式第二个独立事务保留，不称为Prisma传播功能 |
| READ COMMITTED两次读取 | [100,200] | [100,200] |
| REPEATABLE READ两次一致性读取 | [100,100] | [100,100] |

隔离测试不靠sleep或线程碰运气：读取事务先读100，另一个连接上的独立事务把值改为200并提交，然后第一个事务再读。只验证普通一致性读，不推广到锁定读、所有幻读或任意数据库。

Java9项SQL测试、Java2项URL守卫单测、Node7项SQL测试和2项连接边界单测均须通过。预期异常由断言捕获，测试本身必须退出0。实际代理由Proxy.isProxyClass断言，默认checked规则未被全局覆盖。池大小4，以免刻意的第二事务因只剩一个连接自我等待；生产连接池容量另需按真实并发设计。

## CI与当前环境

`.github/workflows/transactions.yml`使用Ubuntu24.04、Temurin21/25及真实MySQL8.4.7服务；每个组合先生成/编译Prisma、执行连接守卫单测、再执行Flyway/Spring verify及Prisma SQL测试。服务与数据库随runner回收，测试不跳过。

本机Windows的Docker仍不可用，只完成Java test-compile、Prisma generate/tsc和连接守卫单测，不能声称本机数据库已通过。失败必须保留日志；恢复服务后重跑完整verify，不使用skipTests掩盖缺口。
