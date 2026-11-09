# 独立示例与双端对照

此目录保存教学运行物。当前可运行 [compatibility-probe](compatibility-probe/README.md)，用于技术基线的集成验证。正式课程示例尚未交付；P0-05 初始化工作区，P1 交付四篇样章的代码和验证。

| 后续路径 | 职责 |
|---|---|
| `java-basics/` | 不依赖 Spring 的 Java 语言、运行时与工程示例 |
| `nestjs-reference/` | TypeScript / Node / NestJS 同题参照与测试 |
| `spring-boot-examples/` | Spring Boot、数据访问、安全及可靠性示例 |

`compatibility-probe/`独立维护，不作为企业项目或安全模板。上述后续路径中的示例交付时必须说明依赖与适用版本、真实运行命令、预期输出、失败排查及测试方式。双端实现应保持业务语义可比，明确机制差异，不强行模拟对方生态。

目录和测试须由章节映射追踪。毕业项目代码位于 [project](../project/README.md)；完整规则见 [贡献指南](../CONTRIBUTING.md)。
