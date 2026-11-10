---
title: 10 · Lambda、Stream与Optional
chapter_id: '10'
teaching_status: in_review
baseline: Java21 / TS5.9.3 / Node22.20.0
---

# 10 · Lambda、Stream与Optional

## 章节范围与先修

先读[集合](09-collections.md)。本章处理小型内存序列，不把Stream当Node网络流，也不声称parallelStream可无条件提速。运行物见[语言实验](../../examples/java-basics/language-lab/README.md)，证据见[P2-02](../progress/P2-02.md)。

## 知识点

### ch10-kp01 · 函数式接口与捕获变量

#### 1. 一句话定义

Java lambda以目标函数式接口为类型，捕获的局部变量必须final或有效final；捕获对象仍可包含可变状态。

#### 2. 为什么需要它

JS闭包常读取后来重新赋值的let变量，把相同写法迁到Java可能编译失败；改成容器字段后虽然能编译，也不自动线程安全。

#### 3. 生活类比

| 元素 | 对应 | 失效条件 |
|---|---|---|
| 指定规格的工作卡 | 函数式接口 | 函数签名不保证无副作用 |
| 带走一张固定引用凭证 | 捕获有效final局部量 | 凭证指向对象仍可能变化 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：TS函数类型与Java函数式接口都表达调用能力；Java不是将任意JS闭包语义原封移植。方法引用是调用表达方式，不是立即调用的结果，也不天然保证纯函数。

#### 5. 两端代码对照

TS测试让闭包捕获let value，之后改为2，调用读取2。Java[CompilationTest](../../examples/java-basics/language-lab/src/test/java/dev/javafornestjs/language/CompilationTest.java)相应重新赋值捕获局部量，必须命中cant.ref.non.effectively.final.var。正例LanguageLab.total以IntUnaryOperator表示乘2，并通过方法引用用于映射；共同序列结果为60。

#### 6. 原理与流程图

目标接口确定参数/返回→编译核对lambda→保留所需捕获→调用时执行。简单顺序用文字说明；捕获并不等于立即求值函数体。

#### 7. 类比边界与错误直觉

有效final不是深不可变，也不是同步屏障。为绕开编译限制把值塞进数组或AtomicInteger，只改变状态存放/操作方式，业务并发正确性仍需分析。

#### 8. 企业应用场景与选型

小型映射/谓词适合lambda；复杂业务可提取具名方法以便测试和阅读。回调中是否访问可变外部状态应明确，尤其在并行或异步执行时。

#### 9. 常见错误与排查

捕获变量错误先核对是否有任何重新赋值；不要立刻引入共享可变容器。需要累加时优先考虑归约，或明确所需并发原语及测试。

#### 10. 实操练习与验收

基础题：运行局部捕获编译负例。迁移题：比较JS let闭包测试。思考题：捕获final列表是否能修改元素？验收：回答可能，并区分引用赋值与对象修改。

<details><summary>完成后查看提示</summary>

检查捕获的是哪个变量、其引用是否可重绑、其对象是否可变，三问分开。

</details>

### ch10-kp02 · Stream惰性、终止与复用

#### 1. 一句话定义

Stream描述一次数据处理管道，中间操作通常惰性，终止操作触发处理；用过的Stream不能当集合重复遍历。

#### 2. 为什么需要它

JS Array.map通常立即生成结果，而Java stream.map后不终止可能什么都没有执行；把日志写进管道也可能被优化或短路影响。

#### 3. 生活类比

| 元素 | 对应 | 失效条件 |
|---|---|---|
| 先拟工序单 | 中间操作 | 不承诺执行每个观察副作用 |
| 下单实际加工 | 终止操作 | 短路不一定处理所有元素 |
| 一次订单 | 一次Stream使用 | 不是可反复读取的集合本体 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：filter/map/reduce表达相似数据转换，但Array方法求值与Stream生命周期不同。Node readable stream处理IO/背压，不能按相同名称直接等价Java内存Stream。

#### 5. 两端代码对照

TS total过滤[1,10,20]、乘2、求和60；Java对应filter/mapToInt/sum也断言60。TS Array.map计数立即为2；Java专门测试构建map后计数0、toList后计数2，再次toList抛IllegalStateException。计数仅为展示本例求值，生产逻辑不应依赖map副作用计数。

