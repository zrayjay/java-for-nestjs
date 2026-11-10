---
title: '08 · 泛型：TS类型消失与Java擦除'
description: 用真实编译器、PECS复制和堆污染反例，认识泛型能保证与不能保证的事。
chapter_id: '08'
teaching_status: draft
baseline: Java21 / JDK21与25 / TypeScript5.9.3 / Node22.20.0
---

# 08 · 泛型：TS类型消失与Java擦除

## 章节范围与先修

目标：解释普通泛型对象的擦除、声明元数据保留、上界及装箱、Java不变性/通配符/PECS，并识别TS静态类型与外部输入的边界。需要了解接口、函数参数及数组；第06/07章仍在计划中，可先用本章完整示例验证，不把缺失先修页伪装成已完成。

共同业务契约是复制整数序列`[1,2]`到目标集合。Java允许目标是Number或Object的列表，TS同题运行值为number数组；边界演示另行标注，不能把Java装箱类型、TS结构约束或可变数组赋值规则称为完全相同。

完整代码及命令见[泛型实验README](../../examples/java-basics/generics-lab/README.md)。Java使用真实JavaCompiler执行正负例，故正常Maven构建应当成功，同时证明某些夹具被javac拒绝；不是把五个错误文件直接放入生产目录来制造失败CI。

## 知识点

### ch08-kp01 · 擦除不等于所有元数据消失

#### 1. 一句话定义

Java泛型以擦除机制实现：类型变量擦除到其最左上界，无显式上界时为Object；必要的强制转换和桥接方法由编译器等机制处理，声明处仍可保留泛型签名。

#### 2. 为什么需要它

从TS迁移时容易认为“泛型运行时没了”便足够解释一切。实际上Java反射可能看见字段声明的`List<String>`，但不能靠普通ArrayList对象直接区分本次实例化时写的是String还是Integer；这两个问题必须分开。

#### 3. 生活类比

用“库房通用箱子与登记簿”的模型：实际搬运的箱型对应擦除后的类，登记簿上的约定对应声明签名。标签不是每次开箱都自动检查内容的保安；这个边界比“所有标签都被撕掉”更准确。

| 模型元素 | 技术对象 | 不成立的推断 |
|---|---|---|
| 同一种通用箱型 | 普通泛型类的运行时Class | 每种类型实参都生成新的运行时类 |
| 字段声明的登记簿 | javac保留的Signature元数据 | 任意对象都能还原实例的类型实参 |
| 取出时按约定检查 | 某些读取位置的编译器插入转换 | raw type污染时能在写入处自动拒绝一切错误 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：TS普通泛型参数不会自动成为JS运行时的输入验证，Java的普通泛型实参也不是普遍可测试的运行时标签。不同点是Java还有class签名、边界、桥接等规则；TS的结构赋值与编译输出机制也不同。装饰器或显式传入的类构造器可能提供额外值/元数据，但不等于运行时自动恢复任意泛型实参。

#### 5. 两端代码对照

TS完整文件为[generics-lab.ts](../../examples/nestjs-reference/src/generics/generics-lab.ts)，普通`copyValues<T>`运行时只是迭代值；没有一个可用的`typeof T`检查。

Java完整文件为[GenericLab](../../examples/java-basics/generics-lab/src/main/java/dev/javafornestjs/generics/GenericLab.java)，片段：

```java
public static <T> T identity(T value) {
    return value;
}
```

[运行测试](../../examples/java-basics/generics-lab/src/test/java/dev/javafornestjs/generics/GenericLabTest.java)验证`new ArrayList<String>()`和`new ArrayList<Integer>()`的运行时类相同；identity擦除后的返回类型是Object，而字段`List<String>`声明可以反射得到String。这不是只看一行伪代码的结论。

#### 6. 原理与流程图

源码类型检查→擦除与生成class→运行时执行擦除后的方法→需要时进行转换；另一条链是字段声明→Signature元数据→反射读取声明。两条链并列，使用文字即可避免画成“每个对象都带着泛型标签”的错误结构图。自定义子类或框架显式保存类型信息属于额外机制，应分别核验。

#### 7. 类比边界与错误直觉

