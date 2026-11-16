# P4单元测试章节：两轴独立审查

2026-11-16。固定范围`606e8e3...b8675f8`，只读审查者p2_foundation_standards、p2_foundation_spec，覆盖34章三点及无服务运行物；不含35章MockMvc或整个P4。

## Standards

通过，无须修订的实质发现。逐点十段、映射反例、双端片段与源码一致；真实业务规则保留，外部SDK替换，关键副作用和精确异常断言有据。Mockito显式agent符合ADR0009，未以替身证明真实协议/存储恢复。

审查者实际运行Node4项、Vitest2项和结构检查，均通过；未重跑Java。提示章末CI待验文字需按已有结果更新，作者同步修正。

## Spec

通过，无阻断缺失、错误或越界。满足C34的JUnit/Mockito/AssertJ隔离及重复稳定：每例新建替身、恢复spy、关闭客户端，双因/相同键/越权不读正文/审计失败不返回均有断言。共享规则没有被整体mock掉。CI四组合各新进程两轮，未误称重复成功证明无竞态。

审查者未执行测试或独立查询CI；已查配置与场景，最终执行证据由作者单独核对。

## 每知识点评分

五维通俗/准确/映射/边界/迁移，各0~2。分数不是读者效果测量。

| 知识点 | Standards | Spec |
|---|---|---|
| ch34-kp01 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch34-kp02 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch34-kp03 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |

## 执行证据与结论

[Unit43817767580](https://github.com/zrayjay/java-for-nestjs/actions/runs/43817767580)对应4ac7664：Windows/Linux×Temurin21/25四job全部成功，每job新进程两轮Java8、node:test4、Vitest2。作者实际查Windows21日志两次Java8、Node4无失败/跳过及workflow完整成功，不把来源写成审查者实跑。

正文b8675f8未改这些测试；之后88e29ef新增MockMvc属另一章，[Foundation43818392057](https://github.com/zrayjay/java-for-nestjs/actions/runs/43818392057)及Docs43818392077亦成功。第34章in_review，未自动done。
