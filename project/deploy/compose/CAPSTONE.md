# 毕业项目 Compose 交付

从仓库根目录执行。先复制环境模板到本地文件，并为应用账号、数据库管理员和首启管理员设置独立密码。管理员密码长度规则见[项目手册](../../README.md#启动本地应用)。Docker需要能够拉取Node、Temurin、MySQL和Redis基础镜像，并在构建时访问npm和Maven仓库。

```powershell
Copy-Item project/deploy/compose/.env.capstone.example project/deploy/compose/.env.capstone
# 编辑 .env.capstone 中的凭据和端口后执行
docker compose --env-file project/deploy/compose/.env.capstone -f project/deploy/compose/capstone.yaml build app
docker compose --env-file project/deploy/compose/.env.capstone -f project/deploy/compose/capstone.yaml up -d --no-build --wait --wait-timeout 160
docker compose --env-file project/deploy/compose/.env.capstone -f project/deploy/compose/capstone.yaml ps -a
```

默认打开 `http://127.0.0.1:18092`，用空库首启时设置的管理员账号登录。浏览器、API和健康检查共用该端口。MySQL与Redis只接受Compose内网连接。项目使用当前快照生成SDK，构建生成的Vue产物随同一个JAR交付。应用等待MySQL、Redis和签名密钥就绪，Flyway迁移完成后才提供健康响应。

Compose等待 `/actuator/health/readiness` 确认为UP。该探测同时检查应用已完成启动回调与数据库可访问，Redis启动健康由独立容器检查。

要验证空库首启，在同一终端将本地环境文件中的测试账号和密码设置为 `CAPSTONE_ADMIN_USERNAME`、`CAPSTONE_ADMIN_PASSWORD`，然后执行：

```powershell
node project/deploy/compose/verify-capstone.mjs
```

自定义端口时将 `CAPSTONE_BASE_URL` 设为相应的本机URL。验证检查健康状态、HTML引用的JavaScript与CSS、直接访问身份接口的401、首启管理员登录、身份、菜单和退出。验证使用一次性环境。CI从同一提交构建镜像，在独立的Compose项目执行相同验证。

用以下命令查看应用日志和有序停止：

```powershell
docker compose --env-file project/deploy/compose/.env.capstone -f project/deploy/compose/capstone.yaml logs --tail 100 app keys mysql redis
docker compose --env-file project/deploy/compose/.env.capstone -f project/deploy/compose/capstone.yaml down
```

普通停止和重建沿用MySQL数据和签名密钥卷。数据库和首启账号的环境变量只在初始化时生效。Redis只存可重建菜单缓存。应用容器以非root运行，对密钥卷只读，并将临时目录挂成tmpfs。默认端口绑定回环地址，数据库通道面向本机演示内网。用于外部访问时另行配置TLS、密钥管理、数据保留和监控。

若首次生成密钥途中中断，`keys` 会报密钥对不完整并阻止应用启动。已有环境需从受保护的备份恢复同一对PEM。仅在确认是一次性空环境时，另建一个Compose项目名称和全新卷重新演练。

备份恢复、结构化日志、Prometheus 和告警验证见[备份与监控手册](RECOVERY.md)。
