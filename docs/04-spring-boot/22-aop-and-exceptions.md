---
title: '22 · AOP、代理与异常'
description: 用真实JDK/CGLIB代理、目标自调用和HTTP异常映射区分机制边界。
chapter_id: '22'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 22 · AOP、代理与异常

## 章节范围与先修

先完成11、18、21章。本章[ProxyModelTest](../../examples/spring-web-lab/src/test/java/dev/javafornestjs/web/ProxyModelTest.java)使用Spring ProxyFactory真实创建两种代理；[ApiErrors](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/ApiErrors.java)及[Nest API](../../examples/nestjs-reference/src/api/api.ts)验证请求异常表示。事务自调用的真实SQL反例已在第27章，不能用本章字符串事件实验代替事务证据。

## 知识点

### ch22-kp01 · JDK代理与CGLIB子类代理

#### 1. 一句话定义

Spring AOP通常通过代理在目标方法外插入Advice，JDK动态代理实现接口，CGLIB代理通过子类拦截可覆盖的方法。

#### 2. 为什么需要它

类型转换失败、final方法不被增强或对象看起来不是原类，往往与代理策略有关。只知道“加注解会生效”不足以判断调用是否真的通过了代理。

#### 3. 生活类比

客户通过代理人预约服务，代理人在转交前登记。JDK代理提供约定的服务接口，子类代理像以相同业务类型提供扩展接待。类比不意味着代理能够修改任意私有或final方法。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest Interceptor包装路由执行流，可比较横切关注点，但不是JDK接口代理或CGLIB子类。JS Proxy也不是Spring AOP的等价实现：语言类型、方法覆盖及框架接入点不同。TS interface运行时消失，不能直接生成Java式接口代理。

#### 5. 两端代码对照

Java测试同一个Worker先设置proxyTargetClass=false，再true，分别断言Proxy.isProxyClass与AopUtils.isCglibProxy。Advice记录`advice:execute`后调用invocation.proceed。Nest完整TraceInterceptor用next.handle返回Observable并finalize；它演示请求横切处理，不声称验证Java代理策略。

#### 6. 原理与流程图

```text
调用者 → 代理 → Advice前段 → invocation.proceed → 目标方法 → Advice后段
```

JDK模式本例代理可转Work接口而不是Worker类；CGLIB模式代理也是Worker子类型。测试直接断言这些运行时差异，不依赖类名字符串猜测。

#### 7. 类比边界与错误直觉

final类不能被CGLIB继承，本实验有真实失败断言；final方法也不可通过覆盖拦截。不是每个Spring Bean都有代理，是否需要代理由具体基础设施和配置决定。此处显式ProxyFactory测试不等于已经演示所有自动代理创建条件。

#### 8. 企业应用场景与选型

事务、缓存、方法级安全等基础设施可能使用代理。优先按接口注入依赖并理解现有框架策略，避免业务代码依赖生成代理类名。需要拦截构造或目标内部任意调用时，代理模型可能不满足需求，应重新设计边界或评估织入方式，而不是随意切换开关。

#### 9. 常见错误与排查

强转为实现类失败时检查当前是否JDK代理。Advice完全不执行时检查Bean是否来自容器、是否通过代理引用、方法能否被代理以及切点是否匹配。不要一开始就改成CGLIB，切换策略也不能解决所有自调用问题。

#### 10. 实操练习与验收

运行ProxyModelTest，两种代理外部execute都应记录advice后work，final类子类代理必须失败。解释为什么JDK代理不是Worker实例，而CGLIB代理是。测试中的具体异常类型是框架配置错误，不是业务异常映射。

### ch22-kp02 · 目标自调用绕过Advice

#### 1. 一句话定义

代理将调用委托给独立目标对象后，目标对象内部通过this调用自身方法通常不再经过外部代理。

#### 2. 为什么需要它

带Transactional或Cacheable的方法由同类另一个方法直接调用时，注解看起来存在，运行效果却可能缺失。问题在调用路径，不在注解拼写。

#### 3. 生活类比

客户通过前台联系员工时前台会登记；员工在办公室内部继续处理另一事项，不会自动重新经过前台。换成另一种前台外观也不能让内部动作重走入口。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest Interceptor包裹框架发起的路由执行，手工调用某个Controller方法也不会自动重进整个请求链。这可帮助理解“入口决定增强”，但不能推出Nest和Spring有完全相同的this/代理语义。CGLIB子类代理仍可能委托独立目标，本实验确实显示自调用不受Advice。

#### 5. 两端代码对照

Java Worker.self仅调用execute。外部先执行proxy.execute再proxy.self，预期：

```text
advice:execute, work, advice:self, work
```

最后的work之前没有第二个advice:execute，两种代理都如此。Nest trace只观察真实HTTP的Controller入口；直接调用普通服务方法没有请求Guard/Interceptor证明，不把这类调用当HTTP测试。

