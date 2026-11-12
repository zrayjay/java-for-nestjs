---
title: '29 · Spring Security'
description: 用真实Bearer请求理解安全链、认证身份、上下文隔离与授权边界。
chapter_id: '29'
teaching_status: in_review
baseline: Java21 / Boot3.5.16 / Spring Security6.5.11 / Nest11.2.9 / jose6.2.17
---

# 29 · Spring Security

## 章节范围与先修

先完成21~22章和G3练习。完整[安全配置](../../examples/security-lab/src/main/java/dev/javafornestjs/security/SecurityConfiguration.java)、[认证服务](../../examples/security-lab/src/main/java/dev/javafornestjs/security/AuthService.java)、[上下文](../../examples/security-lab/src/main/java/dev/javafornestjs/security/AuthContext.java)与[Nest安全Feature](../../examples/nestjs-security-reference/src/app.ts)运行于独立安全入口。P3默认开放入口继续用于请求链教学，不能据其permitAll声称认证完成。运行步骤见[Java说明](../../examples/security-lab/README.md)和[Node说明](../../examples/nestjs-security-reference/README.md)。

## 知识点

### ch29-kp01 · 安全链先于业务入口

#### 1. 一句话定义

Spring Security在所选SecurityFilterChain中处理认证与授权，本例通过Resource Server验证Bearer JWT，再决定是否允许请求进入Controller。

#### 2. 为什么需要它

仅在页面隐藏按钮或Controller里临时检查一个布尔值，无法形成统一入口规则。必须知道无凭据、无权限以及输入不合法分别在哪一层被拒绝，避免把框架默认生成用户误当成已完成业务认证。

#### 3. 生活类比

园区先核验通行证，再判断能进哪个楼，最后才由窗口检查申请表。核验通行证是认证，允许进入楼层是授权，表单校验是业务边界检查。有效通行证不代表所有楼层通行，申请表写得正确也不能绕过入口检查。

#### 4. Node.js / TypeScript / NestJS 技术对照

Nest可通过Passport策略与Guard组合认证。本例实际执行的是jose加前置认证中间件及Guard，没有测试Passport包内部机制。Spring的安全过滤器链不等同于一个Nest Guard：它还管理安全上下文和失败响应。两端都把认证/入口权限放在请求体解析或业务校验之前；数据范围继续由数据层执行。

#### 5. 两端代码对照

Java完整SecurityConfiguration显式使用oauth2ResourceServer，并固定RS256 Decoder及认证转换器。规则片段来自protectedApi：

```java
.requestMatchers(HttpMethod.GET, "/tasks", "/tasks/**")
    .hasAnyAuthority("task:read:department", "task:read:any")
```

Nest完整app.ts在setup中先验证Authorization，再调用SecurityContext.authorize，之后还通过真实CanActivate Guard检查路由。登录本例由AuthService核对BCrypt后发行令牌，不声称使用了Spring默认表单登录或DaoAuthenticationProvider。

#### 6. 原理与流程图

```text
请求 → 安全链读取Bearer → 验签/声明/服务端会话检查 → Authentication
     → URL/方法权限 → 参数校验 → Controller → 带数据范围的Repository
```

认证失败不需要先执行Controller。Node中间件前置是为了让未登录的非法JSON请求也先被认证拒绝，Guard与数据层检查则保留各自的框架和业务边界。

#### 7. 类比边界与错误直觉

认证不是只解码JWT载荷，permitAll也不表示带着无效Bearer的请求会跳过验证。默认匿名身份不是登录用户。代码里出现SecurityFilterChain不证明规则生效，必须确认选中了哪条链、实际启动了哪个应用以及是否有合法/非法请求对照。

#### 8. 企业应用场景与选型

统一认证入口、默认拒绝未知业务路由，为不同API方法声明明确权限。浏览器Cookie会话与纯Bearer API有不同威胁模型；本例选择独立协议来隔离机制，不把它当适用于所有系统的生产模板。多安全链匹配和网关信任边界需要另行设计。

#### 9. 常见错误与排查

