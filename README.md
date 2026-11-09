# 从 NestJS 到 Spring Boot

面向已有 TypeScript、Node.js、NestJS 经验的开发者，学习 Java 与 Spring Boot 的中文系统教程。

通过同题示例连接已有经验，解释运行时、类型系统、依赖注入、事务和安全机制的差异。每个独立知识点包含类比、边界、代码、练习与可检查的验收条件。

## 从这里开始

- [学习指南](docs/00-introduction/learning-guide.md)：先修要求、学习顺序和阶段目标。
- [文档入口](docs/index.md)：课程内容与建设进度。
- [实施计划](JAVA-FOR-NESTJS-IMPLEMENTATION-PLAN.md)：45 章范围、版本治理、教学标准和验收基线。
- [实施任务](TASKBOARD.md)：任务拆分、前置依赖和执行记录。
- [贡献指南](CONTRIBUTING.md)：文档、示例、验证和 PR 要求。

## 当前可运行内容

当前交付为 P0-01 仓库基础：说明文档、学习入口、目录约定和贡献规范。尚未提供可运行课程示例、文档站或毕业项目。

后续任务将分别交付兼容矩阵（P0-02）、VitePress 站点（P0-03）、教学模板（P0-04）、双端工程（P0-05）、CI（P0-06）及章节映射校验（P0-07）。届时在对应目录写明真实运行命令、依赖版本及预期输出。

现阶段可获取仓库并阅读文档：

```sh
git clone https://github.com/zrayjay/java-for-nestjs.git
cd java-for-nestjs
```

新内容在任务分支通过审查后合入默认分支；以实际检出的文件为准。Java、Spring Boot、Node 和工具链的精确版本将在兼容矩阵中核验锁定。

## 仓库结构

| 路径 | 职责 |
|---|---|
| `docs/` | 教程正文、学习指南、术语、ADR 与验证记录 |
| `examples/` | 独立 Java 示例和 NestJS / Spring Boot 对照运行物 |
| `project/` | Vue3 管理端与 Spring Boot 模块化单体毕业项目 |
| `scripts/` | 文档结构、链接、映射与版本验证工具 |
| `TASKBOARD.md` | 实施任务与进度记录 |

章节正文遵循 `docs/<part>/<nn>-<slug>.md`；完整代码指向仓库真实文件。Java 依赖由 Maven 管理，前端与 NestJS 依赖由 pnpm 管理。

## 交付原则

先完成基础工程和四篇样章审查，再建设其余章节。毕业项目采用模块化单体；分布式机制通过可复现示例说明边界。构建、测试、故障和安全结论均须附实际证据。

## 许可

本仓库原创文档与代码采用 [MIT License](LICENSE)。第三方代码、引用和依赖保留各自许可及归属声明。
