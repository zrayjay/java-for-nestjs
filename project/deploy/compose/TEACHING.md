# 完整认证任务教学环境

仓库根目录执行，依赖Docker Engine的Linux容器及Docker Compose V2。不要求宿主Java/Maven；镜像内Wrapper构建，MySQL8.4.7及Temurin21.0.13+9 noble镜像需网络可拉取。当前完整运行链为已交付的认证/授权/任务/审计API与MySQL；不是P6毕业项目，也没有把Redis/文件/消息原语整合进任务API。早期`compose.yaml`的MySQL/Redis用途保持独立。

```powershell
docker compose -f project/deploy/compose/teaching.yaml up -d --build --wait --wait-timeout 240
docker compose -f project/deploy/compose/teaching.yaml ps
```

默认HTTP为`http://127.0.0.1:18089`，MySQL不发布宿主端口。新建`java-for-nestjs-teaching`项目的数据库/密钥命名卷；第一次迁移完成后初始化演示管理员`compose-admin`，默认密码`disposable-admin-password`，只供本机可丢弃教学。启动前可设置`TEACHING_ADMIN_PASSWORD`（16字符起且UTF8不超72字节）、`TEACHING_DB_PASSWORD`、`TEACHING_ROOT_PASSWORD`、`TEACHING_HTTP_PORT`、`TEACHING_IMAGE_TAG`环境变量。不要与早期实验或业务数据库共用卷。

API profile显式启用compose/disposable；普通安全入口不seed账号，默认实验数据库守卫仍拒绝mysql服务名。只有容器模式接受固定`mysql:3306/data_lab`，不能配置成任意远端数据库。首次seed记录BCrypt而非明文；重启保留原密码，改变环境变量不会重置已有身份。RSA私钥在init容器生成，独立卷持久化、应用只读挂载，镜像和仓库不包含私钥；发现半套密钥会拒绝覆盖并失败，不静默重建。

## 操作

以下PowerShell命令不打印Access/Refresh；完整HTTP响应仅保存在变量：

```powershell
Invoke-RestMethod http://127.0.0.1:18089/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:18089/actuator/health/readiness
$teachingLogin = Invoke-RestMethod http://127.0.0.1:18089/auth/login -Method Post -ContentType application/json -Body '{"username":"compose-admin","password":"disposable-admin-password"}'
$teachingHeaders = @{ Authorization = "Bearer $($teachingLogin.accessToken)" }
Invoke-RestMethod http://127.0.0.1:18089/tasks -Method Post -Headers $teachingHeaders -ContentType application/json -Body '{"title":"Compose lesson","details":{"priority":2}}'
Invoke-RestMethod http://127.0.0.1:18089/tasks -Headers $teachingHeaders
docker compose -f project/deploy/compose/teaching.yaml stop api
docker compose -f project/deploy/compose/teaching.yaml up -d --wait --wait-timeout 180
```

若自定义密码/端口，同步修改请求。liveness/readiness初始200/UP；匿名tasks为401；登录与创建分别200/201。readiness包括真实DB，DB不可用时503；liveness保持200，不因一次依赖故障直接杀进程。健康不证明业务授权或每个请求正确。

启动镜像构建中的`-DskipTests`只准备产物，不算测试通过；安全/Data等独立CI继续承担真实契约回归。普通`stop`发送SIGTERM，应用20秒排空，Compose30秒后才允许强杀；应用以非root10001、Java PID1运行。新流量停止接纳与在途完成不是同一时刻，超出预算的请求仍可能被中断。

## 自动故障演练

需要宿主Node22.20.0或受支持的新版本，运行：

```powershell
node scripts/verify-compose-teaching.mjs
```

脚本生成独占随机项目名和新卷，完成后包括失败路径均尝试删除**仅该随机项目**容器/卷；不操作上面手动启动的项目。默认也使用18089，先停手动API或为脚本设置其他`TEACHING_HTTP_PORT`避免端口冲突。日志/摘要留在忽略的`target/compose-evidence`，CI保留7天。命令退出0才是完整实验成功；拉取/构建失败不是排空成功。

实际流程：空卷迁移→登录创建→停DB观察readiness503/liveness200→恢复DB→独立SQL连接锁目标任务→PUT并观察真实MySQL data_lock_waits→SIGTERM并观察开始graceful日志→释放锁→HTTP200、任务与审计同事务提交→进程退出0或143且非OOM/SIGKILL→重启后原Token仍可读到任务。该Token验证同时要求密钥/会话均保留，不能由单独文件存在推断。

独立[Node生命周期参照](../../../examples/container-node-reference/README.md)只验证原生HTTP请求跨SIGTERM排空，不声称部署了同一Nest/SQL业务。容器CI见[containers.yml](../../../.github/workflows/containers.yml)，真实结果及失败过程见[P5-03](../../../docs/progress/P5-03.md)。

## 停止与排错

```powershell
docker compose -f project/deploy/compose/teaching.yaml logs --no-color api keys
docker compose -f project/deploy/compose/teaching.yaml down
```

down保留卷。仅当确定要丢弃本教学项目全部任务、身份和密钥时才自行执行同命令加`--volumes`。这会使旧Access/Refresh失效；不要据环境密码变更推断已有账户重置。

构建若报告Wrapper SHA256错误，先检查构建阶段是否保留unzip：本仓库固定zip校验，Wrapper缺unzip会切tar.gz；不能删除校验来绕过。init失败查卷权限及半套密钥；API未就绪查DB健康、迁移、disposable确认和密码约束。优雅停止失败查Java是否PID1、预算、SQL锁是否释放；退出137不能当作排空完成。当前未声明TLS、公网部署、生产凭据管理、自动备份、高可用或K8s完成。
