# Prisma事务参照

完整运行入口在[真实MySQL实验说明](../transaction-lab/README.md)。这是第27章的独立TS参照，不是毕业项目的新后端，也没有替代Nest业务模块。

CLI/Client/官方MariaDB适配器固定7.10.1，驱动mariadb3.5.4。使用ESM、明确输出的prisma-client生成器及适配器；不采用registry当前默认指向的Prisma8 RC，不自建驱动。

`prisma/schema.prisma`只描述Flyway管理的两张实验表，生成目录src/generated被Git与Prettier忽略，克隆后必须执行transactions:build。prisma.config.ts的回环占位URL仅用于离线生成；真正运行测试必须显式提供TX_DATABASE_URL，并验证主机、端口与transaction_lab库名。

```sh
npx --yes pnpm@10.34.9 transactions:build
npx --yes pnpm@10.34.9 --filter @java-for-nestjs/prisma-reference test:unit
# Flyway迁移和真实本地MySQL服务就绪后：
npx --yes pnpm@10.34.9 transactions:test
```

不会自动加载.env；.env.example仅作同名shell环境变量参考，不放真实凭据。普通回调中的操作显式使用tx，wrongClient是受测试保护的错误示例，不能复制为正确用法。独立事务示例显式另借连接，不能当成Prisma嵌套事务或Spring传播注解。
