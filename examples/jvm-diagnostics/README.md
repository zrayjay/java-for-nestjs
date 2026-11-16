# JVM与Node诊断夹具（实施中）

只运行本目录的可丢弃教学进程，数据均为人工生成，不对业务进程取转储。输出在被忽略target/的新目录，脚本拒绝覆盖旧证据。需要完整JDK21/25的javac/jcmd/jfr、PowerShell7及Node22.20.0。

仓库根目录：

```powershell
.\scripts\measure-jvm-diagnostics.ps1 -OutputDirectory target/diagnostics-run1
node scripts/measure-node-diagnostics.mjs target/node-diagnostics-run1
.\mvnw.cmd -pl examples/spring-web-lab -am package -DskipTests
pnpm examples:build
node scripts/load-http-lab.mjs target/http-load-run1
```

package -DskipTests仅准备HTTP运行物，不算测试通过。JVM脚本实际编译夹具，以64~96MiB堆运行约12秒；16MiB人工保留字节数组、受控分配、cpu热循环与一个monitor竞争。断言JFR包含ExecutionSample/GarbageCollection/JavaMonitorEnter/ObjectAllocationSample，CPU样本定位夹具方法，线程转储确有lab-blocked BLOCKED，堆文件为HPROF且GC日志有实际Pause。

Node脚本生成CPU profile、采样分配profile、实际heap snapshot及V8 GC日志。约5秒CPU/分配阶段与Java夹具不同，不比较耗时或采样数量排名；采样分配profile不是完整对象普查。Node没有Java monitor BLOCKED的直接等价物，本实验不伪造Node线程转储。

独立HTTP负载对两真实任务进程各预热20次、并发4持续约3秒，检查每响应200及空列表，记录成功数/错误数/RPS/p50/p95。只覆盖环回内存GET，无TLS/数据库/认证；不是生产容量报告。HTTP负载与人工JFR夹具为不同实验，不把后者热点当作前者瓶颈。

完整转储可能很大，CI只保留7天；提交目录只放代码和文字证据，不提交二进制转储。本机采集/HTTP负载及Diagnostics43823124708的Windows/Linux×Temurin21/25均通过；具体数据与独立审查见P4-02进度。