不能推出“运行时没有任何泛型信息”，也不能推出“反射看见某字段是`List<String>`，就证明外部传来的每个元素都是String”。擦除到最左上界也不总是Object；下一节的Number上界方法反射结果就是Number。JLS4.6及真实反射断言是本节依据。

#### 8. 企业应用场景与选型

设计通用响应或序列化API时，若库需要目标元素类型，应通过其明确支持的类型令牌、声明信息或转换器传入，并验证真实输入。泛型工具类适合保留编译期关系，不应承诺仅靠`T`实现任意反序列化或运行时鉴权。

#### 9. 常见错误与排查

| 触发 | 现象 | 根因 | 修复与回归 |
|---|---|---|---|
| 写入两个仅List实参不同的重载 | javac拒绝同擦除签名 | 两者擦除后都接收List | 改方法名或重新设计参数；CompilationTest实际验证拒绝 |
| 用反射Class判断ArrayList元素类型 | 无法区分String/Integer实例 | 实参不是普通对象的独立运行时类 | 使用声明或显式验证；运行反射正例理解范围 |

#### 10. 实操练习与验收

- 基础题：找出反射测试中Class、擦除返回类型、声明泛型类型三种不同观测。验收：能解释它们为何不矛盾。
- 迁移题：为什么TS的`T`和Java的反射签名都不能自动验证任意HTTP JSON？验收：指出静态承诺、声明元数据与实际数据之间的差别。
- 思考题：把identity的T改为Number上界，擦除返回类型应是什么？验收：预测Number并实际观察maxNatural的反射断言。
- 命令：泛型README的Maven verify，元数据断言和相同擦除重载负例全部通过。

<details><summary>完成后查看提示</summary>

不要把“某声明有泛型签名”和“某对象能普遍报告自己的实际T”写成同一句话。普通ArrayList例子不覆盖所有可具体化类型（reifiable types）、具体子类或框架类型令牌。

</details>

### ch08-kp02 · 上界、交集与基本类型限制

#### 1. 一句话定义

上界限定类型参数可使用哪些契约；Java21泛型实参须为引用类型，交集上界可以同时要求类及接口能力。

#### 2. 为什么需要它

无上界T不能直接调用任意业务方法。迁移TS的`T extends Shape`时，还要看Java是否要求实现某接口、继承某类，而不是仅凭对象恰好有一个同名字段。

#### 3. 生活类比

入场要求同时具备“人员类别”和“操作资格”：Number作为类别，Comparable作为比较能力。资格门槛使工作人员能调用约定动作；它不验证所有业务值，例如金额范围、币种或NaN规则。

| 模型元素 | 技术对象 | 边界 |
|---|---|---|
| 类别要求 | Java类上界 | 不是只要长得像就自动符合 |
| 操作资格 | 接口上界 | 不保证方法的业务语义正确 |
| 同时满足门槛 | 交集上界 | 不等于所有数值可任意互相比 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比的是受约束T才允许相应操作。Java的Number/Comparable绑定在名义类型体系上；TS本例`T extends { value: number }`按结构约束。它们演示约束机制，**不是同一个输入模型**。Java的Integer装箱也不能直接对应成TS的Number包装对象；TS应用通常使用原始number值。

#### 5. 两端代码对照

TS完整文件中的形状约束示例：

```ts
export function maxValue<T extends { value: number }>(left: T, right: T): T {
  return left.value >= right.value ? left : right
}
```

Java完整文件中的自然顺序示例：

```java
public static <T extends Number & Comparable<? super T>> T maxNatural(T left, T right) {
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
    return left.compareTo(right) >= 0 ? left : right;
}
```

Java测试验证整数2/4得到4；TS验证两个带value对象返回value4的对象。这里明确不是逐字段API契约对比，共同复制业务在下一节。JavaCompiler实际拒绝违反Number上界的String调用；tsc核验字符串value的预期错误。

#### 6. 原理与流程图

调用点推断或指定T→检查上界→在方法体内按上界允许的操作编译→运行对应实现。Java交集上界的类若存在须位于第一位；本例擦除到最左Number，而Comparable能力仍用于静态检查和生成必要的调用转换。直线关系用文字即可，不额外画无信息图。

#### 7. 类比边界与错误直觉

