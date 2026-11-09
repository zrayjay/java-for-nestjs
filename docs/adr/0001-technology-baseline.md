# ADR-0001：课程技术基线与兼容验证方式

日期：2026-11-09。状态：提议，等待独立审查。对应任务：P0-02 / JAV-4。

## 问题

教程需要一个可持续复现的组合，同时展示Java21与25的边界。仅写“最新版本”或沿用示例文章的Starter版本，无法证明Spring、ORM、迁移和API文档能够一起运行。

## 决策

沿用实施计划的Java21语言级别与Boot3.5主线，锁定Boot3.5.16、Maven3.9.11、MyBatis-Plus3.5.18和springdoc2.10.0。Boot BOM管理Spring、安全、数据库驱动和测试组件；只有经验证的必要差异才显式覆盖。

Flyway采用11.20.3，并同时引入`flyway-core`和`flyway-mysql`。原因是实测默认11.7.2对MySQL8.4存在支持范围警告；替换后再次验证真实迁移、查询、事务和完整打包。

建立小型集成探针，使用真实临时MySQL/Redis、真实HTTP、Security过滤器、MyBatis Mapper、Flyway迁移及OpenAPI生成。每个环境运行相同四项测试，不使用H2代替MySQL，也不在缺Docker时自动跳过。

文档站继续使用VitePress稳定线，候选1.6.4；P0-03交付真实站点、导航、搜索和Mermaid。当前不引入alpha版本，也不在缺乏构建结果时宣布前端工具链兼容。

## Boot4比较

另设POM切换Boot4.1.2、对应MyBatis-Plus Boot4 Starter和springdoc3.2.0，并增加Boot4的模块化Flyway Starter。保持相同探针行为，明确显式固定的Flyway/Testcontainers版本。CI中的Java21/25组合均已通过；比较结果是迁移参考，不扩大为全课程或毕业项目的兼容承诺。

本次不采用Boot4作为主线：实施计划优先兼容基线，课程还未建立；Boot4涉及框架/JSON/测试栈变化，局部探针成功不足以替代完整迁移验证。未来改主线先更新ADR、计划、全部示例及回归证据。

## 代价与限制

- 选择已验证的固定版本，不保证它们始终最新；后续按兼容矩阵执行升级回归与安全检查。
- 临时Basic用户、匿名OpenAPI和简单数据库表只用于验证装配，不作为生产配置。
- Windows11、Docker Linux和Linux CI分别记录，测试工具版本及JDK发行商差异也保留。
- 当前Windows原生测试受本机JDK环回连接异常阻碍，未宣称跨平台验收通过；已验证的Docker运行方式是临时替代，不能关闭原生Windows待验收项。
- P0-02的窄CI只验证探针；Maven Wrapper/工作区统一由P0-05交付，全仓库门禁由P0-06交付，避免重复职责。

## 验收依据

精确版本、官方来源、实测状态和升级清单统一记录在[兼容矩阵](../00-introduction/compatibility-matrix.md)。命令与结果记录在[P0-02报告](../progress/P0-02.md)。未完成的环境和独立审查不得记录为通过。
