# 毕业项目备份、恢复与监控

## 数据库备份和恢复

备份目录包含 MySQL 一致性逻辑快照和 SHA-256 校验文件。写入校验文件后备份才可用于恢复。为目录限制访问并使用受保护的备份存储，Windows需使用 NTFS ACL。快照包含账号密码摘要与审计数据等敏感信息。备份期间暂停迁移等表结构变更，订单等 InnoDB 日常写入可继续。

在仓库根目录准备[交付环境](CAPSTONE.md)，将以下变量改为实际**演练**项目名称、环境文件和备份存放位置。备份目录的上级需已存在，每次使用全新目录。

```powershell
$composeFile = 'project/deploy/compose/capstone.yaml'
$envFile = 'project/deploy/compose/.env.capstone'
$backup = 'target/capstone-backup-01'
node project/deploy/compose/database-backup.mjs backup java-for-nestjs-capstone $envFile $backup
```

恢复使用另一 Compose 项目和新数据库卷。先只启动目标 MySQL，应用保持停止，确认前一步成功后才启动应用。此处复用源镜像与数据库配置，目标 HTTP 端口另设以避免冲突。

```powershell
$previousPort = $env:CAPSTONE_HTTP_PORT
try {
    $env:CAPSTONE_HTTP_PORT = '18093'
    docker compose -p capstone-restore --env-file $envFile -f $composeFile up -d --wait --wait-timeout 160 mysql
    if ($LASTEXITCODE -ne 0) { throw 'MySQL startup failed' }
    node project/deploy/compose/database-backup.mjs restore capstone-restore $envFile $backup
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed' }
    docker compose -p capstone-restore --env-file $envFile -f $composeFile up -d --no-build --wait --wait-timeout 160
    if ($LASTEXITCODE -ne 0) { throw 'Restored deployment startup failed' }
} finally {
    $env:CAPSTONE_HTTP_PORT = $previousPort
}
```

脚本在私有临时目录中校验并导入同一份 SQL，检查应用停止且目标库为空。MySQL 原始错误可能含有备份内容，控制台只给出简要错误。导入失败的实例可能已有部分表，保留失败现场排查，在另一空库重新演练。应用从恢复库读取 Flyway 记录，本次演练使用目标卷生成的新密钥。已有密钥需由运维在受保护的位置独立保管。恢复清除会话族和刷新凭据记录，防止已撤销会话随旧快照复活。账号、角色、订单、历史和审计随数据库恢复，用户需重新登录。Redis缓存由应用重建。

要自动比对，先给当前终端设置一次性管理员凭据和 `CAPSTONE_RECOVERY_PASSWORD`（供测试录单员使用）。在源实例备份**之前**执行：

```powershell
$env:CAPSTONE_BASE_URL = 'http://127.0.0.1:18092'
node project/deploy/compose/verify-recovery.mjs seed target/capstone-recovery-fixture.json
```

在恢复实例测得 readiness UP 后执行：

```powershell
$previousUrl = $env:CAPSTONE_BASE_URL
try {
    $env:CAPSTONE_BASE_URL = 'http://127.0.0.1:18093'
    node project/deploy/compose/verify-recovery.mjs compare target/capstone-recovery-fixture.json
    if ($LASTEXITCODE -ne 0) { throw 'Restored data did not match' }
} finally {
    $env:CAPSTONE_BASE_URL = $previousUrl
}
```

脚本验证原有账号能登录，订单金额、版本、状态、历史和带请求 ID 的审计记录逐项一致。校验成功后再决定切换客户端流量。

## 指标、请求日志与告警演练

在**隔离演练**环境用原 Compose 文件加监控配置启动。保留当前项目名及环境文件，让 overlay 沿用同一数据卷。默认 Prometheus UI 仅绑定 `http://127.0.0.1:19092`，应用指标端口留在 Compose 网络中。监控方案使用 [Prometheus 3.13 LTS](https://prometheus.io/docs/introduction/release-cycle/)。

```powershell
$monitor = @('compose','--env-file',$envFile,'-f',$composeFile,'-f','project/deploy/compose/capstone-observability.yaml')
docker @monitor up -d --no-build --wait --wait-timeout 160
node project/deploy/compose/verify-alert.mjs healthy
```

- 应用就绪路径为 `/readyz`。Prometheus 查询 `up{job="capstone"}` 检查抓取，`http_server_requests_seconds_count{job="capstone"}` 检查请求计数。指标按路由模板聚合。
- 使用 `docker @monitor logs --no-color app` 检索响应头中的 `X-Request-Id`。JSON 请求完成日志包含 `requestId`、`method`、`route`、`status` 和 `durationMs`。订单等业务写入另在审计中保留关联 ID。
- 抓取失败 15 秒后，`CapstoneScrapeUnavailable` 进入 firing。API 5xx 在五分钟窗口内估算至少五次且持续三十秒时，`CapstoneApiServerErrors` 触发。规则状态在 Prometheus 的 Alerts 页查看。

确认目标为演练实例后按顺序执行：

```powershell
docker @monitor stop app
node project/deploy/compose/verify-alert.mjs firing
docker @monitor up -d --no-build --wait --wait-timeout 160 app
node project/deploy/compose/verify-alert.mjs recovered
```

修改 Prometheus 回环端口后，设置 `CAPSTONE_PROMETHEUS_URL` 供验证脚本读取。若抓取告警未恢复，先检查应用容器和 `/readyz`，再在 Prometheus Targets 页查看内部端口的抓取错误。5xx 告警先按时间范围检查结构化请求日志，再用请求 ID 联查审计和数据库相关日志。

另在同一隔离环境保持应用运行，停止 MySQL 并执行两次 `node project/deploy/compose/verify-alert.mjs api-errors-sample`。第一组返回后等待 7 秒，保证两批请求完成间有一次抓取。每组核对十条在创建登录事务失败时返回的、带请求 ID 的 500。重启 MySQL，确认 `/readyz` 恢复，再运行 `node project/deploy/compose/verify-alert.mjs api-errors-firing` 观测五分钟窗口内的告警。
