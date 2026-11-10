# ADR-0002：修复文档构建依赖的高危审计项

日期：2026-11-10。状态：提议，随P0-06草稿PR审查。

## 证据与决定

P0-06执行`pnpm audit --audit-level high`，发现Vite的Windows launch-editor检查绕过，以及lodash-es模板导入键代码注入两项高危。原锁文件不是安全扫描通过的证据。

保留VitePress1.6.4稳定线；使用pnpm overrides将其底层Vite替换为已修复的6.4.4，并将lodash-es提升到修复范围。VitePress版本与课程技术选择不变，不引入Vite8或VitePress alpha。变更前更新实施计划，再执行覆盖版本与验证；精确包版本保存在锁文件及兼容矩阵中。

此覆盖超出VitePress声明的Vite5依赖范围，因此不能只凭安装成功宣称支持。必须通过类型检查、文档SSR构建、根/子路径资源、搜索、Mermaid与真实浏览器回归；不通过则不能合并。后续稳定VitePress原生使用安全版本时重新评估并移除覆盖。

## 验收与范围

- 安装使用锁文件，审计在high阈值阻断；保留低/中风险结果，不伪称所有级别清零。
- Windows/Linux文档构建和浏览器功能验证通过，记录实际工具版本及CI。
- Java、Spring Boot、45章目录和毕业项目架构不变。

官方版本和漏洞依据可由[npm Vite元数据](https://registry.npmjs.org/vite/6.4.4)、[lodash-es元数据](https://registry.npmjs.org/lodash-es/latest)及`pnpm audit --json`中的公告定位；具体结果记录于P0-06报告。