`T extends Number`不表示`List<T>`自动能赋给`List<Number>`；容器的赋值关系是另一个问题。`Comparable<? super T>`也不是“任意两个Number都能混比”；T仍要满足同一次调用的推断约束。基本类型`List<int>`在Java21不合法，自动装箱解决值的转换，不取消引用类型实参限制。

#### 8. 企业应用场景与选型

通用排序、选最大值等API应只要求真正使用的能力，避免为代码复用把所有业务对象硬塞进Number继承树。对于金额/日期/优先级等不同排序语义，显式Comparator往往比不合适的继承更清楚；本实验不代替业务数值验证。

#### 9. 常见错误与排查

| 触发 | 现象 | 根因 | 修复与回归 |
|---|---|---|---|
| `List<int>` | javac拒绝类型参数 | 实参须为引用类型 | 按语义使用Integer或基本类型数组；负例验证拒绝 |
| T没有上界却调用专有方法 | 编译无法解析调用 | 没有静态契约保证该能力 | 增加合适接口/函数参数，不用raw type绕过 |

#### 10. 实操练习与验收

- 基础题：运行基本类型及String违反Number上界的两个编译负例。验收：由真实javac产生ERROR，而不是字符串匹配伪日志。
- 迁移题：为一个只有`value:number`字段的TS对象解释为何不能自动当作Java Number。验收：区分结构兼容与名义继承。
- 思考题：上界越宽是否总越好？验收：指出会限制方法内部可用能力，API应服务真实需求。
- 命令：Maven verify加TS build，两个预期TS错误不能被忽略；错误消失时ts-expect-error指令会使构建失败。

<details><summary>完成后查看提示</summary>

先问算法到底要自然顺序、数值相加还是一个提取函数。不要从“有比较功能”倒推出“必须继承Number”，也不要把编译通过当作输入金额在业务上合法。

</details>

### ch08-kp03 · 不变性、通配符与PECS

#### 1. 一句话定义

Java普通泛型容器不因元素继承而自动协变；`? extends T`适合从未知子类型读取T，`? super T`适合写入T，PECS据数据流选择约束。

#### 2. 为什么需要它

若`List<Integer>`能直接当成`List<Number>`，接收方就可能写入Double，破坏原列表的元素承诺。通配符让“只需要安全读取/写入某种值”的API表达得更准确，而不是用强制转换压掉错误。

#### 3. 生活类比

生产方托盘保证取出的物品属于某类，但你不知道托盘专门装哪一种子类，不能随便塞入该类的另一子类。消费方收集箱保证能放进指定物品，但箱中已有物品不一定只有那一种。

| 模型元素 | 技术对象 | 边界 |
|---|---|---|
| 未知子类的供货托盘 | `List<? extends T>` | 不是绝对不可变，仍可能clear/remove或放null |
| 可接收指定物品的收集箱 | `List<? super T>` | 读取不能普遍承诺为T，只安全视为Object |
| 同一搬运物品契约 | 泛型方法中的T | 源和目标约束要一起推断，不是分别随便选类型 |

#### 4. Node.js / TypeScript / NestJS 技术对照

Java使用点通配符**无直接等价于某个TS语法的对应物**。TS本例用readonly源数组表达不通过该引用写入，但readonly不等于Java的`? extends`。TS可变数组的赋值规则也不等于Java List不变性：本实验实际展示number[]可赋给(number|string)[]，写入string后原数组出现静态类型未能阻止的情况。

#### 5. 两端代码对照

共同契约是保持次序复制1、2，完整文件分别在Java GenericLab和TS generics-lab：

```ts
export function copyValues<T>(source: readonly T[], destination: T[]): void {
  for (const value of source) destination.push(value)
}
```

```java
public static <T> void copyValues(List<? extends T> source, List<? super T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
```

Java运行测试将`List<Integer>`复制到`List<Number>`及`List<Object>`，均得到[1,2]；TS同题目标得到[1,2]。JavaCompiler另拒绝不变List直接赋值及向extends生产方添加任意Integer；TS测试如实展示自己的可变数组规则，没有假称两者都拒绝相同赋值。

#### 6. 原理与流程图

```mermaid
flowchart LR
  S[未知T子类型的源列表] -->|读取为T| M[copyValues的T]
  M -->|写入T| D[接收T或其父类型的目标列表]
```

