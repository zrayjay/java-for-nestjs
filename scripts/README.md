# 质量检查工具目录

当前已有`generate-course-index.mjs`，从文档站目录数据生成45章Markdown索引，并检查编号完整性；执行`pnpm docs:prepare`即可。文档类型检查与构建命令见[文档站指南](../docs/00-introduction/docs-site.md)。

以下质量检查脚本由P0-07交付最小版本，P0-06对接全仓库CI：

| 计划脚本 | 检查范围 |
|---|---|
| `check-doc-structure.mjs` | 逐知识点模板与文档结构 |
| `check-example-links.mjs` | 章节、真实源码与测试的映射及路径 |
| `verify-compatibility.mjs` | 示例版本与经过验证的兼容矩阵一致性 |

检查脚本交付后，在此记录实际调用方式、参数、通过条件和失败示例。不能用结构检查代替类比准确性、机制、安全或架构的独立审查。

结构、类比准确性和示例映射不能仅凭文档站构建判断，规则见[贡献指南](../CONTRIBUTING.md)。
