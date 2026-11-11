---
title: '21 · 请求处理链'
description: 用每请求轨迹区分Servlet Filter、安全过滤器与MVC拦截器。
chapter_id: '21'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 21 · 请求处理链

## 章节范围与先修

先完成19~20章路由和校验。完整Java链路在[WebChainConfiguration](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/WebChainConfiguration.java)及[RequestTrace](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/RequestTrace.java)，Nest在[trace.ts](../../examples/nestjs-reference/src/api/trace.ts)与[API装配](../../examples/nestjs-reference/src/api/api.ts)。本章观察同步JSON请求；没有声明覆盖异步Servlet、错误重分发、流响应或生产鉴权。

## 知识点

### ch21-kp01 · Servlet与Filter的外层边界

#### 1. 一句话定义

Servlet是Java Web请求处理规范的一部分，Filter可以在进入Servlet前后处理请求与响应，Spring MVC的DispatcherServlet位于这条链内。

#### 2. 为什么需要它

某个请求没有进入Controller，可能已被外层Filter拒绝。若只在Controller打印日志，就会误以为请求没有到服务器。跨路由的关联ID、基础请求处理也需要比单个方法更外层的入口。

#### 3. 生活类比

园区门口先登记，之后才进入某个服务窗口。窗口没接到访客，并不表示园区门口没人来过。Filter像门口登记，MVC路由像找窗口；回程也经过门口，所以存在before/after包裹结构。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest Middleware可在路由处理前执行通用逻辑，因此与Filter有部分目的相似。Servlet dispatch类型、Filter注册顺序和Java线程模型并不等同于Express Middleware。Node调用next后同步返回也不表示HTTP响应已发送，所以Nest样例使用response的finish事件记录结束。

#### 5. 两端代码对照

Java使用OncePerRequestFilter，通过FilterRegistrationBean设置order=-200，记录filter.before并在finally记录filter.after。Nest app.use记录middleware.before，在response.finish记录middleware.finish。两端都由服务端生成UUID请求ID，不直接信任用户输入；轨迹最多保留100条，仅供教学读取。

#### 6. 原理与流程图

```text
外层Filter.before → 下一个Filter/Servlet → 返回或抛异常 → finally Filter.after
Nest middleware  → next()启动后续处理   → 响应finish事件
```

Java的finally代表当前filter调用栈退出，不等同于客户端已经完整收到响应。Nest finish表示服务器已完成向底层流提交响应，不证明客户端读取成功。

#### 7. 类比边界与错误直觉

OncePerRequestFilter的名字不能被理解为任意异步/错误dispatch都绝对只运行一次，它有分发类型与跳过规则。Filter既可能不是Spring Bean，也可能由Spring注册；“实现Filter接口”与“已经被容器注册”是两回事。

#### 8. 企业应用场景与选型

请求关联、大小限制与适配协议的通用处理可放在外层。需要知道具体Handler方法的规则通常适合MVC层。不要在Filter里重复解析并消耗请求体而不做缓存包装，否则Controller可能读不到正文；本例轨迹只记录事件。

#### 9. 常见错误与排查

事件出现两遍时检查同一个Filter是否既作为Servlet Filter注册，又被添加到Security链。漏记录after时检查finally及早返回。轨迹混杂时检查是否用了全局单一事件数组；本例按请求ID分离，并在take时移除。

#### 10. 实操练习与验收

运行WebApiTest的链路测试，观察合法GET的第一项filter.before与最后一项filter.after。向tasks发送空对象，确认未进入Controller仍有外层结束记录。不能据这个同步测试声称流式响应或异步dispatch已验证。

### ch21-kp02 · MVC Interceptor与参数校验的位置

#### 1. 一句话定义

HandlerInterceptor在MVC识别Handler后参与调用前后处理，与位于Servlet外层的Filter具有不同上下文和异常边界。

#### 2. 为什么需要它

想读取Handler方法元数据时，外层Filter可能还不知道最终匹配结果。另一方面，参数解析或校验失败会让Controller根本不运行；排查必须区分“已选中Handler”和“业务方法已经执行”。

#### 3. 生活类比

已经找到具体窗口后，窗口助理检查办理资格和表单，再交给工作人员。到达助理不意味着材料通过；处理后的回访也只适用于实际走到相应阶段的请求。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest Guard在Interceptor之前决定能否进入后续处理，Interceptor包装handler返回的Observable，Pipe转换与校验参数。Spring MVC Interceptor不等于Nest Interceptor；MVC postHandle、afterCompletion也不能机械替换RxJS map/finalize。二者的名字相近，但返回值和异常处理机制不同。

#### 5. 两端代码对照

Java addInterceptors登记preHandle、postHandle和afterCompletion事件。Nest TraceGuard记录guard，TraceInterceptor在next.handle前记录before并用finalize记录结束；只有tasks列表方法额外记录controller事件。完整测试分别在[WebApiTest](../../examples/spring-web-lab/src/test/java/dev/javafornestjs/web/WebApiTest.java)和[Nest api.test](../../examples/nestjs-reference/src/api/api.test.ts)。