目标可能原本包含其他允许的对象，因此图中箭头不是可逆的“目标读取一定为T”。源通配符也不承担集合不可变性、快照一致性或线程安全保证。

#### 7. 类比边界与错误直觉

不能推出“extends就是只读集合”：clear和remove等操作仍可能允许；不可变集合是另一层契约。也不能推出“super读取是T”。当同一API既消费又生产精确的同一种元素类型时，普通T参数可能更合适；PECS不是遇到泛型就机械加通配符。

#### 8. 企业应用场景与选型

把查询结果送到审计收集器或转换流水线时，先标出数据从哪读、往哪写，再写泛型签名。生产接口对外承诺集合不可变时，应提供相应不可变值/视图或拷贝策略，而不只在泛型签名中写extends。

#### 9. 常见错误与排查

| 触发 | 现象 | 根因 | 修复与回归 |
|---|---|---|---|
| 将`List<Integer>`直接赋给`List<Number>` | javac拒绝 | 不变性保护写入契约 | 用适合数据流的extends参数或复制，不做未检查转换 |
| 在extends源中add(Integer) | javac拒绝 | 实际捕获子类型未知 | 修改API职责或写入已知消费者；真实编译负例验证 |

#### 10. 实操练习与验收

- 基础题：复制到Number和Object列表。验收：两目标都按顺序得到[1,2]。
- 迁移题：解释TS数组污染测试为何不能用于证明Java List会接受同样赋值。验收：指出静态赋值规则的差别，不仅复述“Java更严格”。
- 思考题：生产方调用clear是否与PECS矛盾？验收：解释安全的元素类型约束与不可变性的区别。
- 命令：Maven verify中的两个真实编译负例及TS可变数组负例均通过。

<details><summary>完成后查看提示</summary>

从未知Integer/Double子列表读取Number安全；向它写一个具体Integer却可能破坏另一种实际子列表。向Number或Object目标写Integer安全，反向把已有Object当Integer读则没有保证。

</details>

### ch08-kp04 · raw type、堆污染与运行时输入

#### 1. 一句话定义

绕过泛型检查可能使一个静态参数化引用指向不符合其元素承诺的数据；普通泛型类型声明不能代替运行时数据验证。

#### 2. 为什么需要它

遗留Java API可能返回raw List，TS里的JSON.parse也可能使不可信数据进入静态类型“看起来正确”的变量。此时错误可能在读取或调用方法时才暴露，排查不能只盯着抛异常那一行。

#### 3. 生活类比

登记簿写着“字符串箱”，但有人绕过入库流程放入数字，领取者按字符串使用时才发现。污染发生点与报错点可以不同。模型不保证运行时一定有箱子标签检查，也不代表所有混合类型集合都属于错误。

| 现实动作 | 技术对象 | 边界 |
|---|---|---|
| 绕过入库流程 | Java raw/unchecked写入 | 正常泛型代码不能据此被说成无用 |
| 按标签使用物品 | 读取处转换或后续调用 | 异常可能晚于错误写入 |
| 逐件验货 | 显式运行时校验 | 只能保证检查过的条件，不能自动覆盖业务规则 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比的是静态承诺与实际值脱节的风险。Java raw type/堆污染和TS的any、断言或可变数组赋值漏洞具有不同成因；Java的ClassCastException也不等于JS每次都会抛相同异常。Nest接口输入应由真实校验逻辑或Pipe处理，而不是相信`Dto<T>`的类型参数会自动检查请求。

#### 5. 两端代码对照

Java故意污染只在测试中出现，并明确压制该反例的unchecked/rawtypes警告：

```java
// GenericLabTest中的反例片段，不是生产推荐写法。
List<String> strings = new ArrayList<>();
List raw = strings;
raw.add(42);
assertThrows(ClassCastException.class, () -> strings.get(0).length());
```

TS生产演示先接收unknown并执行真实检查：

```ts
export function isNumberArray(value: unknown): value is number[] {
  return Array.isArray(value) && value.every((item: unknown) => typeof item === 'number')
}
```

测试把JSON解析为unknown，确认`[1,"wrong"]`被拒绝、`[1,2]`被接受。此守卫只检查数组/number类型，不保证金额范围、有限值或权限等业务条件。

