# 私有PNG对象实验（实施中）

Java MinIO SDK8.6.0与Node S3 SDK3.1166.0、pngjs7.0.0同题参照。服务从官方MinIO标签RELEASE.2025-04-22T22-12-26Z的固定提交`0d7408fc9969caf07de6a8c3a84f9fbb10a6739e`、Go1.24.2构建，仅用于临时环回实验；原Docker Hub镜像不可拉取，未改用来源不明的镜像。版本选择见ADR0008，不宣称最新或适合公网部署。

当前是内部文件服务，不提供Multipart HTTP入口或真实身份认证。`trustedActor`必须由上层已认证上下文提供，不能直接接受客户端字段；当前测试直接调用边界并验证匿名/跨用户拒绝，未声称经过Spring/Nest安全链。P6整合仍待实施。

在仓库根目录准备一次性对象服务，并设置其教学凭据：

```powershell
$env:FILES_ENDPOINT='http://127.0.0.1:9000'
$env:FILES_ACCESS_KEY='teaching-minio'
$env:FILES_SECRET_KEY='disposable-files-only-password'
.\mvnw.cmd -f examples/file-lab/pom.xml verify
pnpm security:build
node --test examples/nestjs-security-reference/dist/files.spec.js
```

CI自动构建并运行临时进程；本机需要自行启动同版本服务。测试只创建/删除随机桶及自己的对象，不扫描或清理已有桶。Java7项（PNG4、对象3），Node6项，包含有效像素对照、路径/MIME/签名/字节/尺寸伪装、真实SDK往返、匿名对象HTTP403、跨用户读取拒绝、扫描结果/故障和审计故障补偿、公有策略拒绝。尚待首轮实际服务结果。

输入最多1MiB、宽高各1024、像素至多1048576；仅接受非交错8bit RGB/RGBA及IHDR/IDAT/IEND/tEXt块；先检查签名、唯一IHDR、全部块边界/CRC和尺寸，再按扫描行预算限制zlib输出，然后解码，以新图像重编码去除客户端附加元数据；输出同样限1MiB。不同解码器对边缘非法输入可能不同，当前只承诺列出的有效/无效矩阵，不声称实现完整PNG规范验证。文件名限简单ASCII `.png`显示名且拒绝`..`/分隔符/控制字符；存储键始终服务端随机UUID，与客户端文件名无关。流式读取不把无限请求体完整装入内存，但HTTP总请求超时、Multipart封装和代理限额尚待整合。

桶默认私有，构造时保守拒绝任何已有桶策略；对象不发匿名链接或presigned URL，下载从对象元数据比对owner再经SDK读取。共享同一存储凭据的特权写入者仍可能改元数据或策略；当前信任存储管理员，策略检查不抵抗构造后管理员更改，也没实现AWS ACL/完整IAM审计。服务随机对象键不覆盖，未提供客户端指定对象键的上传接口。

扫描器只有扩展接口：默认测试明确`not_configured`，允许重编码后的受限PNG保存，不表示无病毒；infected及扫描异常均拒绝保存，故障不伪造clean。测试注入verdict只验证接口行为，未接真实病毒引擎。

审计只发actor/action/随机key，示例sink非持久化审计库。上传后审计失败尝试删除对象；删除失败显式暴露，不保证跨系统原子事务或进程崩溃恢复。下载审计失败不返回字节。没有生产日志采集或全链路脱敏声明；实际安全模块已有SQL审计，毕业项目需进一步统一。
