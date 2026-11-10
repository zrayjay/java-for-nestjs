# ADR-0003：事务试点的真实MySQL双端实验

日期2026-11-10，状态：随P1-04候选审查。范围仅第27章，不改变Java21/Boot3.5.16主线或毕业项目架构。

## 决定

Spring实验使用Boot BOM管理的JDBC/事务/Hikari及既定Flyway11.20.3，独立Maven项目，不加入无需Docker的根reactor。Linux CI使用MySQL8.4.7真实服务，禁止H2或mock数据库替代。

TS参照使用Prisma7.10.1稳定版、相同版本Client及官方MariaDB适配器连接MySQL；registry默认prisma标签已指向8.0.0-rc.33，本章不采用预发布主线。7.10.1要求Node^20.19/^22.12或>=24，与已锁Node22.20.0兼容。版本取自实际npm元数据，真实事务行为还须CI验证。

仅使用发布的适配器，不实现新驱动。schema.prisma用于生成类型化Client，Flyway SQL作为本实验唯一表结构来源，不同时使用Prisma Migrate管理同一表。

## 对照边界

共享提交/回滚和读取结果契约；明确Spring代理/异常回滚规则/传播与Prisma显式transaction client不等价。Prisma显式开第二个独立事务可观察局部独立提交，但不能称为完整REQUIRES_NEW实现。隔离实验固定READ COMMITTED/REPEATABLE READ，并控制不同连接上的提交顺序。

测试仅允许回环主机及专用transaction_lab数据库，初始化清理该实验的测试表。CI使用独立临时MySQL服务与公开测试凭据，不创建生产资源或连接外部数据库。

## 验证与限制

本机无可用Docker，只做真实编译、生成和单元检查；数据库通过状态必须来自Linux CI的实际SQL断言。Java21/25分别运行，Node事务也使用真实MySQL。Windows完整数据库探针缺口仍保留，不因G0用户接受而伪造补测。
