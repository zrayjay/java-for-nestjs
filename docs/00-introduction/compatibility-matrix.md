# 技术兼容矩阵

核验日期：2026-11-09。基线决策：[ADR-0001](../adr/0001-technology-baseline.md)。本表区分官方声明、锁定版本与实测证据，不声称使用最新版本或具备生产安全保证。

## Java主线

| 组件 | 主线锁定 | 版本来源 / 约束 | 本次验证范围 |
|---|---|---|---|
| Java语言级别 | 21 | 以21编译；25作为兼容运行与构建目标 | 分环境记录见下表 |
| Spring Boot | 3.5.16 | starter-parent统一管理Spring与插件 | 真实Web上下文与4项集成测试 |
| Spring Security | 6.5.11 | Boot 3.5.16 BOM | 匿名401、认证后200；不代表JWT/RBAC完成 |
| Maven | 3.9.11 | 独立固定构建工具；Wrapper由P0-05统一交付 | clean verify含打包 |
| MyBatis-Plus | 3.5.18 | mybatis-plus-spring-boot3-starter | MySQL查询、写入与回滚 |
| springdoc OpenAPI | 2.10.0 | Boot3.5对应2.8~2.10系列 | 生成文档并包含实际路由 |
| Flyway | 11.20.3 | 显式覆盖Boot默认11.7.2；core与mysql模块同版 | 空库迁移及MySQL8.4集成 |
| MySQL Server | 8.4.7 | 测试镜像mysql:8.4.7 | 迁移、Mapper查询和事务 |
| MySQL Connector/J | 9.7.0 | Boot BOM管理，不要求与Server同主版本 | 连接MySQL8.4.7 |
| Redis Server | 7.4.6 | 测试镜像redis:7.4.6-alpine | Spring Data Redis读写 |
| Testcontainers | 1.21.4 | Boot BOM，junit-jupiter/mysql坐标 | 临时MySQL/Redis、无跳过测试 |
| JUnit Jupiter | 5.12.2 | Boot BOM | 4项集成断言 |
| 编译 / 测试插件 | Compiler 3.14.1 / Surefire 3.5.6 | Boot parent管理 | Java21 release编译与JUnit执行 |

版本证据来自实际POM及[Boot 3.5.16 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom)。不要独立覆写Spring Framework/Security等被管理组件，除非提交兼容验证和ADR。

## 文档与前端候选

| 工具 | 本次决策 | 验证状态 |
|---|---|---|
| VitePress | 1.6.4稳定线，ESM配置；不使用2.0 alpha作为课程基线 | npm发布元数据已核验；站点搭建和构建由P0-03交付 |
| Node | 当前本机22.20.0；P0-03以Node22建立文档构建证据 | 本机版本已读取；不等于站点已构建 |
| pnpm | P0-03/P0-05在项目内锁定可用精确版本及锁文件 | 本机启动器指向缺失的12.5.1文件；未修改全局配置，不视为可用版本 |
| Vue / Vite / Mermaid集成 | 随P0-03文档站和P6前端的实际依赖锁定 | 当前不编造版本或宣称渲染通过 |

VitePress1文档的Node下限与VitePress2文档不同，应查阅[稳定版安装指南](https://vuejs.github.io/vitepress/v1/guide/getting-started)，不要混用next/alpha的要求。

## 官方兼容声明

- [Boot3.5系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)：该版本线支持本课程选择的Java21/25，构建工具满足其最低要求。
- [MyBatis-Plus安装说明](https://baomidou.com/en/getting-started/install/)：Boot3和Boot4使用不同Starter坐标，不能只改Boot parent。
- [springdoc兼容矩阵](https://springdoc.org/v2/faq.html)：Boot3.5选择2.x对应范围，Boot4选择3.x。
- [Testcontainers环境要求](https://java.testcontainers.org/supported_docker_environment/)与[Windows说明](https://java.testcontainers.org/supported_docker_environment/windows/)：Windows使用Linux容器环境，不能以Windows containers模式替代。
- [Maven3.9.11发布说明](https://maven.apache.org/docs/3.9.11/release-notes.html)：固定已验证版本，不声称它是最新版本。

## 分环境实测

| 栈 | 环境 | JDK | 结果 |
|---|---|---|---|
| Boot3主线 | 本机Docker Linux / Maven3.9.11 | Temurin21.0.9 | 4项通过、无跳过，clean verify成功 |
| Boot3主线 | 本机Docker Linux / Maven3.9.11 | Temurin25，补丁号见运行记录 | 4项通过、无跳过，clean verify成功 |
| Boot3主线 | Windows11原生 / Maven3.9.11 / Docker Desktop | JBR25.0.4.1 | 正在验证，最终结果写入P0-02记录 |
| Boot3主线 | Windows11原生 | Java21 | 下载临时JDK中，尚未验证 |
| Boot3主线 | Ubuntu24.04 GitHub Actions | Temurin21/25 | 工作流已配置，运行结果待回填 |
| Boot4对比 | 本机Docker Linux | Java21 | 依赖下载403，未进入测试，不能判定兼容或不兼容 |
| Boot4对比 | Ubuntu24.04 GitHub Actions | Temurin21/25 | 独立POM比较，运行结果待回填 |

本机Docker Linux与Linux CI是不同证据；Windows原生运行与Windows宿主上的Linux容器也不混同。完整过程见[P0-02验证记录](../progress/P0-02.md)。

## 已知问题与处理

1. Boot默认Flyway11.7.2虽能迁移，但对MySQL8.4输出支持范围警告；改用11.20.3后在同一数据库版本重新运行，警告消失且4项测试及打包通过。后续升级应同步core与mysql模块。
2. 首次verify的测试通过，但打包插件依赖下载失败；重新解析依赖并完成整个verify才计为通过。
3. Testcontainers容器结束时Redis客户端可能输出重连日志；只在断言及完整退出码成功时记录通过，不删除或隐藏失败输出。
4. Mockito动态Agent和Maven/JDK25的Unsafe提示属于后续工具链维护事项；当前没有Mock测试，不据此声称Mockito全能力兼容。

## Boot4评估边界

独立`pom-boot4.xml`选择Boot4.1.2、MyBatis-Plus Boot4 Starter3.5.18、springdoc3.2.0。为保持比较变量一致，暂显式固定Testcontainers1.21.4和Flyway11.20.3；这不同于Boot4默认管理的Testcontainers2.0.5/Flyway12.4.0。

即使对比探针通过，也仅证明同一小型API/数据库/缓存组合可运行。Boot4默认测试栈、Jackson3迁移、全部课程和生产行为仍需独立验证；本次不改变Boot3主线。

## 升级回归清单

- [ ] 记录官方支持范围、精确版本及发布来源，提交ADR。
- [ ] Windows11原生和Linux CI分别在Java21/25运行完整verify，禁止跳过测试。
- [ ] 验证Flyway空库迁移、已有库升级、Mapper与事务回滚。
- [ ] 验证认证拒绝、API响应、OpenAPI生成和Redis读写。
- [ ] 执行后续章节交付的JWT/RBAC/缓存/消息故障及安全回归。
- [ ] 构建VitePress，检查链接、章节映射、版本标注及TS锁文件。
- [ ] 记录失败、未测范围和独立审查结果，再更新基线。