#### 6. 原理与流程图

```text
外部 → proxy.self → Advice → target.self → target.execute
                                          ↑ 未返回proxy
```

若把需要独立增强的方法移到另一个容器Bean，通过注入的代理引用调用，就改变了调用边界。是否应该拥有独立事务还需按业务一致性决定，不能为“让注解亮起来”随意拆事务。

#### 7. 类比边界与错误直觉

“private不能代理”与“自调用绕过”是两个问题，public自调用同样可能绕过。切成CGLIB并非普遍修复。AspectJ织入与代理AOP不是同一种机制，本课程没有用织入测试来替换代理实验。

#### 8. 企业应用场景与选型

跨服务事务方法应定义清晰应用边界；同Bean内部辅助方法通常不需各自独立事务。如果确实要新事务，应说明提交/回滚关系，并用真实数据库负例验证。第27章提供自调用、回滚与传播实验。

#### 9. 常见错误与排查

先绘制实际对象引用和调用箭头，确认调用从哪一个代理进入。自己new对象、同类this调用、final方法和错误切点分别排查。只看日志中的方法名不能证明事务已开启，必须检查真实提交/回滚结果。

#### 10. 实操练习与验收

测试中若把预期改成两次advice:execute，必须失败。解释为何self本身有Advice而内部execute没有。进一步按第27章运行真实SQL事务矩阵，再讨论事务结论，不能据本章列表事件声称数据库回滚已验证。

### ch22-kp03 · Advice、ControllerAdvice与ExceptionFilter

#### 1. 一句话定义

AOP Advice增强方法执行，MVC ControllerAdvice集中处理控制器相关异常和绑定等扩展，Nest ExceptionFilter则在其请求异常上下文中决定响应，它们不是同一扩展点。

#### 2. 为什么需要它

把所有名字带Advice的组件当成同一种拦截机制，会导致安全过滤器异常漏处理或后台异常被误认为已转成HTTP。正确划分作用域才能稳定错误契约。

#### 3. 生活类比

业务操作旁的监督员和服务窗口的投诉处理处职责不同。监督员能观察特定方法，投诉处负责把相关失败写成回执，但不能处理发生在另一栋楼、从未进入窗口的所有事故。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring RestControllerAdvice配ExceptionHandler可对照Nest Catch/ExceptionFilter的HTTP错误表示目的。Nest Interceptor还可catchError处理流错误，但不是所有后台Promise的兜底。Spring Security的401/403通常应配置安全层处理器，不依赖MVC Advice捕获Filter异常。

#### 5. 两端代码对照

Java ApiErrors把绑定/校验/非法参数转400、MissingTask转404、未支持方法转405、其他异常转500。Nest Filter读取HttpException状态并生成稳定code；当前未支持方法默认走404。打包共同契约断言400/404；两端各自真实HTTP测试另外在测试环境替换仓库写入行为，注入意外异常并断言500和仅含internal_error的响应体。没有为生产入口添加故障路由，也不声称405完全一致。

#### 6. 原理与流程图

```text
MVC参数/Controller/服务异常 → MVC异常解析 → 受控JSON
Nest请求处理异常 → ExceptionFilter → 受控JSON
外层安全拒绝 → 安全入口/拒绝处理器（后续安全章节）
```

异常转响应后仍要保留内部诊断能力。教学简化错误体不代表生产应静默吞掉意外异常；可观测性章节会补关联和脱敏。

#### 7. 类比边界与错误直觉

统一错误格式不能把所有错误改成200，也不能把所有异常改400。非法用户输入与程序缺陷应区分；隐藏堆栈并不意味着不记录。404码在资源缺失和路由缺失都可出现，但排查原因不同。

#### 8. 企业应用场景与选型

对外稳定错误码、对内保留异常因果链，适合长期演进接口。不要把SQLException消息原样交给前端。异常映射应覆盖所需业务类型并用负例验证，避免大范围catch后虚报成功。

#### 9. 常见错误与排查

响应已经提交后再写JSON可能失败，检查异常发生阶段。Advice未触发时先查是否发生在MVC外层。事务内catch异常而正常返回会改变回滚行为，须到第27章核查实际事务规则，不能凭HTTP500推断全部写入已撤销。

#### 10. 实操练习与验收

发送非法JSON得到400且仅code，删除后读取得到404。对照两端未支持方法默认差异。运行WebApiTest的仓库Spy故障测试和Nest独立故障测试，要求500不泄漏注入的private-database-detail；该替换只存在测试实例，正常打包运行不提供故障入口。

## 章末验收与审查记录

真实JDK/CGLIB、自调用及final类负例通过；400/404通过实际HTTP和共同契约。当前draft，独立审查待完成。官方参考：[Spring AOP代理](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)、[MVC异常处理](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)、[Nest异常过滤器](https://docs.nestjs.com/exception-filters)。
