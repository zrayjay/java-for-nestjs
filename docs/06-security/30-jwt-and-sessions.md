---
title: '30 · JWT与会话'
description: 验证Access签名声明、Refresh轮换重放、凭据版本和浏览器存储边界。
chapter_id: '30'
teaching_status: draft
baseline: Java21 / Boot3.5.16 / Security6.5.11选定Nimbus9.37.4 / Nest11.2.9 / jose6.2.17
---

# 30 · JWT与会话

## 章节范围与先修

先完成29章认证上下文和27章事务。完整[Java认证](../../examples/security-lab/src/main/java/dev/javafornestjs/security/AuthService.java)、[密钥读取](../../examples/security-lab/src/main/java/dev/javafornestjs/security/TokenKeys.java)、[Node认证](../../examples/nestjs-security-reference/src/auth.ts)与[Node密钥](../../examples/nestjs-security-reference/src/keys.ts)为同题实现。数据库结构由[V1](../../examples/security-lab/src/main/resources/db/security-migration/V1__identity_and_sessions.sql)及[V2版本绑定](../../examples/security-lab/src/main/resources/db/security-migration/V2__bind_session_credential_version.sql)维护，不修改已应用脚本。

## 知识点

### ch30-kp01 · 验签、接收方和时间合同

#### 1. 一句话定义

本例Access是RS256签名的JWT，接收方必须验证算法、签名、发行方、受众、时间及所需声明，再核对服务端会话，解码载荷不等于认证。

#### 2. 为什么需要它

攻击者可以任意改Base64URL里的sub或exp。只读取这些字段就信任用户，会把未认证输入当权限。签名正确但发给另一应用、已过期或缺少当前合同要求的声明，也不能接受。

#### 3. 生活类比

通行证上的文字人人可读，但要检查盖章、适用园区、持有人和有效时间。另一园区签发的有效票不一定适用于这里。签名像可验证的章，不是把票据内容藏起来的信封。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java使用BOM管理的Nimbus Encoder/Decoder与Spring Validator，Node使用jose；不手工实现RSA或JWT拼接验证。Java audience可序列化为数组，Node本例发行字符串，两者均是JWT允许的表示，语义按受众匹配而非字节相同检查。JWT库不自动知道本业务的会话族、凭据版本和当前权限，这些仍需服务端规则。

#### 5. 两端代码对照

Java Decoder限制SignatureAlgorithm.RS256，Node jwtVerify限制algorithms:['RS256']，并指定issuer/audience/currentDate。两端选择iss/aud/exp/iat/nbf/sub/jti/sid/ver为必需合同；这不是说RFC要求所有JWT都有九项。本例sub为用户ID、sid为会话族、ver为凭据版本，角色权限不从客户端载荷直接采信。

```text
Access: 签名JWT，默认最多300秒
Refresh: 32随机字节的Base64URL不透明值，不是JWT
```

测试使用真实签名但逐项删声明、错误issuer/audience、none算法和签名篡改，不能只测过期一项。

#### 6. 原理与流程图

```text
收到Bearer → 限定算法/验签 → issuer/audience → 必需声明/nbf/exp/iat
           → sid仍有效、用户启用、族/用户/ver一致 → 当前DB权限 → 身份成立
```

本例零时钟容差且now>=exp拒绝，Clock注入用于精确到期测试，不靠sleep。多实例部署需要合理校时和误差策略；不能把调大容差当过期测试通过的办法。

#### 7. 类比边界与错误直觉

JWS签名不加密payload，不能存密码或需要保密的字段。验证公钥只能来自受控配置，不从任意jku/文件名等令牌输入加载。JWT短期不等于无法撤销，本例主动引入数据库会话状态实现后续请求撤销。

#### 8. 企业应用场景与选型

明确谁发行、谁接收、密钥如何配置与轮换。普通启动必须提供匹配的RSA2048以上PEM，临时密钥仅允许明确测试路径；重启时静默换密钥会使旧Access失效，不能冒充生产密钥管理。签名密钥轮换与Refresh轮换是不同任务。

#### 9. 常见错误与排查

先区分签名、issuer/audience、时间、声明缺失和服务端会话失效。缺aud应成为正常拒绝，不能空指针。不要为某个失败令牌把算法允许表放宽为任意算法，也不要打印原始Authorization或Refresh。

#### 10. 实操练习与验收

运行两端签名/声明负例及密钥匹配测试，全部错误令牌都应401。固定Clock推进到exp边界即拒绝。解释合法签名为什么仍可能因受众或会话状态失败，并指出本例密钥测试与真实SQL测试分别证明什么。

