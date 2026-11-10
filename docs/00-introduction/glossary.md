# 术语表

按已交付正文逐步维护，不把索引中的计划章节当作已经解释的内容。

| 术语 | 本教程用法 | 常见误解 | 正文 |
|---|---|---|---|
| class / 字节码 | JVM格式的指令及元数据；本实验major65 | 等同于CPU机器码或TS输出JS文本 | [第02章](../01-java-runtime/02-runtime-model.md) |
| JIT | 运行期间编译部分代码的实现策略 | 观察一次编译就证明服务稳态性能 | [第02章](../01-java-runtime/02-runtime-model.md) |
| 存活集 | 某时刻仍须保留的对象及其占用 | 等于累计分配字节总数 | [第02章](../01-java-runtime/02-runtime-model.md) |
| GC roots / 可达性 | 从根沿引用链判断保留关系的模型 | 简单数引用次数或离开作用域立即释放 | [第02章](../01-java-runtime/02-runtime-model.md) |
| 事件循环 | 单个JS执行环境中的回调调度机制 | Node进程只有一个操作系统线程 | [第02章](../01-java-runtime/02-runtime-model.md) |
| Worker | 具备独立JS执行环境的执行单元，可消息通信或显式共享部分内存 | 创建Promise就会产生Worker | [第02章](../01-java-runtime/02-runtime-model.md) |
| 丢失更新 | 多个业务增量基于旧快照写回，覆盖彼此结果 | 等待线程结束就能修复已覆盖结果 | [第02章](../01-java-runtime/02-runtime-model.md) |

自动链接检查核对路径，概念与类比边界仍需独立技术审查。
