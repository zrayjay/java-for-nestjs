---
title: 14 · POM、生命周期与版本管理
chapter_id: '14'
teaching_status: draft
baseline: Maven3.9.11 / Java21 / Boot3.5.16
---

# 14 · POM、生命周期与版本管理

## 章节范围与先修

先读[依赖机制](13-pnpm-to-maven.md)。本章用仓库当前有效模型解释行为，不把父POM声明直接当运行类路径。运行说明见[构建实验](../../examples/java-basics/BUILD-LAB.md)，证据见[P2-03](../progress/P2-03.md)。

## 知识点

### ch14-kp01 · 生命周期阶段与插件goal

#### 1. 一句话定义

生命周期phase规定构建阶段顺序，插件goal执行具体工作；调用verify会经过此前相关阶段，插件绑定决定每阶段做什么。

#### 2. 为什么需要它

把verify当作一个孤立测试脚本，或将clean与default生命周期混为一条，容易误判哪些编译/测试/打包动作实际发生。

#### 3. 生活类比

| 元素 | 对应 | 边界 |
|---|---|---|
| 标准工序站点 | phase | 站名不是具体实现 |
| 站点上的机器动作 | plugin goal | 可显式调用，不必总从默认阶段进入 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：pnpm scripts是命令入口，Maven phase绑定了约定的构建生命周期；名叫build/test的npm脚本具体做什么取决于内容，不自动具备Maven相同阶段图。

#### 5. 两端代码对照

TS examples:build调用tsc，examples:test单独运行已输出JS测试，故顺序不能省略。Java根POM将Checkstyle绑定validate，JUnit由Surefire测试阶段执行，CLI的shade在package生成带core的产物。实际root verify日志显示这些动作；元数据goal `dependency:tree`/`help:effective-pom`用于检查，不等于完整verify。

#### 6. 原理与流程图

```mermaid
flowchart LR
  V[validate] --> C[compile]
  C --> T[test]
  T --> P[package]
  P --> R[verify]
```

图省略中间细分阶段以聚焦主链；clean是另一生命周期，`clean verify`依次调用两者，不是verify自动clean。

#### 7. 类比边界与错误直觉

一个phase可能有多个goal，打包类型和配置会改变绑定；verify不意味着所有可能集成测试都存在。skipTests跳过测试不是验证通过。

#### 8. 企业应用场景与选型

把质量检查绑定到稳定生命周期，CI调用统一入口；新增集成测试需选择合适插件/阶段并说明服务前置。不要依赖作者手工记得额外运行未登记的检查。

#### 9. 常见错误与排查

goal没执行，查绑定phase、模块选择、profile及skip参数；执行了但未测所需链路，查测试发现规则和断言。日志中的BUILD SUCCESS只对实际执行范围负责。

#### 10. 实操练习与验收

基础题：在日志找validate/test/package。迁移题：比较两个TS脚本与Maven verify。思考题：直接dependency:tree成功能否证明单测通过？验收：回答不能，列出实际verify测试数与日志。

<details><summary>完成后查看提示</summary>

先列执行目标，再看goal绑定，最后核对实际日志，不从命令名字推断覆盖。

</details>

### ch14-kp02 · 继承、聚合与有效POM

#### 1. 一句话定义

parent继承配置，modules聚合构建；effective POM展示合成后的模型，两者不是同一个关系。

#### 2. 为什么需要它

把模块放进modules却以为自动继承父配置，或看到父POM管理Spring就断言子模块运行时必加载Spring，都会误判。

#### 3. 生活类比

| 元素 | 对应 | 边界 |
|---|---|---|
| 使用同一本规章 | parent继承 | 不是自动加入同一批次构建 |
| 同一批次装配清单 | modules聚合 | 不直接建立业务依赖 |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：workspace组织与共享配置有类似动机，pnpm workspace本身不自动成为Maven parent配置继承。根package脚本也不自动等同Reactor依赖排序。

#### 5. 两端代码对照

TS根workspace配置选择Nest项目，tsconfig另行定义编译规则。Java java-basics POM聚合各实验，子POM显式parent；根又继承Boot父项。实际help:effective-pom为core生成XML，XPath核对java.version与maven.compiler.release为21；字节码另由javap核对major65。

#### 6. 原理与流程图

