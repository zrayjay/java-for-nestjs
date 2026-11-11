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

语言基础扩展：

| 术语 | 本教程用法 | 常见误解 | 正文 |
|---|---|---|---|
| 装箱/拆箱 | 基本值与包装引用的转换，null拆箱会失败 | Integer天然非空或身份==就是通用数值比较 | [第06章](../02-java-language/06-type-system.md) |
| 名义/结构可赋值 | Java声明接口关系与TS主要结构检查分别分析 | 方法名相同就必然可赋给Java接口 | [第07章](../02-java-language/07-classes-and-records.md) |
| record / sealed | 组件值模型与限制直接子类型的机制 | 深冻结对象或业务权限名单 | [第07章](../02-java-language/07-classes-and-records.md) |
| equals/hashCode | Java哈希键的相等/哈希契约 | 哈希值相同就等于同一键；JS对象Map也按字段判等 | [第09章](../02-java-language/09-collections.md) |
| Stream / Optional | 一次处理管道与可能缺失结果 | Node IO stream；自动验证或所有null的替身 | [第10章](../02-java-language/10-lambda-and-stream.md) |
| checked / suppressed | 编译期异常责任与清理时附加异常 | 自动恢复；与cause完全相同 | [第11章](../02-java-language/11-exceptions-and-annotations.md) |
| Retention / 装饰器 | Java元数据保留与本项目TS legacy注册函数分别讨论 | 注解本身自动执行鉴权 | [第11章](../02-java-language/11-exceptions-and-annotations.md) |
| 虚拟线程 / Future | Java执行线程形式与提交任务结果 | Promise等于线程；等待超时自动取消 | [第12章](../02-java-language/12-concurrency.md) |
| 坐标 / scope | Maven产物标识与各阶段可用/传播规则 | Java import自动下载；scope与npm字段一一对应 | [第13章](../03-build-tools/13-pnpm-to-maven.md) |
| BOM / 依赖管理 | 统一选版规则，不自动引入全部管理项 | 看到BOM条目就认定打包依赖或完整锁图 | [第14章](../03-build-tools/14-pom-lifecycle.md) |
| 有效POM | 合成的Maven项目模型 | 等于实际运行依赖图或所有插件参数完整快照 | [第14章](../03-build-tools/14-pom-lifecycle.md) |
| Reactor | 本次所选Maven项目及构建排序 | 按目录逐个执行；-am自动包含全部下游 | [第15章](../03-build-tools/15-maven-and-gradle.md) |
| 缓存分层 | 下载、增量判定、配置及构建输出分别分析 | cache命中等于测试或业务正确性通过 | [第15章](../03-build-tools/15-maven-and-gradle.md) |
| 模块边界 / 部署单元 | 构建依赖方向与运行部署责任分层 | 每个Maven模块都要拆成微服务 | [第16章](../03-build-tools/16-java-monorepo.md) |

Spring与数据访问扩展：

| 术语 | 本教程用法 | 常见误解 | 正文 |
|---|---|---|---|
| Starter / 自动配置 | 依赖组合与运行时条件注册分别分析，已有Bean可使默认定义退让 | 引入Starter就是Maven启动了服务器；注册后每请求重新评估条件 | [第17章](../04-spring-boot/17-startup-and-autoconfiguration.md) |
| MVC / DispatcherServlet | Servlet栈中协调路由、参数解析及返回值处理 | 等同于Express；Controller存在就一定注册路由 | [第19章](../04-spring-boot/19-rest-api.md) |
| DTO / 转换 / 校验 | 结构表达、输入转换、约束检查分层 | TS类型天然验证JSON；强制转换总能修复输入 | [第20章](../04-spring-boot/20-dto-validation.md) |
| 原始数值token | JSON解析器舍入前的十进制源表示，本例用来核验精确小整数 | JS number保持任意JSON数值的十进制精度 | [第20章](../04-spring-boot/20-dto-validation.md) |
| 校验分组 | 服务端操作选择约束集合，基础组与发布组明确配置 | 客户端能选择跳过规则；两端分组继承完全相同 | [第20章](../04-spring-boot/20-dto-validation.md) |
| Filter / MVC Interceptor | Servlet外层与已识别Handler后的不同扩展位置 | 两者等同；到preHandle就表示Controller已执行 | [第21章](../04-spring-boot/21-request-pipeline.md) |
| SecurityFilterChain | Security代理所选链的内部过滤器顺序 | addFilterBefore等同于Servlet注册order；permitAll证明认证完成 | [第21章](../04-spring-boot/21-request-pipeline.md) |
| JDK / CGLIB代理 | 接口代理与子类代理均需核对真实调用路径 | 切成CGLIB自然修复目标自调用；final任意可拦截 | [第22章](../04-spring-boot/22-aop-and-exceptions.md) |
| Advice / ControllerAdvice | 方法增强与MVC异常/绑定扩展分开 | 名称相近就处理相同范围；MVC能兜底所有Filter异常 | [第22章](../04-spring-boot/22-aop-and-exceptions.md) |
| Profile / 属性源 | 激活配置组与最终覆盖优先级分别判断 | NODE_ENV天然等同Boot Profile；Profile文件适合提交密钥 | [第23章](../04-spring-boot/23-configuration-and-lifecycle.md) |
| DataSource / 逻辑连接 | 连接入口与有限物理池的借用句柄分别拥有生命周期 | 每次close都关闭整个池；池借用超时等于SQL执行超时 | [第24章](../05-database/24-jdbc-and-pools.md) |
| Mapper / Wrapper | SQL方法映射及结构化条件，不提供自动脏检查 | 改TaskRow自动落库；任意原始SQL片段天然安全 | [第25章](../05-database/25-mybatis.md) |
| N+1 / 批量读取 | 用真实语句计数观察逐条关联读取，分别核对结果 | async/await自动消除往返；少查询必然更快 | [第25章](../05-database/25-mybatis.md) |
| 持久化上下文 / 托管 | JPA当前工作单元的实体身份与状态跟踪 | 全局缓存；等同Nest单例或Prisma普通结果对象 | [第26章](../05-database/26-persistence-alternatives.md) |
| detach / merge | 解除跟踪及把状态复制到返回的托管实例 | detach删除行；merge把原对象本身变成唯一受管实例 | [第26章](../05-database/26-persistence-alternatives.md) |
| 生成Schema类型 | 从Schema生成查询字段及编译约束，仍可能与运行库漂移 | 动态SQL字符串也有同等Schema类型检查；编译通过就等于业务安全 | [第26章](../05-database/26-persistence-alternatives.md) |
| Flyway / generate | 迁移改变数据库，客户端生成改变代码，唯一DDL所有者明确 | Prisma generate创建表；多个迁移系统可不协调地共同写同一Schema | [第28章](../05-database/28-database-engineering.md) |
| possible_keys / key | 候选索引与实际所选索引，rows通常为估计 | 候选即必用；一次EXPLAIN就是生产加速保证 | [第28章](../05-database/28-database-engineering.md) |
