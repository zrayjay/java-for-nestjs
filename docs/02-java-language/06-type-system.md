---
title: 06 · Java类型系统
chapter_id: '06'
teaching_status: draft
baseline: Java21 / TS5.9.3 strict / Node22.20.0
---

# 06 · Java类型系统

## 章节范围与先修

先阅读[第05章引用模型](../01-java-runtime/05-memory-model.md)。本章讨论普通Java类型和本仓库TS strict配置，不承诺Java编译器默认消除所有空值错误。完整运行物位于[language-lab](../../examples/java-basics/language-lab/README.md)，统一运行记录见[P2-02](../progress/P2-02.md)。

## 知识点

### ch06-kp01 · 基本类型与数值范围

#### 1. 一句话定义

Java基本类型有各自表示范围和运算规则，int不是TS number换个名字。

#### 2. 为什么需要它

JS中2147483647+1仍为2147483648，Java int却溢出为-2147483648；把金额、计数或标识直接迁移可能改变行为。

#### 3. 生活类比

| 元素 | 对应 | 失效条件 |
|---|---|---|
| 固定位数计数盘 | int固定32位范围 | 不表示所有数值类型都循环溢出 |
| 更大刻度的测量仪 | JS number双精度浮点 | 不是任意大整数精确容器 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：TS number约束表达数值但不区分int/double；Java还有long、float、double等。JS位运算可在特定位置转成int32，但不能把整个JS算术都看作int32。JS bigint和Java BigInteger也有各自API/序列化边界。

#### 5. 两端代码对照

TS[language-lab.test.ts](../../examples/nestjs-reference/src/language/language-lab.test.ts)固定断言普通加法2147483648及显式位转后的-2147483648。Java[LanguageLabTest](../../examples/java-basics/language-lab/src/test/java/dev/javafornestjs/language/LanguageLabTest.java)断言int溢出结果，并用Math.addExact断言ArithmeticException。这是边界对照，不声称两端默认运算相同。

#### 6. 原理与流程图

操作数类型→运算提升规则→目标表示范围→结果或显式检查异常。简单链路用文字足够；先确认表达式实际类型，再讨论赋给long是否能挽救已经发生的int溢出。

#### 7. 类比边界与错误直觉

先用int计算再赋给long不会回到溢出前的数学值；需要在运算前提升或选择检查/大数策略。浮点的舍入又是另一问题，不能用扩大整数范围解决。

#### 8. 企业应用场景与选型

金额通常需要明确的小数或最小货币单位规则，业务ID需评估JSON消费者的精度。类型选择应随数据契约，而非照搬数据库列名；本例整数只做语言实验，不是生产金额实现。

#### 9. 常见错误与排查

溢出异常值先核对每个中间表达式类型。修复后用边界输入回归，不能只用1+2证明安全；要明确是拒绝超范围、扩大范围还是按模运算。

#### 10. 实操练习与验收

基础题：预测两端最大int加1。迁移题：说明TS类型number为何不能提供int溢出检查。思考题：运算后赋给long是否恢复原值？验收：运行测试并解释普通加法、显式检查、位转换三种规则。

<details><summary>完成后查看提示</summary>

类型与检查必须发生在风险运算之前；Math.addExact的抛错不是普通int加法的默认行为。

</details>

### ch06-kp02 · 装箱、拆箱与空值

#### 1. 一句话定义

Integer是可为null的引用类型；自动拆成int需要真实值，null拆箱会抛NullPointerException。

#### 2. 为什么需要它

接口缺失值进入包装类型后，编译器仍可能允许参与算术；“Java是静态类型”不等于“运行时不可能有null”。

#### 3. 生活类比

| 元素 | 对应 | 失效条件 |
|---|---|---|
| 装数字的可空信封 | Integer引用 | 不代表每次装箱都创建不同实例 |
| 从信封取数字 | 拆箱 | 空信封没有可用数字 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：本仓库TS strictNullChecks拒绝将null赋给number，Java默认引用类型不具备同等静态非空承诺。TS检查也不能替外部JSON运行校验；Java可使用校验与空值分析工具，但它们不是普通Integer类型自动具备的能力。

#### 5. 两端代码对照

TS源码[nullableCompileFixture](../../examples/nestjs-reference/src/language/language-lab.ts)用未调用的@ts-expect-error夹具证明配置确实拒绝null→number。Java[LanguageLab.unbox](../../examples/java-basics/language-lab/src/main/java/dev/javafornestjs/language/LanguageLab.java)接收Integer并返回int，测试传null要求真实NPE。Java泛型使用包装类型的原因与限制复用[第08章](08-generics.md)。