父模型/项目声明/适用配置合成→effective POM→插件读取参数→实际产物。属性绑定不一定展开成configuration XML节点；本实验首次错误要求必须存在release节点，读取真实模型后改为属性+显式覆盖+产物分层核对。

#### 7. 类比边界与错误直觉

有效模型不是运行时依赖图，也不等于所有插件实际参数和外部环境的完整快照。需要依赖树和产物证据共同判断，不能仅搜索某个字符串。

#### 8. 企业应用场景与选型

共享构建规范可放父POM；组织构建可由聚合POM承担，未必强制所有库同一parent。升级父项前比较实际插件/依赖管理变化及回归，不只改一个版本号。

#### 9. 常见错误与排查

看不懂继承值就生成effective POM并定位来源；属性正确而产物目标不对，还要查显式插件覆盖及实际JDK。不要把XML里没有配置节点等同功能没启用。

#### 10. 实操练习与验收

基础题：生成core有效POM。迁移题：区分聚合与配置共享。思考题：java.version21能否单独证明产物major65？验收：用模型属性与实际javap两类证据回答。

<details><summary>完成后查看提示</summary>

声明意图、合成模型、插件执行和最终产物分别核对。

</details>

### ch14-kp03 · BOM、管理条目与冲突选择

#### 1. 一句话定义

dependencyManagement/BOM集中管理版本等默认规则，通常不自动引入依赖；实际冲突选择仍取决于项目声明和Maven规则。

#### 2. 为什么需要它

子POM没写JUnit版本却能解析，来自父级管理；但因此误认为BOM列出的所有库都自动打包，会把构建管理当运行依赖。

#### 3. 生活类比

| 元素 | 对应 | 边界 |
|---|---|---|
| 批准采购的版次目录 | BOM/管理条目 | 列在目录不等于实际采购 |
| 冲突时的选版规则 | 依赖调解 | 不是所有生态都选“最高版本” |

#### 4. Node.js / TypeScript / NestJS 技术对照

部分可比：pnpm overrides与Maven管理版本都可约束选择，但解析模型和优先级不同。Maven普通传递冲突常用nearest definition，同深度受声明顺序影响；还需考虑直接依赖与管理规则，不能照搬npm直觉。

#### 5. 两端代码对照

TS根package使用overrides固定相关依赖，真实锁文件另记录解析图。Java core的JUnit无显式版本，真实树解析到5.12.2，继承的Boot管理提供选择；core字节码仍只依赖java.base。此例验证管理条目与实际引入的区别，没有新造多版本冲突矩阵，冲突规则按官方文档核对，不能把未执行夹具称为已测。

#### 6. 原理与流程图

声明/管理约束→解析依赖路径→调解选择→实际图→编译打包。遇到不符预期的版本，沿dependency:tree向引入边追踪，管理值与最终值分别检查。

#### 7. 类比边界与错误直觉

强制版本能消除图上的重复选择，不保证二进制/API行为兼容；exclusion切掉传递依赖也可能造成运行缺类。依赖与插件版本管理是不同块，别用BOM假设所有插件已锁定。

#### 8. 企业应用场景与选型

组织BOM可统一受支持组合，升级仍需回归应用及第三方starter。安全修复应追踪实际可达版本，不只在某处写上安全版本就宣布依赖图已更新。

#### 9. 常见错误与排查

版本不符时查直接声明、管理来源和最近路径，修复后重新生成真实图。不要通过排除报错库再忽略运行失败；保留升级前后行为证据。

#### 10. 实操练习与验收

基础题：找到JUnit实际版本与管理来源。迁移题：解释BOM与override/锁图区别。思考题：选了更高版本是否必然兼容？验收：回答不能，并说明需要哪些编译/运行回归。

<details><summary>完成后查看提示</summary>

选版一致是必要信息，不是行为兼容性的完整证明。

</details>

## 章末验收与审查记录

当前draft。生命周期用真实日志，有效模型与产物交叉核对；未执行全部冲突/profile组合实验。见[P2-03](../progress/P2-03.md)。

## 参考资料与变更

2026-11-10参考：[Maven生命周期](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)、[effective-pom goal](https://maven.apache.org/plugins/maven-help-plugin/effective-pom-mojo.html)、[依赖调解](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)。
