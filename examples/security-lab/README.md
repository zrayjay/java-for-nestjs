# 安全教学实验（实施中）

G3技术准入后的P4独立模块，复用Data/HTTP普通JAR，显式security模式替换P3开放链。主线：RS256 Access JWT、高熵不透明Refresh、MySQL轮换与会话族撤销、当前数据库RBAC及部门/本人数据范围、同事务审计。尚未完成P4全部功能或独立验收。

真实MySQL仅允许专用data_lab。测试清理data_tasks/data_owners与sec_*夹具，不能用于业务数据。原transaction_lab守卫不变。与P3数据实验分别使用临时库环境，不同时对同一个表运行破坏性测试。

根目录准备复用JAR，再执行验证：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/spring-web-lab -am install -DskipTests
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/data-access-lab/pom.xml install -DskipTests
$env:DATA_JDBC_URL='jdbc:mysql://127.0.0.1:3306/data_lab?allowPublicKeyRetrieval=true&useSSL=false'
$env:DATA_DATABASE_USER='lab'
$env:DATA_DATABASE_PASSWORD='lab-only-password'
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml verify
```

前两步只安装构建产物，不是测试通过声明。测试明确激活security-test并生成临时RSA密钥、插入BCrypt实验用户；普通入口不会创建默认账号或静默生成签名私钥。

手动启动先生成被git忽略的本机教学密钥（拒绝覆盖已有文件）：

```powershell
java -cp examples/security-lab/target/classes dev.javafornestjs.security.KeyTool
java -jar examples/security-lab/target/security-lab-0.1.0-SNAPSHOT-exec.jar --security.private-key-path=target/demo-keys/private.pem --security.public-key-path=target/demo-keys/public.pem
```

绑定127.0.0.1:8089。需要自行准备用户/角色夹具；测试用户只存在测试代码，不是生产默认账号。Auth API有POST /auth/login、/auth/refresh、/auth/logout及GET /me；角色管理员PUT /admin/users/{id}/roles。任务API仍是/tasks，普通成员读本部门/写本人，reader不能写，admin按数据库权限可跨部门。未实现租户，不伪称租户隔离。

每个Refresh只用一次；已消费令牌重放会撤销整个会话族，包括刚轮换出的Access。并发刷新只有一次轮换成功，另一次识别重放后族被撤销；网络重试此处需要重新登录，不能将保守撤销误称无损自动重试。库中只保存SHA256，不存原始Refresh。注销/禁用/角色调整对后续请求生效，不保证取消已开始的在途请求。

认证只从Authorization Bearer读取Access，Refresh从JSON正文读取；不接受Cookie认证，CSRF关闭仅限此协议。若以后改HttpOnly Cookie/BFF，应重新设计CSRF。当前监听环回，未提供公网部署/TLS配置、限流、Redis或对象存储完成保证。

本机目前编译、打包和TokenKeysTest3项通过；真实HTTP/SQL安全矩阵待专用CI，见[进度](../../docs/progress/P4-01.md)。
