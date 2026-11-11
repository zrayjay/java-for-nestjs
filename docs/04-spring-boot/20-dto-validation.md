---
title: '20 · DTO与参数校验'
description: 验证JSON转换、嵌套对象、未知字段和校验分组的迁移边界。
chapter_id: '20'
teaching_status: in_review
baseline: Java21 / Spring Boot3.5.16 / Nest11.2.9 / Node22.20.0
---

# 20 · DTO与参数校验

## 章节范围与先修

先完成第19章HTTP输入契约。Java完整[Tasks DTO](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/Tasks.java)、[JSON策略](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/JsonPolicy.java)与[Nest DTO/Pipe](../../examples/nestjs-reference/src/api/api.ts)共同限定tasks输入。完整命令见[README](../../examples/spring-web-lab/README.md)。本例限制ASCII title，目的是可验证的跨语言契约，生产国际化校验需另定规范。

## 知识点

### ch20-kp01 · 类型转换与约束校验是两道关

#### 1. 一句话定义

DTO表达边界输入结构，反序列化负责把JSON转为对象，校验负责判断对象是否满足约束，两者不能互相替代。

#### 2. 为什么需要它

在TS里写priority:number不会让网络字符串变非法，Java写BigDecimal也不意味着任意JSON都应该自动转成数值。迁移时必须同时审查转换策略和约束，否则两端看似有同名DTO却接受不同输入。

#### 3. 生活类比

录入员先把表单录成档案，再由审核员判断是否合规。录入阶段把“是”误录为1，审核员看到1就可能放行。必须明确哪些转换允许，不能只依靠最后范围校验纠正所有歧义。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java Bean Validation通过运行时约束注解工作，MVC在参数上启用相应验证。Nest ValidationPipe结合class-transformer和class-validator处理DTO；TS interface运行时消失，不能当作DTO元数据来源。transform:true不等于开启所有隐式转换，本例明确enableImplicitConversion:false。

#### 5. 两端代码对照

Java PriorityRequest采用BigDecimal，范围1~5，再用AssertTrue检查stripTrailingZeros后的scale是否不大于0。这样JSON数字1.0允许而1.5拒绝。Jackson策略拒绝数值字段来自字符串/布尔值、title来自数字/布尔值。Nest priority采用IsInt/Min/Max，JS解析1和1.0都为整数值，保持共同语义。

```json
{"title":"Learn Java","details":{"priority":1.0}}
```

它是合法输入；将1.0换成`"1"`或1.5则非法。JavaScript会把1.0000000000000001舍入为1，单靠IsInt会与Java精确十进制验证产生差异，因此Nest另用[原始输入边界](../../examples/nestjs-reference/src/api/input-boundary.ts)读取Node22.20的JSON.parse reviver context.source，按数字token的系数与指数检查其精确值是否为1~5整数。原始body由Nest rawBody选项保留，在DTO转换前检查；1.0000000000000001与0.99999999999999999均应400，10e-1则合法。完整类及配置均在源码链接中，不应只复制约束而漏掉转换前检查。这是本例小整数契约的专用检查，不是通用金额或任意精度JSON库。

#### 6. 原理与流程图

```text
原始字节 → JSON语法解析 → 类型转换/对象构建 → 约束检查 → Controller
                失败400              失败400          失败400
```

同为400不意味着失败发生在相同阶段。Java JSON解析异常与方法参数校验异常需要分别映射；Nest Pipe错误再交给异常过滤器。

#### 7. 类比边界与错误直觉

DTO不是权限规则，也不是数据库唯一约束。输入格式合法但资源不存在仍可能404；用户无权操作即使DTO合法也必须拒绝。BigDecimal这里只为定义数值契约，不说明JS number能够无损表示任意十进制金额。

#### 8. 企业应用场景与选型

对外请求应使用专用DTO，避免把数据库字段全量暴露。金额、ID和时间戳应按精度及语言边界选JSON表示；不能见到number就都迁为double。校验错误的用户展示和内部日志可分开，本例只返回稳定代码。

#### 9. 常见错误与排查

字符串数字被接受时查Jackson coercion或class-transformer选项。TS DTO没有装饰器且只使用interface时查运行时元数据。不要在Controller里先强转再校验，强转可能丢失原始类型信息。JSON语法错误应先排查客户端正文而非数据库。

#### 10. 实操练习与验收

运行两端测试确认数字1.0通过、1.5失败、字符串1失败、数字title失败。说明每种输入应在哪一关被拒绝。修改一个转换配置观察差异，恢复后重新跑共同契约，避免“本机能收数据”就视作迁移正确。

### ch20-kp02 · 嵌套校验与未知字段

#### 1. 一句话定义

嵌套校验需要显式遍历子对象，同时父对象的非空、子对象的类型及未知字段策略都要独立定义。

#### 2. 为什么需要它

只在priority上加约束，可能仍允许整个details缺失；只验证details存在，可能未验证里面的值。未知字段还可能携带权限相关输入，不能因为服务当前没使用就默认永远安全。

#### 3. 生活类比

申请表附带子表：主表要检查子表确实提交，子表再检查每一项。盖了“附件齐全”的章不表示附件内容正确。未登记的附加页是否拒收也需要明文规则。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java父字段使用NotNull配合Valid递归验证。Nest用IsObject、ValidateNested和Type把子对象转成可识别DTO，再遍历约束。Nest whitelist与forbidNonWhitelisted负责DTO层的字段规则；class-transformer可能在它们之前移除constructor或__proto__等保留键，因此原始JSON边界还会在转换前检查各层允许键，避免把“先丢弃再成功”误称为拒绝。Spring本例显式开启Jackson FAIL_ON_UNKNOWN_PROPERTIES，而不是把这一行为说成所有Spring默认值。

