---
title: '31 · RBAC权限系统'
description: 分开验证角色权限、界面能力与部门/本人数据范围，拒绝直接请求越权。
chapter_id: '31'
teaching_status: draft
baseline: Java21 / Boot3.5.16 / MySQL8.4.7 / Nest11.2.9 / Prisma7.10.1
---

# 31 · RBAC权限系统

## 章节范围与先修

先完成30章身份与会话。完整[权限读取/角色分配](../../examples/security-lab/src/main/java/dev/javafornestjs/security/AuthService.java)、[带范围SQL](../../examples/security-lab/src/main/java/dev/javafornestjs/security/mapper/SecuredTaskMapper.java)、[Java Repository](../../examples/security-lab/src/main/java/dev/javafornestjs/security/PolicyTaskRepository.java)、[Node策略](../../examples/nestjs-security-reference/src/tasks.ts)和[安全表结构](../../examples/security-lab/src/main/resources/db/security-migration/V1__identity_and_sessions.sql)构成同题。当前实现部门范围，不是多租户系统；Vue管理界面留到P6，本章提供能力元数据与直接HTTP拒绝证据。

## 知识点

### ch31-kp01 · 用户、角色与权限图

#### 1. 一句话定义

RBAC通过用户到角色、角色到权限的关系分配动作能力，服务端根据当前主体的实际权限判定，而不是相信客户端的角色标签。

#### 2. 为什么需要它

把isAdmin布尔值散落在前后端，会让角色变化与具体动作规则难以维护。角色是便于组织授权的集合，权限才是动作检查的明确输入；角色名字本身没有魔法。

#### 3. 生活类比

员工被编入岗位组，每个岗位组拥有办理事项清单。叫“管理员”的组若没有某项办理权，也不能凭名称通过；员工在表单上自填“管理员”更不构成授权。更改岗位组应影响后续办理，而不是继续使用过期清单。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring GrantedAuthority与Nest Guard可承载权限判断，但两者不会替你自动建立业务角色表。本例Java读取关系并转换authority，Node从关系模型生成权限Set。JWT不携带被永久信任的旧角色列表；两端每次认证读取当前DB权限，不用重新签Token才能撤销某项动作。

#### 5. 两端代码对照

sec_users→sec_user_roles→sec_roles→sec_role_permissions→sec_permissions构成关系。权限例子为task:read:department、task:write:own、task:read:any、task:write:any、role:manage。member、reader、admin只是测试角色的组合；admin也必须实际关联相应permission code。

```text
member → 部门读 + 本人写
reader → 部门读
admin  → 全局读 + 全局写 + 管理角色分配
```

管理员PUT /admin/users/{id}/roles在事务中检查目标用户和已登记角色，拒绝未知/重复角色；参数化写入和审计一起提交。

#### 6. 原理与流程图

```text
用户身份 → 读当前角色关系 → 合并权限去重 → 当前请求权限集合
管理员改角色 → DB事务提交 → 下一请求读新集合 → 原Access也受新规则约束
```

角色授权变化与凭据版本失效分开：前者本例保持会话但改变动作权限，后者使旧会话凭据失效。把两个机制混成“每次改角色都重新登录”会遮住各自目标。

#### 7. 类比边界与错误直觉

角色不存在天然继承或自动超级管理员；本例也没有租户字段。把部门当租户会误导数据隔离设计。不同系统的权限代码约定可不同，但前后端、审计和测试必须使用同一业务词汇。

#### 8. 企业应用场景与选型

按动作和资源范围设计权限，避免一个全局写权限包办所有业务场景。复杂属性规则可逐步引入策略模型，但不要把所有判断变成难以追踪的散乱if。角色管理本身需要专门权限和审计，不能对普通用户开放。

#### 9. 常见错误与排查

角色已分配却无权，检查连接表、权限代码和是否错误添加ROLE_/SCOPE_前缀。角色已撤销仍能写，检查是否采信旧JWT角色或长期缓存权限。未知角色失败后关系丢失，检查事务或是否在验证前已删除旧分配。

#### 10. 实操练习与验收

将Alice从member改reader，继续使用旧Access直接POST任务，应403；/me仍可成功且权限已更新。普通reader不能给自己分配admin。提交未知角色999或重复角色时400，原角色关系不被破坏；不能只断言HTTP失败而不核对数据库。

### ch31-kp02 · 按钮、接口与能力元数据

#### 1. 一句话定义

前端按钮权限表达当前可见/可操作能力，接口与服务端规则才决定请求是否真正获准，隐藏按钮不是安全边界。

#### 2. 为什么需要它

用户可以直接发HTTP请求，旧页面也可能保留已撤销权限的按钮。若服务端只依赖前端状态，任何绕过页面或过期界面都可能产生越权操作。

#### 3. 生活类比

服务大厅指示牌展示可办理事项，有助于减少误排队；真正办理仍要核对资格。把指示牌摘掉不能阻止拿着请求直接走到柜台，指示牌没来得及更新也不应该让无资格者办理成功。

#### 4. Node.js / TypeScript / NestJS 技术对照

两端/me都输出当前权限字符串，可供任意前端消费；本章没有声称已交付Vue按钮。Spring链/Repository与Nest前置授权/Guard/PolicyTasks分别验证直接请求。前端缓存权限影响体验，不应影响后端是否拒绝。

#### 5. 两端代码对照

Java AuthController.Me和Node SecurityController.me显式输出id/username/departmentId/permissions，不输出密码Hash、会话秘密或原始Refresh。基于该响应可计算界面能力，以下为消费合同的TS片段，不是已交付管理页面：

