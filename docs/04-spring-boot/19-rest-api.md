---
title: '19 · Controller / REST API'
description: 从真实HTTP理解路由、参数边界、CRUD状态与错误契约。
chapter_id: '19'
teaching_status: draft
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 19 · Controller / REST API

## 章节范围与先修

先掌握第18章构造器注入。本章以tasks资源拆开路由、输入绑定与响应契约。完整文件为[Java Controller](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/TasksController.java)、[Service](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/TaskService.java)、[仓库接口](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/TaskRepository.java)及[Nest API](../../examples/nestjs-reference/src/api/api.ts)。当前仓库是内存实现；Controller→Service→Repository在数据访问阶段替换为Mapper适配器，不把Map当成MySQL证据。

## 知识点

### ch19-kp01 · 路由与Controller边界

#### 1. 一句话定义

Controller把HTTP方法、路径、输入和输出映射到应用服务，Spring MVC由DispatcherServlet协调映射与调用。

#### 2. 为什么需要它

路由注解能让函数被HTTP调用，却不应让函数同时负责SQL、事务和所有业务规则。把边界留在Controller，可以让Service在不同入口复用，也方便分别验证协议与业务行为。

#### 3. 生活类比

Controller像服务窗口：识别办理事项，检查材料格式，把工作交给业务人员，再按规定返回回执。窗口不是档案库。窗口路线正确也不能保证背后的业务处理正确，因此直接调用Service和真实HTTP测试各有目的。

#### 4. Node.js / TypeScript / NestJS 技术对照

`@RestController`配合`@GetMapping`可对照Nest的`@Controller`和`@Get`。Spring MVC采用Servlet栈，不是把Express换成Java语法；本例也没有采用WebFlux。Spring类级RequestMapping和方法级映射共同确定路径，Nest前缀与装饰器组合具有相似目的，但底层匹配器和默认行为不同。

#### 5. 两端代码对照

Java TasksController注入TaskService，Nest TasksController也通过构造器注入TaskService。Java的find接收字符串id，经过规范整数解析后由服务查找；Nest同样显式解析，避免用`Number('')`得到0一类隐式转换。

```text
GET /tasks        → service.list → repository.list
GET /tasks/{id}   → service.find → repository.find
POST /tasks       → service.create → repository.create
PUT /tasks/{id}   → service.replace → repository.replace
DELETE /tasks/{id}→ service.delete → repository.delete
```

两端完整实现都位于本章链接的源文件，独立运行命令见[实验README](../../examples/spring-web-lab/README.md)。

#### 6. 原理与流程图

```text
HTTP → Servlet Filter → DispatcherServlet → HandlerMapping
     → 参数解析/反序列化/校验 → Controller → Service → Repository
     → 返回值处理/JSON序列化 → HTTP响应
```

此图省略安全过滤器和异常分支，完整链路见第21章。Controller Java对象存在，不表示HandlerMapping已经把它注册成路由；必须由正确的Web上下文识别。

#### 7. 类比边界与错误直觉

RestController是Controller加ResponseBody语义，不能因为返回String就认定是视图名。Nest装饰器不是Java注解的执行机制复刻。类路径上有Controller但不在扫描范围时，不会自动成为路由。同步Java方法也不意味着一台服务器一次只能处理一个请求。

#### 8. 企业应用场景与选型

简单资源CRUD适合MVC加服务分层。对外接口应稳定资源名和状态语义；跨多个仓库的业务一致性通常由服务层事务处理，不能通过Controller连续调用两个独立写入就假定原子性。流式或响应式需求需另行评估，不能只为“看起来异步”切换栈。

#### 9. 常见错误与排查

404先核对方法、路径、前缀、扫描包和实际启动物。不要对着仍在运行的旧进程测试新代码。直接new Controller成功不能证明Web绑定、Filter或序列化正确；本仓库同时保留MockMvc与实际随机端口HTTP验证。

#### 10. 实操练习与验收

运行共同契约验证，检查POST后GET、列表、替换和删除都实际经过HTTP。给Service添加一个只用于学习的断点，确认路由命中而非修改客户端“模拟返回”。能指出Controller、Service、Repository分别改变哪类规则；完成后恢复任何调试改动。

### ch19-kp02 · 路径、查询与请求体的显式契约

#### 1. 一句话定义

参数绑定把HTTP文本或JSON转为方法输入，但可接受值的范围仍需由明确的解析、校验及业务规则决定。

#### 2. 为什么需要它

两端框架都能把输入转成数字，但默认转换不保证等价。例如`01`、`1e1`、超int32值、空字符串可能被不同解析器接受。迁移时应先约定共同输入集合，再选择转换方式。

#### 3. 生活类比

路径参数像档案编号，查询参数像查阅条件，请求体像提交的表单。把表单里的数量放到档案编号位置会改变协议含义。仅能读成数字不表示它是合法编号，例如编号0在本例没有意义。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring的PathVariable、RequestParam、RequestBody分别对应Nest的Param、Query、Body。Java类型在运行时能参与参数转换，TS静态类型本身不会校验网络JSON；Nest仍需Pipe和DTO装饰器。两端本例均先读取id/limit字符串，再按相同的规范格式解析，避免依赖框架默认强转。

#### 5. 两端代码对照

完整Java与Nest文件都将id限定为正十进制int32：首位1~9，后续0~9，最大2147483647。limit默认20，合法范围1~100。priority在JSON中是1~5整数值；`1.0`与`1`数学值相同，都接受，字符串`"1"`拒绝。

