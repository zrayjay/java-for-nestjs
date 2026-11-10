# ADR-0005：固定Gradle对照实验运行时

2026-11-10，实施采用。第15章原任务要求可复现任务图/缓存比较，先前规则对照明确未实测；现在补齐独立小型实验，不迁移Maven主线，不添加第二套业务构建。

选择Gradle9.2.1稳定发布运行于JDK21/25。官方发行校验入口为services.gradle.org/distributions/gradle-9.2.1-bin.zip.sha256，本次读取值72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f。下载、校验与实际运行结果另按证据记录，不以版本选择代替验证。

独立Groovy构建只实现缓存标注的问候文件任务与依赖它的验证任务；无远端仓库/第三方插件/业务依赖。显式输入learner、输出文件；依次验证执行、同输入UP-TO-DATE、删除单个生成文件后FROM-CACHE、改输入输出变化与dry-run依赖顺序。不测试相对性能、配置缓存、远端缓存或生产项目迁移。

CI用官方gradle/actions/setup-gradle固定9.2.1，在现有Windows/Linux × JDK21/25矩阵执行。产物和.gradle缓存不入Git；Maven Wrapper、根Reactor与业务主线不变，课程计划更新v1.4登记对照运行基线。
