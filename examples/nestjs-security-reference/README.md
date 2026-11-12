# Nest安全同题参照（实施中）

复用P3 HTTP/DTO及Prisma客户端；安全Feature显式注入，不改变默认开放实验。RS256、issuer/audience/过期和声明验证、BCrypt、Refresh轮换/族撤销、当前数据库权限、部门读/本人写与同事务审计。未实现租户，不宣称生产认证部署完成。

先按[Java安全实验](../security-lab/README.md)运行Flyway迁移，使用专用data_lab；测试清理该库教学表。然后从根目录执行：

```powershell
pnpm security:build
$env:DATA_DATABASE_URL='mysql://lab:lab-only-password@127.0.0.1:3306/data_lab'
pnpm security:test
```

3项密钥单测可独立运行`node --test examples/nestjs-security-reference/dist/keys.test.js`，不代表其余SQL测试通过。完整结果见[进度](../../docs/progress/P4-01.md)。

手动运行要求外部PKCS8私钥和X509公钥，可复用Java KeyTool生成的被git忽略的本机教学PEM：

```powershell
$env:SECURITY_PRIVATE_KEY_PATH='target/demo-keys/private.pem'
$env:SECURITY_PUBLIC_KEY_PATH='target/demo-keys/public.pem'
node examples/nestjs-security-reference/dist/main.js
```

绑定127.0.0.1:8090，无静默临时密钥或默认用户。测试临时密钥只由测试代码显式传入，正常入口必须读取文件并验证实际匹配。API为/auth/login、/auth/refresh、/auth/logout、/me及/tasks、管理员角色调整。

认证/授权前置于请求体解析，同时使用Guard与数据层规则；AsyncLocalStorage隔离各请求身份。Refresh只保存SHA256，重放会提交整个会话族撤销后返回401；并发刷新一次成功后，另一次重放会使成功响应里的令牌也失效。JSON Refresh和Authorization Bearer不接受Cookie认证；改Cookie方案须重新设计CSRF。没有跨实例限流、Redis、上传扫描或完整P4验收声明。