```http
POST /tasks
Content-Type: application/json

{"title":"Learn Java","details":{"priority":2}}
```

JSON校验的嵌套细节见第20章；本章不把请求DTO等同于数据库实体。

#### 6. 原理与流程图

```text
路径/查询文本 → 规范格式检查 → 数值转换 → 范围检查 → 服务调用
JSON字节 → JSON解析 → DTO转换 → 字段/嵌套校验 → 服务调用
```

`/tasks/abc`在本例映射到find后返回400，`/unknown`没有资源路由而返回404。路径不合法与路由不存在不是同一种失败。

#### 7. 类比边界与错误直觉

`@RequestBody`不自动保证每个字段都合法，类型声明也不决定未知字段策略。本例明确拒绝未知字段，不能外推为所有Spring项目默认行为。重复查询参数、不同URL编码、Content-Type和Accept协商尚不在共同契约范围，不能据已通过的几类输入声称全部HTTP语义等价。

#### 8. 企业应用场景与选型

外部ID可用UUID或字符串；int32是本教学例的显式限制。分页上限保护资源，但默认20不是完整分页协议。本例列表按id排序并截断，不提供total、游标或快照一致性。生产接口应另行定义这些语义，并把边界加入契约测试。

#### 9. 常见错误与排查

字段总变成0时检查原始JSON、DTO字段名、默认值与转换选项。先检查服务是否真的未被调用，再看400来源是JSON解析还是Bean Validation。不要通过开启所有隐式转换来掩盖客户端错误；这样会让空值、布尔值和数字字符串悄悄改变含义。

#### 10. 实操练习与验收

确认`/tasks/0`、`/tasks/01`、`/tasks/2147483648`以及`limit=101`、`limit=1e1`返回400。确认默认limit请求可成功。把一个数字字段写成字符串，两端都应拒绝；修正客户端而不是删掉服务端规则。

### ch19-kp03 · 状态码、Location与错误表示

#### 1. 一句话定义

HTTP响应契约由状态、头部和响应体共同组成，仅返回相同JSON不足以证明接口兼容。

#### 2. 为什么需要它

前端可能根据201识别创建成功，根据Location访问新资源，根据204跳过解析响应体。服务迁移时如果只比较字段而忽略状态，会让客户端在“业务成功”之后仍报错。

#### 3. 生活类比

办理回执同时包含办理结果、查询地址和详细内容。结果写“已创建”但不给可用的资源地址会降低可用性；写“无内容”却塞入一段JSON会让接收方无所适从。类比不能替代HTTP标准对头部和状态的具体约束。

#### 4. Node.js / TypeScript / NestJS 技术对照

Spring常用ResponseEntity显式控制状态与头部。Nest POST默认201，其他普通返回通常200，可用HttpCode改变；直接操作响应对象可能绕过框架自动处理，本例只用passthrough设置Location。Spring与Nest未支持方法的默认响应通常分别为405和404，本课程不掩盖该差异。

#### 5. 两端代码对照

共同契约：创建201加`Location: /tasks/{id}`和资源体；查询/替换200；删除204且空体；资源不存在404加`{"code":"not_found"}`；输入非法400加`{"code":"invalid_request"}`。意外异常500仅返回`internal_error`。Java异常映射在[ApiErrors](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/ApiErrors.java)，Nest位于API文件的ExceptionFilter。

#### 6. 原理与流程图

```text
服务成功 → Controller选择状态/头/体 → 序列化
服务或参数失败 → MVC Advice / Nest Filter → 受控错误JSON
```

ControllerAdvice不是所有Servlet Filter异常的总捕手，Nest ExceptionFilter也不自动覆盖任意脱离请求上下文的后台Promise；异常作用域见第22章。

#### 7. 类比边界与错误直觉

PUT本例表示完整替换两个可写字段，不是“缺哪个就保留哪个”的PATCH。重复DELETE在状态上可先204后404；幂等性关注重复操作的预期服务端效果，不要求每次状态完全相同。简单错误码隐藏内部细节，却不代替服务端诊断与请求关联设计。

#### 8. 企业应用场景与选型

已有前端依赖错误码时，应把错误结构纳入版本化契约。不要在500响应中泄漏SQL、堆栈、令牌和配置。若采用Problem Details等标准格式，应明确迁移计划，而不是只改一个框架配置让客户端突然收到另一种结构。

#### 9. 常见错误与排查

客户端解析204失败时先检查是否仍强制调用response.json。创建变200时检查显式状态覆盖。相对Location在网关后可以由客户端按当前源解析；生产若改绝对地址应确认代理头和可信来源。异常总200通常来自把错误吞掉后返回普通对象。

#### 10. 实操练习与验收

共同验证器必须同时检查201、Location、精确资源字段、PUT后的值、204空体和删除后的404错误码。手动发PATCH观察两端默认差异，说明这不在当前共同契约内。生产若要求全部方法一致，必须新增显式处理及负例，不能修改报告声称已有覆盖。

## 章末验收与审查记录

已执行WebApiTest、Nest api.test和独立打包进程共同契约。本章draft，独立审查未完成。官方参考：[Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)、[MVC注解控制器](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)、[Nest Controllers](https://docs.nestjs.com/controllers)、[HTTP语义](https://www.rfc-editor.org/rfc/rfc9110)。