```ts
const canCreate = permissions.includes('task:write:own')
  || permissions.includes('task:write:any')
```

必须同时考虑全局写权限，否则admin可能被错误隐藏按钮。无论canCreate为何，接口仍重新验证当前身份与权限。

#### 6. 原理与流程图

```text
/me当前能力 → 前端显示/禁用按钮
点击或直接HTTP → 服务端当前权限 → 数据范围 → SQL
服务端403 → 客户端更新能力/提示，不把所有403当401无限刷新
```

前端按钮、路由菜单与接口动作可能不是一对一。例如列表菜单可见不表示可修改每一条记录，行级可写性仍取决于数据范围。

#### 7. 类比边界与错误直觉

任何localStorage中的isAdmin、角色名或用户自填字段都不构成授权。前端类型检查也不验证网络输入。即使按钮不渲染，服务端授权仍必须有独立负例；这里的元数据不是完整前端联调证据。

#### 8. 企业应用场景与选型

集中定义权限词汇，前端按能力渲染，后端按同一业务规则拒绝。权限变化后刷新/me并处理已打开页面的失权状态。不要为消除用户报错而让服务端放行旧按钮请求。

#### 9. 常见错误与排查

按钮缺失但接口允许，检查全局/本人权限分支和前端数据更新。按钮存在但403，先核对当前DB授权而非强制重发。401应走认证处理，403应走权限处理，跨范围404按资源不可用处理，避免自动重试扩大副作用。

#### 10. 实操练习与验收

reader的/me没有写权限，直接POST仍403且数据库没有新增任务；直接调用管理员角色接口也403。保留member/admin合法对照，避免“所有请求都拒绝”的实现也通过测试。真正页面与按钮联调在P6补充，不能提前标完。

### ch31-kp03 · 部门读、本人写与条件SQL

#### 1. 一句话定义

数据权限把授权范围放入数据选择和修改条件中，本例普通成员读本部门、写本人，只有相应全局权限才跨部门或跨所有者操作。

#### 2. 为什么需要它

“允许编辑任务”并不回答允许编辑哪条任务。先查一次权限再无条件按id修改，还可能在检查与写入之间产生竞态；先LIMIT再在应用内过滤，也会返回错误页或泄漏不该看到的记录。

#### 3. 生活类比

仓库领料单不仅写“可领料”，还写允许的仓库和领料人。发货时按同一限制选中货物，不先发一车再挑出不该给的货。修改领料单和实际出库之间若没有约束，也可能换走别人的货。

#### 4. Node.js / TypeScript / NestJS 技术对照

MyBatis显式SQL与Prisma结构化where都要表达范围。Java在Mapper UPDATE/DELETE中检查owner，Node在updateMany/deleteMany中带ownerId；不是查出行后只在Controller比对一次。列表按范围WHERE之后ORDER/LIMIT，而非截取后过滤。两端ORM不同，授权目标不能因此变化。

#### 5. 两端代码对照

Java SecuredTaskMapper修改条件为：

```sql
WHERE id=#{id} AND (#{any}=TRUE OR owner_id=#{owner})
```

any与owner来自已验证主体/当前权限，不来自请求体。Node PolicyTasks.writable对全局权限返回{id}，否则返回{id,ownerId:user.id}，同样原子地限制修改。创建总把owner绑定当前主体，DTO不允许客户端额外传ownerId。本实验以P3 ownerId映射用户ID，不意味着所有领域中的所有者都必须是用户。

#### 6. 原理与流程图

```text
当前主体/权限 → 允许动作？ → 生成范围谓词
读：范围WHERE → 排序 → LIMIT
写：id + 范围WHERE → 影响行数0则404 → 同事务审计 → commit
```

权限集合是请求入口时的快照，记录范围在SQL执行时施加；本例不承诺撤权取消已经开始的请求。写操作和审计原子提交，审计故障应回滚任务，而不是留下未审计写入。

#### 7. 类比边界与错误直觉

部门隔离不等于租户隔离。用404隐藏存在性不修复遗漏的WHERE。读同部门不表示能修改同部门所有人的记录。缓存引入后也不能把过期授权结论作为依据，第32章将单独验证安全边界与缓存一致性。

#### 8. 企业应用场景与选型

把数据范围封装在可复用边界，覆盖列表、详情、更新、删除与导出等入口。若后台任务或管理员工具需要绕过范围，应有明确服务身份、权限和审计，不能直接提供一个用户可控的ignoreScope参数。

#### 9. 常见错误与排查

跨部门能看见数据，检查所有查询路径和关联条件；limit=1返回空但本部门有行，检查是否过滤太晚。修改未报错但影响0行时核对是否应404。日志actor与owner不一致时检查请求上下文，不先把权限放宽为全局。

#### 10. 实操练习与验收

先创建Bob的较小id，再创建Alice任务；Alice limit1必须得到本部门任务，而不是取到Bob后过滤为空。Alice不能读/改/删Bob，Erin同部门可读Alice但不能改删；admin具备全局权限时可操作。合法本人写与审计故障回滚都要保留正反例，并核对数据库最终状态。

## 章末验收与审查记录

本章draft。已实现RBAC与部门范围、能力元数据、实时撤权、直接HTTP拒绝及SQL审计回滚。真实执行按[进度](../progress/P4-01.md)逐提交记录，未声称租户、前端管理页面或G4已完成。官方参考：[Spring授权架构](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/architecture.html)、[请求授权](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html)、[Nest Authorization](https://docs.nestjs.com/security/authorization)、[OWASP授权指南](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)。
