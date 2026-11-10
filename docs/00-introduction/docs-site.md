# 文档站运行指南

文档站使用VitePress1.6.4、Vue3、TypeScript与Mermaid。精确依赖固定在根`package.json`和`pnpm-lock.yaml`；无需Java或数据库即可构建文档。

## 本地运行

要求Node22.20.0或满足根`package.json`中engines约束的版本。在仓库根目录执行：

```sh
npx --yes pnpm@10.34.9 install --frozen-lockfile
npx --yes pnpm@10.34.9 docs:dev
```

打开终端显示的本地地址，默认是`http://127.0.0.1:5173/`。已有相同版本pnpm时可直接使用`pnpm`；npx方式不依赖本机已损坏或版本不匹配的全局启动器。

构建与预览：

```sh
npx --yes pnpm@10.34.9 docs:typecheck
npx --yes pnpm@10.34.9 docs:build
npx --yes pnpm@10.34.9 docs:preview
```

预览默认地址`http://127.0.0.1:4173/`，以终端输出为准。静态产物在`docs/.vitepress/dist/`，不提交Git。

## 站点能力

- 首页和中文侧栏提供学习指南、9部分/45章索引、技术基线及兼容探针入口。
- 导航栏使用VitePress本地搜索，可搜索标题、正文及全部章节索引。没有正文的章节只作为目录结果，不存在假正文页面。
- [图表说明](diagrams.md)展示流程图与时序图；源码始终可展开，渲染失败不会丢失文字。
- 支持浅色/深色、键盘导航和小屏幕菜单。

## 修改目录

根目录`chapter-map.yml`是唯一数据源；`docs/.vitepress/course.json`和`docs/course/index.md`都是生成后纳入仓库的产物。修改映射后执行：

```sh
npx --yes pnpm@10.34.9 docs:prepare
```

dev/build也会先校验映射并生成索引。数据必须顺序包含01~45且编号唯一，正文路径、状态、先修条件及测试映射均在源文件维护。执行`chapters:check`、`chapters:test`检查结构、链接、版本和阻断用例；构建后执行`docs:links`检查HTML页面与锚点。详见[映射维护指南](chapter-map.md)。

新增真实章节时，更新索引生成方式、侧栏和章节映射，并去掉对应“待编写”标识。不要仅建立空文件后标记章节完成。

## 子路径与源码链接

默认根路径`/`。部署到仓库子路径时，在构建进程设置`DOCS_BASE`：

```powershell
$env:DOCS_BASE = '/java-for-nestjs/'
npx --yes pnpm@10.34.9 docs:build
npx --yes pnpm@10.34.9 docs:preview
```

也可在Linux shell前置`DOCS_BASE=/java-for-nestjs/`。构建后访问对应子路径；环境变量影响构建和预览，需要保持一致。

## GitHub Pages自动部署

在线文档：[Java for NestJS](https://zrayjay.github.io/java-for-nestjs/)。这是建设中的站点，章节完成情况以课程目录为准。

仓库公开，Pages的发布源选择GitHub Actions。`.github/workflows/docs.yml`固定`DOCS_BASE=/java-for-nestjs/`，main上的相关文档/脚本/依赖更新或手动运行工作流后，先在Windows/Linux完成类型、章节映射、测试、构建和产物链接检查；两端全部成功后，由Ubuntu产物部署到`github-pages`环境。PR和其它分支只检查，不发布。

部署使用官方Pages Actions及短期OIDC凭证，不需要个人Token或gh-pages分支。发布作业仅获`pages: write`和`id-token: write`权限，并使用并发组避免同时部署。失败时查看Actions中的Documentation site工作流；修复后重新运行或推送修复提交。

此部署不创建GitHub Release，不代表G0/G1关卡或P7全书发布验收已经通过。

docs目录内的Markdown链接作为站内页面解析；指向仓库外层README、任务清单和examples源码的相对链接，在构建时转换到GitHub main上的对应文件。未合并的源码文件在main中可能尚不存在，审查阶段可切换到任务分支查看。

## 维护边界

Mermaid在客户端按需加载，不参与SSR初始化；严格安全模式只处理仓库作者维护的图表。这里不提供对任意输入的在线绘图服务。

本项只建立文档站及其构建校验。教学模板、Maven/前端业务工作区、全仓库CI、章节结构校验分别由后续P0任务交付。
