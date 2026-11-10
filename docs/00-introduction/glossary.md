# 术语表

按已交付正文逐步维护，不把索引中的计划章节当作已经解释的内容。

| 术语 | 本教程用法 | 常见误解 | 正文 |
|---|---|---|---|
| JDK / JVM / Java SE | 开发工具包、执行class的虚拟机、平台规范/API分别讨论 | Java就是Spring；Node整体只等于JVM | [第01章](../01-java-runtime/01-java-ecosystem.md) |
| classpath / Module Path | 普通类查找根与命名模块解析路径 | PATH、Java包、Maven模块、JPMS模块是一回事 | [第04章](../01-java-runtime/04-compile-and-run.md) |
| JAR / Main-Class | 归档及特定启动方式的入口元数据 | 每个JAR都可直接java -jar或带齐所有依赖 | [第04章](../01-java-runtime/04-compile-and-run.md) |
| 按值传参 / 引用值复制 | 形参复制值；对象字段修改与局部引用重新绑定分开 | Java对象参数按引用传递；赋值自动深拷贝 | [第05章](../01-java-runtime/05-memory-model.md) |
| 对象身份 / 内容相等 | 引用==看身份，String.equals看内容，其他类型按各自约定 | Java String==等于TS string===；equals一律深比较 | [第05章](../01-java-runtime/05-memory-model.md) |
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
| IoC / DI | 对象创建/协作由外部组合，依赖通过明确声明装配 | 业务方法每次主动查容器就等于构造注入 | [第18章](../04-spring-boot/18-dependency-injection.md) |
| Bean / Provider | 特定框架容器中的管理定义及其对象 | 一个类文件存在就自动被所有容器管理 | [第18章](../04-spring-boot/18-dependency-injection.md) |
| singleton / prototype | Spring同一容器/定义共享与按查找等创建的作用域 | 全局唯一；prototype每次业务方法调用自动重注入 | [第18章](../04-spring-boot/18-dependency-injection.md) |
| 注入令牌 | 按框架规则定位依赖的运行时身份 | 两个同描述Symbol必然相同；TS接口自动成为运行时令牌 | [第18章](../04-spring-boot/18-dependency-injection.md) |
| BeanPostProcessor | Spring Bean初始化前后等后处理扩展，可返回包装对象 | 完全等价于Nest请求Interceptor | [第18章](../04-spring-boot/18-dependency-injection.md) |
| 代理自调用边界 | 本例target内部调用未重新经过JDK代理 | 方法没执行；所有织入/代理技术必然同样处理 | [第18章](../04-spring-boot/18-dependency-injection.md) |
| 事务边界 / transaction client | 实际参与同一提交/回滚的资源范围；Prisma回调显式传tx | 源码出现transaction字样就覆盖所有根client或外部副作用 | [第27章](../05-database/27-transactions.md) |
| rollback-only | 本例Spring共用事务被标记为最终应回滚的状态 | catch原异常就自动清除该标记 | [第27章](../05-database/27-transactions.md) |
| REQUIRED / REQUIRES_NEW | Spring加入已有事务或建立独立事务的传播规则 | 与savepoint或Prisma普通函数调用完全等价 | [第27章](../05-database/27-transactions.md) |
| 一致性读 / 隔离级别 | 在固定引擎、级别和读取类型下分析可见性 | RR下任意查询必然永远读旧值；一次实验覆盖所有幻读 | [第27章](../05-database/27-transactions.md) |

自动链接检查核对路径，概念与类比边界仍需独立技术审查。
