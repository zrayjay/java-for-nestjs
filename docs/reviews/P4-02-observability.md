# P4观测章节：两轴独立审查

2026-11-16。全文固定范围`ee676ac...5c58300`，只读审查者p2_foundation_standards、p2_foundation_spec，覆盖37章三点及实际指标/Tracing/结构化日志。不代表G4或生产监控平台验收。

## Standards

早期确认事件字段白名单与指标权限合理，但不能只凭ILoggingEvent证明编码。新增真实Tracing后发现Node仅finish结束span，漏掉客户端早断连（P2）。d1d35af改finish/close共享一次性收尾，中止标ERROR/aborted；499仅内部观测值，真实socket中止与正常单次导出测试对应。独立增量关闭P2。

最终全文通过，无新增实质发现。有限指标、真实SDK父子span、最终编码、MDC恢复和敏感信息边界与源码一致。三点十段/题型/源码定位齐备，未混淆内部499与线上响应、SQL调用与事务提交。

审查者实际运行结构/版本检查，未复跑SQL/HTTP。

## Spec

初期requestId关联尚不满足Tracing要求；补真实OTel SERVER和sql.audit子span、W3C入站父上下文、无效上下文及不授予权限的测试后，源码缺口关闭。Java最终ECS JSON测试实际暴露requestId的MDC/kv重复，修为完成日志只取MDC，span exporter临时清MDC按SpanData导出后恢复，组合真实日志/内存exporter核编码与父子关系，未删除断言求绿。

全文限定范围完整，满足错误/耗时/跨层关联与敏感信息不进入指定事件。本地exporter足以支撑本章，不额外要求Collector部署；入站传播仍不等于完整多服务追踪。无新增阻断。

审查者只读核对，未执行SQL或查询CI。

## 每知识点评分

五维通俗/准确/映射/边界/迁移，每项0~2；分数不是读者效果测量。

| 知识点 | Standards | Spec |
|---|---|---|
| ch37-kp01 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch37-kp02 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch37-kp03 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |

## 执行证据与结论

初始日志21+21测试成功，但当时未核最终JSON；1a7106c的Security43856201329在Java最终编码断言失败（22项中1项），Node尚未执行，不据此报成功。修后[Security43857439628](https://github.com/zrayjay/java-for-nestjs/actions/runs/43857439628)对应d1d35af，两JDK各Java22、Node23完整成功，作者查两job日志确认无失败/跳过。

文档构建曾因进度内未转义的路由标签失败，5c58300修复后本机构建成功，最终部署CI另记。第37章in_review，未自动done；G4仍需阶段独立技术验收。
