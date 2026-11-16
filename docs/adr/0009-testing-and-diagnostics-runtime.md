# ADR-0009：P4测试、诊断与观测运行物

2026-11-16。沿用Java21/25、Boot3.5.16和Node22.20.0，不为测试章节替换主线框架。JUnit5.12.2、Mockito5.17.0、AssertJ3.27.7从实际Boot BOM取得；文件边界增加无服务单测，保留真实对象服务集成测试，两种证据独立。Mockito以Surefire显式javaagent启动，避免依赖未来不再默认允许的动态附加。

Node已有node:test运行物继续保留；第34章补实际Vitest5.1.0参照。npm元数据要求Node^22.12及Vite^6.4，满足已固定22.20.0/Vite6.4.4；锁精确版本并实际构建/测试/审计，不声称只凭peer范围已兼容。单元测试不启动SQL/Redis/S3，注入外部故障验证服务边界；真实集成另用原CI。

第35章复用/补齐真实MockMvc、Testcontainers与双进程HTTP契约，Docker仅在需要容器的任务运行，不使用H2替代MySQL。第36章诊断用本地可控CPU/分配/锁等待夹具，实际保存JFR/GC/heap/thread证据，Node同题用CPU/heap诊断；文件体积与耗时均为具体环境观察，不外推性能排名。第37章沿用Actuator/Micrometer与真实请求关联，明确是否接入外部Tracing后端，不把日志中的requestId冒充完整分布式追踪。

每类测试与诊断需单独记录命令、平台、结果和限制；独立审查及适用CI后进入in_review，G4另验。本文是实施决定，尚未交付的工具/正文不计完成。

第37章日志参照采用Pino10.4.0（npm精确版本来源），只输出显式最小字段；Java使用SLF4J2键值日志与Boot ECS结构化控制台。HTTP低基数路由标签和requestId连接已有SQL审计，不声称接入OpenTelemetry Collector或完整分布式Tracing。Spring Actuator metrics仅对现有role:manage身份授权，默认安全链继续拒绝未授权访问；Node用独立有限标签计数/耗时摘要，不冒充Micrometer导出协议一致。先补真实请求/错误/敏感信息不输出测试，再写正文。

Tracing补充决定：C37的Tracing不能以requestId替代。Java使用Boot BOM管理的OpenTelemetry1.49.0 API/SDK，Node SDK2.12.0、core/context-async-hooks2.12.0及API1.9.0（SDK声明peer <1.10.0，不直接选最新API1.10.0）。显式SERVER span及sql.audit子span、W3C traceparent传播，采样人工教学请求100%；不自动采集完整URL、headers、SQL或异常正文。仅最小span元数据导出到本地结构化日志，测试用内存exporter核对父子关系/错误状态。未配置外部Collector，不宣称完整分布式系统部署。Node使用每应用独立context manager/provider，不重复注册全局provider。新增真实传播测试及最终ECS JSON断言后再验收。

Node运行依赖使用统一的@opentelemetry/sdk-trace2.12.0（TracerProvider），未保留已由官方标弃用的sdk-trace-node/base包；仍保持SDK2/API1.9的兼容peer范围。SDK导出仅最小span元数据；没有任何外部Collector网络发送，也不采集完整URL、SQL、头、异常文本或baggage。