#### 6. 原理与流程图

```mermaid
flowchart LR
  S[数据源1,10,20] --> F[filter至少10]
  F --> M[map乘2]
  M --> T[终止sum]
  T --> O[60]
```

数据源仍是集合，管道不是持久存储；实现可融合处理，不要求先物化每个中间数组。

#### 7. 类比边界与错误直觉

count等终止操作可能利用源特征省略某些中间计算；不要用peek/map写必须执行的审计。parallelStream还有共享状态、顺序与线程池问题，不能仅加parallel就声称更快。

#### 8. 企业应用场景与选型

内存集合转换可用Stream表达过滤映射；数据库大表应优先在查询层选择数据，不先全量加载再过滤。需要逐项外部副作用时应清楚表达执行与错误策略。

#### 9. 常见错误与排查

没有输出先检查是否终止、是否短路或被允许优化；复用异常应重建管道而非缓存旧Stream。空集合归约也要验证初值与缺失语义。

#### 10. 实操练习与验收

基础题：预测计数0→2。迁移题：说明Array eager与Stream lazy差异。思考题：map内日志能否证明每项业务一定完成？验收：运行求和及复用负例，并指出副作用与结果契约不同。

<details><summary>完成后查看提示</summary>

先写结果函数，再用测试核对结果；不要让调试日志成为业务处理是否完成的唯一证据。

</details>

### ch10-kp03 · Optional与缺失建模

#### 1. 一句话定义

Optional显式表达某个结果可能不存在，调用方仍需选择缺失策略，它不是自动校验或所有nullable字段的替身。

#### 2. 为什么需要它

查找用户可能没结果；无条件get会把正常缺失变异常，而orElse中的昂贵参数也可能在值已存在时先执行。

#### 3. 生活类比

| 元素 | 对应 | 失效条件 |
|---|---|---|
| 标明有/无结果的回执 | Optional | 回执不能证明输入有效或值对象的内部字段非空 |
| 需要时才制作备选 | orElseGet供应器 | 函数本身有副作用时仍需负责 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：TS返回string或undefined可表达缺失，`??`可短路后备表达式；Java Optional具有方法API和运行时对象概念。两者都不能取代输入验证，且null、undefined与空字符串不是同一状态。

#### 5. 两端代码对照

TS find返回u1或undefined，`?? fallback()`在找到时不调用fallback。Java find返回Optional，测试找到u1与missing为空；orElseGet供应器调用数为0，而orElse(fallback())因方法实参先求值，调用数为1。源码及完整命令在语言实验README，固定断言不依赖时间测量。

#### 6. 原理与流程图

查找→有值则转换/返回→无值按约定提供备选或抛错。orElse的参数在调用前已求值，orElseGet把计算交给供应器；这一表达式求值顺序用文字足够。

#### 7. 类比边界与错误直觉

Optional.empty不是业务错误的统一包装；使用get并不会消除缺失。不要返回null代替Optional.empty，也不要把Optional当作任意框架实体字段都支持序列化/持久化的保证。

#### 8. 企业应用场景与选型

查询可能无结果的API可返回Optional，让调用者明确选择404、默认或下一步查找；多个不同失败原因需要更丰富结果类型或异常契约，不能全部压成empty。

#### 9. 常见错误与排查

找到结果却仍访问备用服务，检查orElse参数求值；修复为按需供应器并用调用计数回归。缺失时抛错是否合理，应按业务场景决定而非普遍禁止异常。

#### 10. 实操练习与验收

基础题：跑有值/无值与后备计数测试。迁移题：将TS `??`迁为适当Java调用。思考题：校验失败能否直接empty？验收：说明信息损失风险，分别表达未找到与非法输入。

<details><summary>完成后查看提示</summary>

缺失、无效、访问失败是不同结果；Optional只覆盖其中“可能无值”的承诺。

</details>

## 章末验收与审查记录

当前draft。真实捕获编译反例、惰性/一次性管道、后备求值均已接入测试；未做并行性能实验。见[P2-02](../progress/P2-02.md)。

## 参考资料与变更

2026-11-10参考：[Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)、[Optional](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)、[JLS lambda](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.27)。