#### 6. 原理与流程图

Integer形参接收引用→返回int触发拆箱→null时在拆箱处失败。文字链路足够；报错位置可能离最初引入null的位置很远，因此需追踪输入来源。

#### 7. 类比边界与错误直觉

Integer引用==比较身份，不应因小整数缓存偶然相同推导通用数值相等；数值比较需按空值与类型规则选择。Optional不应用来掩盖所有null设计，下一步先明确缺失是合法状态还是数据错误。

#### 8. 企业应用场景与选型

请求中的可选数量、数据库nullable列、Map查找结果都可能缺失。入口先定义默认、拒绝或缺失分支，再进入要求int的核心规则；不要以异常捕获代替正常的缺失建模。

#### 9. 常见错误与排查

遇到NPE时检查隐式拆箱、三元表达式及自动运算转换，不只搜索显式方法调用。回归用null与合法数字两类输入；不要通过全局填0悄悄改变业务含义。

#### 10. 实操练习与验收

基础题：运行null拆箱反例。迁移题：说明Java Integer与TS strict number的可空承诺区别。思考题：为什么不能统一把null变0？验收：提出明确的缺失契约，并指出校验应发生的入口。

<details><summary>完成后查看提示</summary>

空值可表示未提供、未知、不适用或非法，必须先决定其业务意义。

</details>

### ch06-kp03 · var推断与静态类型

#### 1. 一句话定义

Java var由初始化表达式推断局部变量的静态类型，不是动态类型或任意值容器。

#### 2. 为什么需要它

从TS let迁移时，容易把var当成可随意切换类型的JS变量，或以为它会自动推断出将来想赋的接口类型。

#### 3. 生活类比

| 动作 | 对应 | 边界 |
|---|---|---|
| 按首件货物确定柜型 | 按初始化式推断 | 后续赋值仍受已确定类型限制 |
| 没有首件只有空标记 | var value=null | 没有可推断的具体类型 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：两端都有静态推断，但Java var仅适用特定局部声明等语法位置，不能直接作为普通字段类型或方法返回类型。TS let/const与Java var/final的职责也不同，var不意味着不可重新赋值。

#### 5. 两端代码对照

TS源码使用const/let并依strict构建；Java[CompilationTest](../../examples/java-basics/language-lab/src/test/java/dev/javafornestjs/language/CompilationTest.java)将`var value=null`交给真实JavaCompiler，必须命中cant.infer.local.var.type诊断。正例可在LanguageLabTest看到var从new Account、List、Optional等具体表达式推断，整个模块必须编译通过。

#### 6. 原理与流程图

编译器查看初始化式→确定静态类型→继续按该类型检查使用→产生普通class，不在运行时存放“var类型”。流程简单，不额外画图。

#### 7. 类比边界与错误直觉

推断为实现类可能暴露超出接口的API；为了重构稳定，有时显式接口类型更清晰。var不消除泛型检查，也不能让null具备新的类型信息。

#### 8. 企业应用场景与选型

局部构造明确、右侧已说明类型时使用var减少重复；公共API、复杂链条或容易误推断的场景优先表达意图。规范应围绕可读性，而非一律替换。

#### 9. 常见错误与排查

var推断失败先看初始化式，而非添加强制转换隐藏设计；泛型空集合需要结合目标类型确认元素类型。用编译器诊断验证修改，不以编辑器高亮代替构建。

#### 10. 实操练习与验收

基础题：解释null负例。迁移题：比较TS const与Java final约束什么。思考题：var为何不适合表达所有API？验收：给一个清晰正例和一个应显式声明接口类型的例子，真实负例由测试核对诊断。

<details><summary>完成后查看提示</summary>

推断省略的是源码拼写，不是静态约束；final约束赋值，不自动冻结对象。

</details>

## 章末验收与审查记录

三点分别以数值边界、真实拆箱、真实编译反例验收。状态draft，构建/评审证据见[P2-02](../progress/P2-02.md)，不冒充读者效果通过。

## 参考资料与变更

2026-11-10参考：[JLS21类型与变量](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)、[JLS21转换](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html)、[TS strictNullChecks](https://www.typescriptlang.org/tsconfig/strictNullChecks.html)。
