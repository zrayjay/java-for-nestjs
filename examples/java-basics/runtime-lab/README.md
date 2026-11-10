# 第02章运行时实验

工作目录均为仓库根。Java语言级别21，实测Microsoft21.0.13.0.2/JBR25.0.4.1；CI使用Temurin21/25。JIT/GC参数针对HotSpot系JVM，不声称适用于所有JVM。

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/java-basics/runtime-lab -am verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
npx --yes pnpm@10.34.9 runtime:verify
```

Windows使用`.\mvnw.cmd`；`JAVA_HOME`须指向带java/javap的完整JDK，脚本用同一JDK的工具。JetBrains附带的JBR不保证包含javap，不能代替完整工具链。无需Docker、HTTP或数据库。

实际运行物：`RuntimeLab.java`包含32位算术、受控线程交错及可回收分配；TS端位于`examples/nestjs-reference/src/runtime/`，包含同步CPU/定时器顺序及真实Worker。

验收脚本执行以下独立进程，任一步失败均以非零退出：

- javap检查class major65及调用字节码。
- `-Xint`与受控HotSpot JIT的计算结果都为380327344，编译日志包含RuntimeLab::mix。
- Node主线程及Worker计算同一int32契约，事件顺序为sync-start、cpu-finished、timer。
- `-Xms16m -Xmx32m -XX:+UseSerialGC -Xlog:gc`运行64MiB累计分配并保留最后一块256KiB，观察young GC日志和程序完成。累计分配量不等于同时存活量，也不等于真实生产堆使用。
- 两个Java线程用屏障固定“先共同读0再各写1”的交错，普通计数为1，原子计数为2。它是可复现的负例，不能据此断言任意调度都丢一次更新。

不测试“JIT一定更快”“GC必须在某毫秒发生”或“Worker一定更快”。需要性能结论时应另做预热、多轮测量和环境记录；此处只验证机制证据及结果一致性。练习、提示和迁移边界见[第02章](../../../docs/01-java-runtime/02-runtime-model.md)。
