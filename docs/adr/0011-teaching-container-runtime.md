# ADR-0011：P5教学容器运行物

2026-11-17，P5-01/02进入in_review后采用。P5-03将已验证P4认证任务API及其真实MySQL依赖组成可一键启动的单体示例；既有compose.yaml仍用于早期MySQL/Redis实验，新增teaching.yaml避免改变基础CI契约。当前完整示例指认证/任务/MySQL运行链，不把所有独立实验强行装配成业务，也不提前声称P6缓存/文件/消息业务集成完成。

复用security-lab exec JAR，Docker多阶段构建通过Wrapper安装HTTP/Data/Security依赖。Temurin21.0.13+9 noble镜像候选，实际拉取/运行及摘要由CI证明；不能将本机registry失败记为镜像可用。运行镜像非root，以exec形式让Java为PID1，显式优雅停止20秒、Compose宽限30秒。使用专用新项目名/数据库卷，暴露HTTP仅主机127.0.0.1，MySQL不发布宿主端口。

默认实验仍仅接受环回data_lab；显式DATA_DEPLOYMENT_MODE=compose才接受唯一jdbc:mysql://mysql:3306/data_lab及限定查询参数。fixture/protocol tests默认保护不放宽。新增compose profile只在明确disposable教学确认下初始化单一演示管理员，密码来自环境，首启BCrypt入库，重启不重置；普通安全入口继续不创建默认账号。RSA密钥由单次init容器在私有命名卷生成，应用只读挂载，重启保留，镜像/仓库不包含私钥。

健康探针区分liveness/readiness，readiness包含真实DB；就绪表示可接流量，不证明业务授权完整。优雅停止实验证明实际SQL事务中PUT在SIGTERM期间完成：另一个连接锁住目标行，确认真实数据库等待后停止API，释放锁，核对HTTP成功、事务数据、退出非SIGKILL和重启持久性。不新增线上可调用的sleep或故障接口，不把空闲stop当在途请求证据。

Compose是P5-03，K8s/滚动更新为P5-04，镜像扫描/发布回滚为P5-05，40章在两部分齐备前保持draft。真实演练日志、镜像版本/摘要、独立两轴审查及失败记录分别归档，不自动done或G5通过。