基础题：列出本例9项必需声明并区分JWT规范要求。迁移题：对照jose与Nimbus的验签及声明验证入口。思考题：签名正确为什么仍会401？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#signatureAlgorithmIssuerAudienceAndRequiredClaimsAreValidated' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

### ch30-kp02 · Refresh轮换、重放与凭据版本

#### 1. 一句话定义

Refresh轮换把一个高熵凭据消费一次并发行下一凭据，服务端记录族状态和登录版本，旧凭据重放或凭据版本失效必须阻止继续恢复Access。

#### 2. 为什么需要它

只把Refresh过期时间设得长而不消费旧值，会让窃取者长期重复刷新。只让Access携ver也不够：如果旧Refresh可以直接取得用户最新ver再签发，就会复活本应失效的凭据。

#### 3. 生活类比

续期券每次使用都被登记为已消费并换新券，发现旧券再次出现就冻结整册。换了身份凭据版本时，旧册不能自动升级为新册；必须重新核验身份。该策略无法分辨攻击重放和客户端丢失响应后的重试，因此需要说明使用代价。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java TransactionTemplate与Prisma交互事务都能实现状态转换，但必须使用实际事务资源。两端把未知/拒绝结果返回到事务外再抛401，让撤销确实提交；如果在事务里抛异常导致回滚，重放检测看起来报错却可能没撤销任何东西。并发Java线程和并发Node请求最终都要依赖数据库锁，而不是客户端Promise写法。

#### 5. 两端代码对照

库中只保存SHA256(token)和sessionId/过期/消费/撤销时间，原始Refresh不写库。V2在sec_sessions保存登录时auth_version；Access及Refresh同时比较族与当前用户版本。用户状态用当前FOR UPDATE读，统一锁顺序User→Session→Refresh，避免MySQL RR早期一致性快照读到旧版本。

```text
有效Refresh → 消费旧行 + 插入新行 + 审计 → commit → 返回新令牌
已消费Refresh → 撤销族及Refresh行 + 审计 → commit → 401
族版本!=用户版本 → 撤销/拒绝，不能用旧Refresh自动升级ver
```

Java和Node完整源码均按这些步骤实施；旧V1族默认绑定0，不回填成最新版本，必要时保守要求重新登录。

#### 6. 原理与流程图

```text
哈希定位族（仅路由） → 当前锁定读用户 → 锁族 → 锁并重读Refresh
 → 检查版本/启用/过期/消费/撤销
 → 成功轮换或撤销拒绝 → 提交 → HTTP结果
```

最初非锁定查询只用于找到不可由客户端选择的族，不作为最终授权依据。登录也在锁内复核Hash/版本/启用状态，避免密码核对期间凭据变化后仍发行旧状态会话。

#### 7. 类比边界与错误直觉

库里存哈希不保护已被窃取的原始Bearer凭据，仍需传输和客户端防护。族撤销仅对后续认证生效，不取消所有在途业务。两次同时刷新会一次成功、一次重放，继而使成功响应里的新令牌也失效；本例没有承诺网络重试宽限窗口。

#### 8. 企业应用场景与选型

浏览器客户端可合并并发刷新，避免自己触发重放；丢失轮换响应后本例要求重新登录。生产若采用宽限/幂等刷新协议，需要重新定义攻击识别与测试。固定族有效期默认7天，新Refresh不无限滑动延长，Access寿命也不超过族剩余时间。

#### 9. 常见错误与排查

重放后新令牌仍可用，检查撤销是否被异常回滚。版本提升后旧Refresh能恢复访问，检查是否真正绑定登录版本。并发死锁应先画锁顺序，不盲目加重试。事务中读到旧用户状态时区分一致性快照和当前锁定读。

#### 10. 实操练习与验收

先轮换再重放旧值，要求新旧Access/Refresh都被拒；验证数据库撤销已提交。提升auth_version后旧两类凭据都拒绝，新登录绑定新版本。两个身份并发与两个刷新并发分别测试，不把它们当相同场景。锁等待测试的短超时只说明未提前返回，源码锁定读与最终数据库结果才构成完整依据。

基础题：画出Refresh消费及族撤销状态。迁移题：将Prisma事务外抛错规则迁移到TransactionTemplate。思考题：为什么事务内抛异常可能恢复已经撤销的会话？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#refreshRotationRejectsReplayAndCommitsFamilyRevocation' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

### ch30-kp03 · 会话状态、浏览器存储与CSRF取舍

#### 1. 一句话定义

令牌存放和自动发送方式决定CSRF/XSS等威胁面，STATELESS不消除服务端状态，也不自动证明关闭CSRF安全。

#### 2. 为什么需要它

