# Spring / Nest HTTP教学实验

基线：Java21、Boot3.5.16、Nest11.2.9。内存仓库会随进程结束清空，不是数据库或生产认证实现。监听127.0.0.1。

从仓库根目录运行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/spring-web-lab -am verify
pnpm examples:build
pnpm examples:test
pnpm http:verify
```

`http:verify`启动两个独立构建产物，等待真实端口标志，调用HTTP，再结束子进程；它不通过导入Controller绕过网络链路。

手动运行：

```powershell
java -jar examples/spring-web-lab/target/spring-web-lab-0.1.0-SNAPSHOT-exec.jar
node examples/nestjs-reference/dist/api/main.js
```

Spring默认8085；Nest默认8086（用PORT环境变量覆盖）。两端均有/tasks CRUD、/validation/draft、/validation/publish及/openapi.json。POST /tasks输入为`{"title":"Learn Java","details":{"priority":2}}`，返回201及相对Location。title为最多80个ASCII字符且首字母为英文字母，priority是1~5的整数值。拒绝未知字段和字符串数字。数学整数1.0允许，1.5拒绝。

Java WebApiTest验证真实随机端口HTTP、MockMvc、请求链、profile及Actuator；BootModelTest验证条件装配、用户Bean退让、非法配置启动失败及显式关闭；ProxyModelTest用真实JDK/CGLIB代理验证自调用边界。Nest api.test通过真实HTTP及app.close验证契约和生命周期。

请求链是教学范围的观测：Spring Filter/Security Filter/MVC Interceptor，Nest Middleware/Guard/Interceptor；两端不保证所有异常、异步流、重分发的事件顺序相同。跟踪最多保留100条，不是生产可观测性系统。Nest还在转换前用rawBody与Node22.20的JSON.parse源token检查精确整数、原始允许键，防止高精度小数舍入或保留键提前丢弃造成契约差异；不要只复制DTO而漏掉InputBoundary。空limit显式拒绝，只有未提供limit才使用20。

共同契约不包括所有框架默认行为：未支持方法Spring通常405，Nest默认404。Nest显式类型配置Provider用于对照，不声称已执行ConfigModule。安全链permitAll、无JWT/RBAC；Actuator仅公开health/info。后续章节单独验证数据库及权限。
