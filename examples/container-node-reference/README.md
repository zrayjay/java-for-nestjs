# Node原生HTTP生命周期参照

这是一项受控生命周期模型，不是认证任务API或Nest/MySQL对照部署。Node22.20.0，Linux真实子进程收到SIGTERM后停止接流量、等待已接收但正文未完整的HTTP请求、最后关闭并退出；超5秒会主动断开并非零退出。

```sh
node --test examples/container-node-reference/lifecycle.test.mjs
```

从仓库根目录执行。测试等待子进程实际admitted消息后发SIGTERM，等待draining后补齐正文，验证真实HTTP200/字节数和进程退出0。不同于Java容器实验的数据库事务/审计证明；不把原生server.close自动泛化为Nest enableShutdownHooks或Prisma关闭。Windows的子进程信号语义不同，当前真实信号验收在Linux CI，不据Windows编译替代。

完整源码[server.mjs](server.mjs)，测试[lifecycle.test.mjs](lifecycle.test.mjs)。生产需为连接、队列、数据库和后台任务定义各自关闭顺序及预算。