#### 5. 两端代码对照

Java TaskWriteRequest中details带NotNull/Valid；Nest TaskWrite的details带IsObject、ValidateNested、Type。Java用于检查整数性的isIntegral方法带JsonIgnore，JSON策略又开启FAIL_ON_IGNORED_PROPERTIES，因此客户端注入`integral:true`同样拒绝。

```json
{"title":"Learn","details":{"priority":1,"integral":true}}
```

两端均400；Nest没有暴露integral DTO字段，也由未知字段规则拒绝。

#### 6. 原理与流程图

```text
TaskWrite → title约束
          → details必须存在且形状正确
              → 转换/构造子DTO → priority约束
          → 各层未知字段检查
```

递归不代表无限深层结构天然安全；生产还需要请求体大小、深度及资源限制。本例只验证一层固定结构。

#### 7. 类比边界与错误直觉

NotNull不等于非空字符串，Valid本身也不是“必须存在”。去除未知字段和拒绝未知字段是不同契约；本例选择拒绝，不能把Nest whitelist默认裁剪后200称为等价。DTO校验不能代替防止SQL注入的参数绑定。

#### 8. 企业应用场景与选型

订单行、收货地址等组合输入需要明确每层约束。对外API拒绝未知字段能及早发现拼写错误，但对宽松兼容要求高的客户端可能需要版本策略。应根据契约选行为，不因某个框架默认值省去设计。

#### 9. 常见错误与排查

子字段约束不运行时检查Valid/ValidateNested、Type元数据和真实输入是否已经被转换。null通过时检查父字段NotNull或IsObject。若辅助计算属性能从JSON写入，检查序列化可见性与忽略属性策略，不能只靠属性名称“不像业务字段”。

#### 10. 实操练习与验收

验证空对象、details:null、priority:0、未知根字段及未知子字段都被拒绝。记录返回体仅包含code，不带堆栈和原始输入。尝试删掉父字段非空校验，解释为什么仅递归注解不能代表完整约束，完成后恢复。

### ch20-kp03 · 草稿与发布的校验分组

#### 1. 一句话定义

校验分组根据当前操作选择一组约束，让同一结构在草稿与发布等场景下具有不同的完整性要求。

#### 2. 为什么需要它

草稿允许空标题而发布要求合法标题时，单一规则可能迫使用户提前填写或让发布漏检。分组解决约束选择，但不会自动完成状态迁移、授权或业务唯一性判断。

#### 3. 生活类比

内部草稿可以留空，正式送审必须填写完整。两次检查使用同一份表格的不同清单；清单不同不表示基础材料类型可以随意改变，比如标题仍必须是字符串。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java本例Draft/Publish继承Default，把基础约束纳入显式分组；Publish另加标题模式。Nest基础装饰器always:true，发布模式用groups:['publish']。全局Pipe仅启用shared组，路由Body Pipe再选draft/publish，避免全局默认校验提前执行发布约束。二者不是一套可机械复制的分组继承模型。

#### 5. 两端代码对照

Java [LabController](../../examples/spring-web-lab/src/main/java/dev/javafornestjs/web/LabController.java)在请求体参数选择Validated组。Nest API中两个Body Pipe显式选择groups。共同输入`{"title":""}`发往draft返回204，发往publish返回400；合法Learn发布204。两个路由只演示校验，不创建或发布实际Task。

#### 6. 原理与流程图

```text
同一JSON → 基础类型/长度约束
        → draft：允许空标题 → 204
        → publish：追加标题模式 → 不符合则400
```

Nest Pipe有先后执行顺序，方法上的宽松规则不能“撤销”之前全局Pipe已经抛出的异常。因此先审查全局层的规则，再讨论局部分组。

#### 7. 类比边界与错误直觉

分组不是自动状态机；用户不能通过提交group字段选择跳过规则，本例分组由服务端路由决定。Java Default组是否参与取决于调用和继承，Nest无组约束的默认选择也要查所用validator版本，不能推断两端行为相同。

#### 8. 企业应用场景与选型

操作差异很大时可拆不同请求DTO，让API文档也清楚表达。约束差异小、字段相同且团队熟悉分组时可使用分组。不要把大量互斥业务条件堆成难以推断的组组合；复杂发布规则适合显式服务策略。

#### 9. 常见错误与排查

草稿仍报标题模式错误时检查全局Pipe是否默认执行了所有约束。基本类型约束突然不执行时检查Java Default继承或Nest always配置。Swagger Schema不必然表达运行时所有分组语义，应核对真实路由测试而非只看文档。

#### 10. 实操练习与验收

运行空标题draft204/publish400及合法发布204三条测试。再设计标题为数字的负例，要求基础类型规则对两组都有效。解释为什么“HTTP204”仅证明验证通过，不能声称业务发布完成。

## 章末验收与审查记录

本机新增Java模块14项、Nest全量27项与扩展打包共同HTTP契约通过；两轴增量独立审查及Windows/Linux×Temurin21/25修订CI通过。当前in_review，待用户验收，详见[审查归档](../reviews/P3-01.md)与[实施证据](../progress/P3-01.md)。本章不据此宣称SQL、生产认证或读者效果完成。

官方参考：[Jakarta Validation规范](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)、[Spring MVC验证](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)、[Nest Validation](https://docs.nestjs.com/techniques/validation)、[class-validator分组](https://github.com/typestack/class-validator#validation-groups)。
