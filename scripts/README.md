# 质量检查工具目录

工具已由P0-06/P0-07交付。在仓库根使用`npx --yes pnpm@10.34.9 <命令>`；`docs:prepare`从唯一数据源chapter-map.yml生成JSON及Markdown课程目录。文档运行方式见[文档站指南](../docs/00-introduction/docs-site.md)。

现有质量命令：

| 命令 / 脚本 | 检查范围 |
|---|---|
| `chapters:check` / `check-doc-structure.mjs` | 映射、正文路径、知识点稳定ID及十段式结构 |
| `chapters:check` / `check-example-links.mjs` | 源码Markdown链接；`docs:links`在构建后校验HTML路径及锚点 |
| `chapters:check` / `verify-compatibility.mjs` | 根POM、Wrapper、package及CI的有限版本字段一致性 |
| `chapters:test` | 临时合成正反例，结束后清理，不计作课程正文 |
| `js:check` | 本目录所有mjs语法 |
| `examples:verify` | 三端×九输入的进程契约 |

检查失败以非零退出阻断CI。`chapters:check`在Foundation每次推送/PR执行，避免只改POM、Wrapper或CI时被文档路径过滤漏掉。具体字段、参数及失败边界见[映射维护指南](../docs/00-introduction/chapter-map.md)。不能用结构检查代替类比准确性、机制、安全或架构的独立审查。

结构、类比准确性和示例映射不能仅凭文档站构建判断，规则见[贡献指南](../CONTRIBUTING.md)。