#### 6. 原理与流程图

```text
Spring: Handler已选中 → preHandle → 参数绑定/校验 → Controller
        → 返回值处理 → postHandle（正常路径）→ afterCompletion
Nest:   Middleware → Guard → Interceptor.before → Pipe → Controller
        → Observable结束/错误 → Interceptor.finalize → response.finish
```

这是本例正常路径和局部失败的教学图。Spring REST响应序列化与postHandle的关系使postHandle不适合任意重写响应体；不要把它当Nest返回流变换器。

#### 7. 类比边界与错误直觉

afterCompletion不意味着所有情况都先运行postHandle；抛错或被提前拒绝时路径会变化。Nest finalize是流终止清理钩子，不是专门错误处理。拦截器可以观察调用，但不自动提供数据库事务或幂等。

#### 8. 企业应用场景与选型

Handler相关审计、方法元数据检查可放MVC Interceptor。返回对象统一包装可以考虑适当的ResponseBodyAdvice；不要在postHandle里读写已提交响应。Nest返回流变换与清理应分别选择map、catchError、finalize，并考虑异常过滤器最终表示。

#### 9. 常见错误与排查

合法请求有Controller事件、非法输入没有时，先检查参数校验；并非一定是路由丢失。日志顺序与预期不同时检查注册顺序、全局/局部拦截器和是否为异常路径，不要通过手工排序日志“修正”真实行为。

#### 10. 实操练习与验收

合法GET /tasks在Java应得到8项事件，在Nest得到6项事件，具体列表由测试断言。Java非法输入测试要求没有controller但包含interceptor.after。增加Nest非法输入轨迹负例时同样应从真实响应读取请求ID；不复用另一个请求的轨迹。

### ch21-kp03 · Security Filter Chain与权限边界

#### 1. 一句话定义

Spring Security通过Servlet过滤器代理进入选中的SecurityFilterChain，在MVC之前执行安全相关处理，链内过滤器顺序与Servlet外层顺序是两个层次。

#### 2. 为什么需要它

认证失败可能发生在ControllerAdvice可见范围之外。仅在MVC Interceptor加“是否登录”判断，容易遗漏静态资源、错误分发或不同入口；需要理解框架安全边界再实现JWT与RBAC。

#### 3. 生活类比

园区门口登记后，还要经过安全检查通道，才到业务窗口。安全通道内各检查站有自己的顺序。把某个检查站同时放在园区门口和安全通道里，会检查两次。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest常以Guard表达请求准入，Spring Security过滤器链承担更广泛的认证、安全上下文与异常转换工作。不能认为“Guard迁为HandlerInterceptor”就完成安全迁移，也不能把permitAll样例等同于权限保护。CSRF、Session与Token生命周期需根据实际客户端和威胁模型设计。

#### 5. 两端代码对照

Java本例创建一个仅用于轨迹的内部OncePerRequestFilter，在AnonymousAuthenticationFilter之前加入Security链；它不作为独立Servlet Bean注册。配置stateless、关闭CSRF、permitAll仅适用于当前环回教学API。Nest TraceGuard总返回true，也只用于观察顺序，未实现认证。

#### 6. 原理与流程图

```text
Servlet外层Filter(-200)
  → Security代理（默认顺序约-100）→ 链内security.before
    → MVC pre/controller/post/after
  ← security.after
← filter.after
```

链内addFilterBefore的相对位置并不修改Servlet注册order。多条SecurityFilterChain时需分别理解匹配与顺序，本实验只有一条，不提供多链匹配证据。

#### 7. 类比边界与错误直觉

stateless不代表系统没有状态，令牌吊销、刷新、限流都可能依赖服务端状态。关闭CSRF不是所有API的通用最佳实践；使用Cookie承载认证的浏览器接口必须重新评估。请求只绑定环回地址也不等于拥有授权模型。

#### 8. 企业应用场景与选型

安全主线应统一认证入口、授权策略和失败响应，再用直接HTTP负例验证绕过页面仍被拒绝。P4将独立实现安全矩阵，本章选择可观测的开放链避免提前假称JWT/RBAC已完成。

#### 9. 常见错误与排查

Filter跑两次先检查重复注册。401/403没有被ControllerAdvice改写时，检查Security的AuthenticationEntryPoint和AccessDeniedHandler，而不是无休止扩大全局异常捕获。错误分发再次进安全链时应核查dispatch策略和具体路径。

#### 10. 实操练习与验收

核对测试中的security.before位于MVC事件之前，security.after位于之后。指出这只证明过滤器经过了链，不证明用户身份受保护。尝试把内部Filter额外注册为Bean并观察重复事件属于手动故障实验，恢复后verify才可提交。

## 章末验收与审查记录

Java真实HTTP链路及Nest每请求轨迹测试已通过；本章draft，尚无独立审查结论。官方参考：[Servlet Filter](https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0)、[MVC拦截器](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html)、[Spring Security架构](https://docs.spring.io/spring-security/reference/servlet/architecture.html)、[Nest生命周期](https://docs.nestjs.com/faq/request-lifecycle)。