先检查入口、模式和实际FilterChain，而不是先给所有Controller加permitAll。401发生在过滤器层时应查看AuthenticationEntryPoint；MVC ControllerAdvice不能兜底所有过滤器错误。不要靠开启默认生成密码或同时启用不需要的Basic认证“解决”业务登录。

#### 10. 实操练习与验收

执行SecurityApiTest与Node security.test：匿名/tasks拒绝，合法用户/me成功，reader直接POST拒绝。另发送未登录的非法正文，确认认证先于业务校验。解释P3开放实验仍可运行为何不表示安全模块退回开放模式。

基础题：区分401、403与表单400。迁移题：把Guard拒绝映射到安全链入口拒绝。思考题：有效证件为什么仍可能不能进楼？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#passwordsAreHashedAndOnlyExplicitBearerCredentialsAuthenticate' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

### ch29-kp02 · Authentication、Principal与上下文

#### 1. 一句话定义

Authentication表达本次认证结果及权限，Principal标识当前主体，SecurityContext把它们放在适当的执行上下文中，而不是放在全局可变用户变量里。

#### 2. 为什么需要它

并发请求可能属于不同用户。若一个全局currentUser被后来的请求覆盖，数据所有者、审计操作者和响应权限都可能串号；只测试单用户并发刷新无法证明跨身份隔离。

#### 3. 生活类比

每个服务窗口为当前访客保留独立工单，不把大厅白板上的最后一个名字当所有窗口的访客。工单记录身份和可办理事项，办理结束后清理。换窗口或把工作交给后台线程，不代表工单会自动正确传过去。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring Servlet模式通常在线程绑定的SecurityContext中取得身份，框架在请求结束清理。本例同步MVC业务没有声称自动验证了Async/自建线程传播。Node事件循环不能用同一全局变量区分交错Promise，本例用AsyncLocalStorage随异步链隔离请求。两者都不是“每个Provider都是当前用户”；Nest单例可共享，但用户状态不能作为其普通可变字段。

#### 5. 两端代码对照

Java AuthService.authenticate把已验证JWT映射为SecurityUser和当前数据库权限，认证对象不保留原始JWT作credentials：

```java
return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
```

AuthContext.current要求已认证且Principal类型正确。Node [SecurityContext](../../examples/nestjs-security-reference/src/context.ts)使用AsyncLocalStorage.run保存requestId/user；Controller与PolicyTasks读取同一请求状态。HTTP响应和数据库审计分别核对关联ID，不能只验证对象上有一个username字段。

#### 6. 原理与流程图

```text
认证JWT → 当前DB主体/权限 → 本请求上下文
       → Controller读取 → Repository按主体限制 → Audit记录同一主体/请求ID
请求结束 → 框架清理 / 异步资源作用域结束
```

JWT只携sub/sid/ver等发行合同，不信任客户端自己提交的部门或角色。权限更新后的下一次请求重新读取数据库，因此旧Access不自动保留旧权限。

#### 7. 类比边界与错误直觉

上下文能找到用户不代表该用户有权操作任意数据。ThreadLocal不能未经设计传给线程池任务，AsyncLocalStorage也不跨独立进程或消息系统自动传播。已开始请求采用入口身份快照，不声称角色撤销可以取消全部在途操作。

#### 8. 企业应用场景与选型

为审计、数据范围和业务授权提供统一主体访问器；后台任务显式携带经过设计的服务身份或业务参数，避免伪装成最近的HTTP用户。测试既检查角色判断，也检查数据owner与audit.actor是否正确。

#### 9. 常见错误与排查

随机越权或串审计先检查共享字段、异步上下文丢失及请求结束清理。不能通过“每次都重新登录”掩盖串号。直接调用服务时也应有拒绝对照，不只依赖页面或某一条Controller路由。

#### 10. 实操练习与验收

同时发Alice与Bob的创建请求，分别核对MySQL owner、audit.actor以及响应X-Request-Id；之后匿名/me仍拒绝。该新增两端回归以对应修订CI为准。能解释为什么同一个用户的两次刷新并发通过并不等于跨用户隔离已验证。

基础题：画出Authentication与SecurityContext的关系。迁移题：对照AsyncLocalStorage说明并发身份隔离。思考题：为什么ThreadLocal不能自动传播到所有异步任务？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#concurrentDifferentUsersKeepOwnershipAndAuditCorrelationIsolated' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