#### 6. 原理与流程图

未经检查的数据进入→静态引用仍保持原承诺→读取时转换/调用暴露不一致。修复应追溯入口、减少raw/any逃逸并按需求验证，而不是在每个失败读取处随意吞异常。文字链路足以说明污染与失败位置分离，不用复杂图掩盖因果。

#### 7. 类比边界与错误直觉

不能因为能用raw绕过检查就说泛型没有价值；正常静态检查拦截了大量错误。也不能因为某守卫返回true就断言数据符合所有业务规则。Java的泛型擦除不是“写入所有错误值都安全”的许可证；本章负例明确在读取处失败。

#### 8. 企业应用场景与选型

包装遗留API时把unchecked范围缩到可审查的边界，并明确验证策略。API、消息或数据库JSON字段从unknown输入到领域值的转换应有运行时校验和负例。不要为消除编译错误，在整个模块上大范围SuppressWarnings或把TS输入全部改成any。

#### 9. 常见错误与排查

| 触发 | 现象 | 根因 | 修复与回归 |
|---|---|---|---|
| raw List写入错误类型 | 类型化读取抛ClassCastException | 未检查写入破坏承诺 | 回溯写入和遗留边界，保留污染负例及正确类型路径 |
| 把JS数组赋给更宽的可变数组后写string | 原number引用后续调用toFixed失败 | TS允许的赋值不保证写入安全 | 避免暴露可变别名、按语义用readonly/拷贝与入口验证；执行真实负例 |

#### 10. 实操练习与验收

- 基础题：指出Java堆污染写入点与读取失败点。验收：负例被assertThrows捕获，正常构建仍成功。
- 迁移题：给isNumberArray增加“必须有限”的业务要求。验收：为NaN/Infinity写测试，并说明这是新增语义而非number类型自动提供。
- 思考题：如何包装一个不可修改的raw遗留库？验收：说明可检查边界、数据验证及局部警告说明，不在全局关闭类型检查。
- 命令：Java verify、TS build/test均成功，故意错误夹具确实被编译器或运行断言捕获。

<details><summary>完成后查看提示</summary>

可在既有typeof检查旁增加Number.isFinite，再分别测试合法数字、非数字和非有限数字。若把isNumberArray称为“业务金额校验器”，还必须增加单位、范围、精度等规则，本章没有声称已实现这些能力。

</details>

## 章末验收与审查记录

运行实验README的全部命令，并逐项解释：擦除后仍可有什么元数据；何时擦除到Number；PECS为什么不等于不可变；TS数组赋值与Java List不变性为何不同；编译通过为何不验证HTTP JSON。

| 知识点 | 运行或编译证据 | 独立审查 |
|---|---|---|
| ch08-kp01 | Class/方法/字段反射及同擦除重载负例 | 待审 |
| ch08-kp02 | 交集上界正例、primitive/String上界负例、TS预期错误 | 待审 |
| ch08-kp03 | 双端[1,2]复制、Java赋值/写入编译负例、TS数组污染反例 | 待审 |
| ch08-kp04 | ClassCastException断言与unknown真实守卫正反例 | 待审 |

实际版本、命令、结果和作者逐点自检见[P1-02验证记录](../progress/P1-02.md)。当前初稿不计完成，G1仍需四篇试点整体独立验收。

## 参考资料与变更

2026-11-10核对官方入口可访问；规范范围优先于类比：

- [JLS21第4节](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)：4.4类型变量与上界、4.5参数化类型、4.6擦除、4.7可具体化类型（reifiable types）、4.8 raw types、4.12.2堆污染。
- [TypeScript泛型手册](https://www.typescriptlang.org/docs/handbook/2/generics.html)：类型参数、约束及接口/类中的泛型；不能将文档中的TS机制套成Java规范。
- [TypeScript兼容性说明](https://www.typescriptlang.org/docs/handbook/type-compatibility.html)：结构兼容与有意保留的不健全部分。
- [术语表](../00-introduction/glossary.md)与[兼容矩阵](../00-introduction/compatibility-matrix.md)。

本章不升级语言版本或框架，也不把能反射到声明签名推广成任意对象都能还原其T。
