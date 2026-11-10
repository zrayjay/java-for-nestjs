# NestJS最小对照端

使用Nest11.2.9的独立应用上下文，按功能模块组织，构造器注入`GreetingPolicy`；不创建HTTP监听器。其目的在于验证工作区、模块装配和与Java共享的问候契约。

从仓库根目录运行：

```sh
npx --yes pnpm@10.34.9 install --frozen-lockfile
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
npx --yes pnpm@10.34.9 examples:start NestJS
```

预期输出`Hello, NestJS!`。名字参数直接跟在脚本名后，pnpm中不需要额外的`--`。省略名字默认NestJS；非法输入非零退出。完整输入规则见[Java版本](../java-basics/README.md)。

测试使用真实Nest TestingModule和Node内置测试运行器，验证模块装配、合法输出和非法输入。框架日志在此CLI入口关闭，仅保留业务输出；应用使用完毕关闭上下文。

TS工程由pnpm与tsc管理，采用Nest传统装饰器元数据与CommonJS输出，不改变根文档站的ESM配置。原理、生命周期和类比边界在后续样章讲解，当前不将两种容器等同。

官方依据：[Nest独立应用](https://docs.nestjs.com/application-context)。