Cookie可能由浏览器自动附带，Authorization通常由应用显式设置；两者的攻击路径不同。把JWT搬到Cookie后继续照抄“关闭CSRF”，或者认为localStorage不受XSS影响，都会形成错误安全直觉。

#### 3. 生活类比

把票据交给自动出示的门卡夹，与每次主动拿票给窗口，是不同流程。自动出示方便，但可能在你没注意时被带去别处使用；主动出示也不能防止有人偷走票据。流程便利性与票据保密性要分别设计。

#### 4. Node.js / TypeScript / NestJS 技术对照

Java配置SessionCreationPolicy.STATELESS，只是不使用HTTP Session保存认证；Node也不以Cookie建立会话，两端仍查询MySQL会话族。CSRF保护机制不能用“框架是Java还是Node”判断，应按凭据是否自动携带、来源控制、CORS及客户端协议分析。

#### 5. 两端代码对照

本例Access仅从Authorization Bearer读取，Refresh从JSON正文读取；手动Cookie accessToken不会认证成功。登录/刷新响应no-store，审计仅存事件、actor/resource/request ID，源码没有把密码或原始令牌写入审计。公开登录/刷新请求也应省略失效Access头，因为即使路由permitAll，携带无效Bearer仍可能先被认证层拒绝。

| 方式 | 能帮助什么 | 不能据此声称 |
|---|---|---|
| 内存令牌 | 减少跨刷新持久留存 | 自动免疫同源脚本窃取 |
| localStorage | 客户端持久化方便 | 安全防XSS或适合所有管理端 |
| HttpOnly Cookie/BFF | 限制JS直接读取相应Cookie | 自动解决CSRF、来源和会话固定问题 |

本例没有实现BFF或浏览器持久登录，不能把表格当已验证功能。

#### 6. 原理与流程图

```text
客户端选择存储/发送协议 → 服务端明确凭据入口 → 验证与会话状态
                       → 配置对应CSRF/来源策略 → 日志脱敏/撤销
```

注销使数据库族失效；权限变更从DB读取；这些都是有状态行为。JWT签名减少部分信任需求，不代表每次请求都可以不访问服务端状态。

#### 7. 类比边界与错误直觉

只绑定环回不等于生产网络安全部署；普通教学入口不提供TLS、完整限流或外部密钥管理。Cookie不被接受只证明当前协议入口，不证明浏览器应用不存在XSS。不要把用户ID或JWT payload当敏感秘密的安全存储。

#### 8. 企业应用场景与选型

根据客户端、部署域和会话体验选择Bearer或Cookie/BFF，并定义过期、退出、重放与多设备策略。后续Vue毕业项目必须遵守所选协议，不能在自动刷新拦截器里对登录/刷新本身无限递归。修改存储方式要同步修改CSRF测试和文档。

#### 9. 常见错误与排查

登录后立刻401先查是否附带旧Bearer、时钟、密钥和族状态。退出后仍有权限先查当前请求是否在退出前已经开始，再查DB撤销与缓存。日志中出现原始头或正文应直接修日志采集，不靠缩短令牌寿命掩盖泄漏。

#### 10. 实操练习与验收

明确Cookie不认证、有效Bearer成功、失效Bearer拒绝、注销后双凭据失效四条预期。检查审计记录不含密码/Access/Refresh，并解释该断言不等于所有代理与外部日志系统都已审计。写出改用Cookie时需要重新验证的CSRF和来源边界。

基础题：说明Cookie不认证与Bearer入口。迁移题：把Cookie/BFF方案所需CSRF校验列为迁移差异。思考题：HttpOnly为什么不能直接解决所有XSS与CSRF风险？

可执行验收（仓库根目录，先完成章首README的独立教学库、依赖构建和环境变量准备）：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml '-Dtest=SecurityApiTest#logoutAndDisabledAccountRevokeUseOfAccessAndRefresh' test
node --test examples/nestjs-security-reference/dist/security.test.js
```

Java命令定位本点关键场景，Node命令执行完整16场景以包含对应正反例；核对测试断言与上面的验收预期。测试通过不能代替思考题解释。

## 章末验收与审查记录

本章draft。版本绑定、缺声明与并发回归已在Security43170418413的Java21/25矩阵通过，每组Java19项及Node19项；后续合法写断言仍需对应提交CI，见[进度](../progress/P4-01.md)。官方参考：[JWT RFC7519](https://www.rfc-editor.org/rfc/rfc7519)、[JWT最佳实践RFC8725](https://www.rfc-editor.org/rfc/rfc8725)、[OAuth安全最佳实践RFC9700](https://www.rfc-editor.org/rfc/rfc9700)、[Spring CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)、[jose](https://github.com/panva/jose)。
