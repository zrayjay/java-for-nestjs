# ADR-0008：P4安全、缓存与上传运行时

2026-11-11，G3技术准入后实施采用。继续模块化单体教学，不把P3 permitAll入口直接描述为生产认证。新增独立security-lab复用HTTP/Data普通JAR，安全模式显式启用；原P3默认开放模式和测试独立保留。独立MySQL/Redis/对象存储CI不进入无服务的根Reactor。

认证采用Spring Security OAuth2 Resource Server/Nimbus（Boot3.5.16管理），Node参照用jose6.2.17验证RS256，固定issuer/audience/有效期及所需声明，不手写JWT签名或信任客户端角色。密码BCrypt，Access短期JWT，Refresh为高熵不透明随机值、库中只存SHA256。MySQL事务/行锁实现单次轮换，已消费Refresh重放撤销整个会话族；拒绝结果在事务提交后抛出，避免回滚撤销。注销撤销族，认证核对服务端会话与启用状态；权限从当前数据库读取。固定Clock用于过期实验，不用sleep模拟JWT到期。

签名私钥不提交仓库。正常入口要求显式外部PEM密钥；只有明确测试配置可临时生成RSA2048及以上密钥。令牌不接受Cookie隐式认证，CSRF关闭仅适用于当前Authorization头/JSON Refresh教学协议；BFF/Cookie方案须重新设计CSRF，不外推为通用建议。

RBAC明确用户/角色/权限与数据范围：普通成员可读本部门、写自己的任务，具备全局权限者可跨部门；只读角色不能写。未实现多租户，不伪称租户隔离。直接HTTP与数据层负例核对，前端按钮权限元数据不替代服务端授权。安全上下文按请求隔离，数据库写入使用带权限范围的条件避免先查后改竞态。

缓存使用既有Redis7.4.6、Java Spring Data Redis及Node ioredis5.8.2（未跟随最新6.0.0跨主版本）。授权判定不依赖缓存；小整数示例缓存只覆盖授权范围内数据。TTL/空值缓存/单进程并发合并/写后失效分别验证，明确外部写入、Redis故障与多实例一致性边界，不宣称分布式锁或强一致。

对象存储选MinIO临时环回测试服务、Java SDK8.6.0、Node @aws-sdk/client-s3 3.1166.0。SDK与服务固定来源/版本，Node22满足S3 SDK>=20要求；Java标签已从官方仓库确认。PNG限定1MiB和像素预算，先检尺寸与签名再解码重编码；Node pngjs7.0.0/@types6.0.5。用户文件名不成为对象路径，随机键、私有桶、服务端读取授权。扫描只有扩展接口时返回not_configured，不宣称已有杀毒引擎。

P4继续实际JUnit/Mockito/AssertJ、MockMvc/Testcontainers、JFR/GC/heap/thread诊断与跨层观测；每类结果单独记录。完整JWT/刷新重放/部门越权/上传负例和实际Redis/S3结果为G4前置；本ADR不是实现或安全验收证明。真实读者效果仍由P7完成。
