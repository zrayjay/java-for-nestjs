# 章节映射与校验

仓库根`chapter-map.yml`是45章目录的唯一数据源，编号和路径遵循实施计划第5节。JSON侧栏数据和Markdown目录由`docs:prepare`生成，不能独立编辑。

## 映射字段

`parts`记录9个部分及目录；`baseline`记录实际构建工具版本；每个章节包含两位字符串`id`、所属`part`、标题、正文路径、状态、先修ID、技术标签、知识点摘要、技术参照和试点标记。

`examples`与`tests`指向`examples/`或`project/`下真实文件；`verification`指向仓库内可检查的运行证据。没有新增代码的合理场景，填写至少10字的`noCodeReason`并保留验证证据，不能用空测试充数。

所有映射路径使用仓库相对路径和正斜杠，不允许`..`、绝对路径、目录代替文件或逃逸仓库的符号链接。Markdown源码链接允许正常的相对父目录引用，解析后仍须处于仓库内。

第18章从P0-05构造注入起点扩展为显式容器、作用域、生命周期及代理实验，映射指向其真实源码/测试。初稿或in_review仍不计完成；没有交付的示例使用空列表，不虚构路径。

## 状态与提交步骤

| 状态 | 要求 | 是否计入完成 |
|---|---|---|
| planned | 预定路径可缺文件；引用的示例/测试若已登记必须存在 | 否 |
| draft | 正文存在，元数据与映射一致，逐知识点十段式完整 | 否 |
| in_review | draft要求，加真实运行验证证据 | 否 |
| done | in_review要求，加用户接受及独立审查记录 | 是 |

正文frontmatter至少填写`chapter_id: '02'`、`teaching_status: draft`及非占位的`baseline`（例如`Java21 / Spring Boot3.5.16`）。`## 知识点`区域中每个三级标题采用`ch02-kp01 · 标题`，ID须匹配章节且章内唯一；下面完整复制模板的十个四级标题，顺序及名称保持一致。代码围栏里的标题不计数，空段落和`【待填写】`等占位会阻断。

独立审查记录字段为`path`、`reviewer`、`date`（YYYY-MM-DD）、`independent: true`和`conclusion: approved`。校验器检查字段和证据文件存在，无法认证审查者身份、断言教学正确性或代替用户验收。编写者自评不能作为独立审查。

```sh
npx --yes pnpm@10.34.9 chapters:check
npx --yes pnpm@10.34.9 chapters:test
npx --yes pnpm@10.34.9 docs:prepare
npx --yes pnpm@10.34.9 docs:build
npx --yes pnpm@10.34.9 docs:links
```

CI在Windows/Linux执行同样的检查，并核对两份生成产物没有未提交差异。HTML链接检查覆盖干净URL、相对路径、base及编码锚点；源码Markdown检查覆盖链接和图片目标，忽略代码围栏中的演示。站外HTTP链接不发网络请求，不声称检查远端可用性；根README等未构建文件的锚点需人工审查。

版本校验将映射对照根POM、package.json、Wrapper、CI的Node/pnpm及事务MySQL镜像、Prisma参照包版本和事务Flyway配置，并要求兼容矩阵包含记录。它是有限字段一致性检查，不等于解析所有依赖树或证明全部兼容性；升级依然需要真实测试和ADR。最低特性版本由作者逐知识点标注，不能自动推断。

计划中的先修条件帮助读者排序，不要求试点编写依照所有前置章节的交付顺序。G0/G1仍按实施计划验收，结构检查通过不能自动开放后续批量正文。
