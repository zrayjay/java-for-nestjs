# ADR-0009：P4测试、诊断与观测运行物

2026-11-16。沿用Java21/25、Boot3.5.16和Node22.20.0，不为测试章节替换主线框架。JUnit5.12.2、Mockito5.17.0、AssertJ3.27.7从实际Boot BOM取得；文件边界增加无服务单测，保留真实对象服务集成测试，两种证据独立。Mockito以Surefire显式javaagent启动，避免依赖未来不再默认允许的动态附加。

Node已有node:test运行物继续保留；第34章补实际Vitest5.1.0参照。npm元数据要求Node^22.12及Vite^6.4，满足已固定22.20.0/Vite6.4.4；锁精确版本并实际构建/测试/审计，不声称只凭peer范围已兼容。单元测试不启动SQL/Redis/S3，注入外部故障验证服务边界；真实集成另用原CI。

第35章复用/补齐真实MockMvc、Testcontainers与双进程HTTP契约，Docker仅在需要容器的任务运行，不使用H2替代MySQL。第36章诊断用本地可控CPU/分配/锁等待夹具，实际保存JFR/GC/heap/thread证据，Node同题用CPU/heap诊断；文件体积与耗时均为具体环境观察，不外推性能排名。第37章沿用Actuator/Micrometer与真实请求关联，明确是否接入外部Tracing后端，不把日志中的requestId冒充完整分布式追踪。

每类测试与诊断需单独记录命令、平台、结果和限制；独立审查及适用CI后进入in_review，G4另验。本文是实施决定，尚未交付的工具/正文不计完成。

第37章日志参照采用Pino10.4.0（npm精确版本来源），只输出显式最小字段；Java使用SLF4J2键值日志与Boot ECS结构化控制台。HTTP低基数路由标签和requestId连接已有SQL审计，不声称接入OpenTelemetry Collector或完整分布式Tracing。Spring Actuator metrics仅对现有role:manage身份授权，默认安全链继续拒绝未授权访问；Node用独立有限标签计数/耗时摘要，不冒充Micrometer导出协议一致。先补真实请求/错误/敏感信息不输出测试，再写正文。
