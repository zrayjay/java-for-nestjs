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
| 类型参数 / 类型实参 | 声明中的T与使用时传入的String等类型 | 把类型参数当作可直接typeof的运行时值 | [第08章](../02-java-language/08-generics.md) |
| 擦除 | 类型变量按最左上界等规则映射到运行时类型 | 所有元数据都消失，或一律变成Object | [第08章](../02-java-language/08-generics.md) |
| 可具体化类型 / reifiable type | JLS4.7定义的运行时可充分表示的类型类别，如基本类型、非泛型类/接口、raw type或满足规则的无界通配符参数化类型 | 泛型声明保留Signature就能使任意`List<String>`对象实参可具体化 | [第08章](../02-java-language/08-generics.md) |
| 不变性 | List的元素继承关系不自动变成容器赋值关系 | `List<Integer>`必然可赋给`List<Number>` | [第08章](../02-java-language/08-generics.md) |
| 通配符 / PECS | 根据生产/消费T的数据流表达Java使用点约束 | extends等于不可变，super读到的总是T | [第08章](../02-java-language/08-generics.md) |
| raw type / 堆污染 | 绕过泛型检查等路径可能破坏参数化引用的元素承诺 | 泛型正常检查没有价值，或运行时自动拒绝全部错误写入 | [第08章](../02-java-language/08-generics.md) |

自动链接检查核对路径，概念与类比边界仍需独立技术审查。