### ch29-kp03 · 认证、权限与资源范围的不同拒绝

#### 1. 一句话定义

认证决定你是谁，权限决定允许何种动作，数据范围决定动作能作用于哪些记录；它们需要分别执行和测试。

#### 2. 为什么需要它

有task:write权限的普通用户仍不能删除同事的任务。把所有失败统一成“没有登录”，会让客户端刷新令牌陷入循环，也会掩盖真正的数据越权问题。

#### 3. 生活类比

员工证有效、具备仓库出库资格和能够领走哪一个部门的货物，是三道不同限制。第一道通过不自动取消后两道；柜台可以对不属于你的货物统一说“无可办理记录”，避免透露库存存在性。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring URL权限由安全链执行，方法内部与Repository仍可防御性检查；Nest Guard与PolicyTasks分工相似但不是同一类API。本例GrantedAuthority使用数据库permission code，不采用默认SCOPE_或ROLE_字符串约定。两端返回401/403/404时强调相同业务语义，不把所有框架默认路由状态假称一致。

#### 5. 两端代码对照

Java [PolicyTaskRepository](../../examples/security-lab/src/main/java/dev/javafornestjs/security/PolicyTaskRepository.java)在每类操作前require权限，再调用[带范围Mapper](../../examples/security-lab/src/main/java/dev/javafornestjs/security/mapper/SecuredTaskMapper.java)。Node [PolicyTasks](../../examples/nestjs-security-reference/src/tasks.ts)同样把ownerId条件放入updateMany/deleteMany。401为未认证/无效凭据；403为动作权限不足；记录超出可见/可写范围返回404，避免直接暴露其存在。

#### 6. 原理与流程图

```text
无有效身份 → 401
身份有效但动作权限不足 → 403
动作允许但该记录不在数据范围 → 404
全部通过 → 条件SQL与审计 → 成功响应
```

异常位置也不同：安全链失败由入口/拒绝处理器生成JSON，MVC内AccessDenied由专用Advice处理；Node前置层与全局Filter都只输出稳定code，不回传数据库或验签细节。

#### 7. 类比边界与错误直觉

404隐藏存在性不替代授权。CORS不是用户权限，前端按钮状态也不是服务端规则。stateless仅说明不依赖HTTP Session，本例仍查询数据库会话族与权限，不是完全无状态JWT系统。

#### 8. 企业应用场景与选型

对外稳定错误代码，按业务决定是否隐藏记录存在性；权限规则既要有拒绝例，也要有合法对照。权限撤销后客户端应更新界面，而不是把403一律当401自动刷新重试。

#### 9. 常见错误与排查

有角色仍403时检查实际permission code、前缀和当前DB分配，不直接给用户超级管理员。跨部门能列出数据时检查WHERE是否先于LIMIT。合法GET却无法修改他人任务在本例是预期的部门读/本人写规则，不是权限系统随机故障。

#### 10. 实操练习与验收

验证匿名401、reader写403、跨部门读404、同部门非本人写404、admin按权限跨部门成功。直接发送请求，不依赖页面按钮。记录每个拒绝发生在认证、动作还是资源范围层，再到第31章核对数据库模型。

基础题：列出动作许可与记录范围的区别。迁移题：将Nest权限判断迁移为条件SQL。思考题：为什么403与隐藏存在性的404不能互相替代？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#departmentAndOwnershipRestrictionsApplyBeforeLimitAndWrites' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

## 章末验收与审查记录

本章in_review，独立[两轴审查](../reviews/P4-01-auth.md)已归档。凭据版本、缺声明及并发修订已在Security43170418413的Java21/25矩阵通过，每组Java19项及Node19项；后续合法写断言仍待对应提交CI；源码审查与完整课程验收不同，见[实施记录](../progress/P4-01.md)。官方参考：[Security架构](https://docs.spring.io/spring-security/reference/6.5/servlet/architecture.html)、[认证架构](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html)、[JWT Resource Server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)、[Nest Guards](https://docs.nestjs.com/guards)。
