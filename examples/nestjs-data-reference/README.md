# Nest / Prisma真实数据参照

先按[Java数据实验](../data-access-lab/README.md)准备专用data_lab并运行Flyway迁移。原transaction_lab实验守卫及入口保持独立，不能把其URL直接改为其他库。

根目录运行：

```powershell
pnpm data:build
$env:DATA_DATABASE_URL='mysql://lab:lab-only-password@127.0.0.1:3306/data_lab'
pnpm data:test
node examples/nestjs-data-reference/dist/main.js
```

默认绑定127.0.0.1:8088，PORT可设0让系统分配。仅供开放教学API，无生产认证。请使用实验账号；测试删除data_tasks，URL守卫只允许环回专用库及无查询参数。完整Schema由Flyway管理，Prisma只generate，不运行Migrate/db push。

复用原Nest Controller/Service/DTO与原始输入守卫，通过TaskStorage注入PrismaTasks并await真实持久化；内存入口和原有问候入口仍可独立使用。app.close触发Adapter关闭Client；测试finally也释放Client，初始化失败时同样清理。

真实SQL验证包括HTTP创建/替换/删除与数据库直读、参数绑定、分页、不同owner的N+1 4次SELECT和批量读取2次SELECT。本机只生成/编译与URL守卫，Linux21/25+MySQL8.4.7的实际结果见[进度](../../docs/progress/P3-02.md)。
