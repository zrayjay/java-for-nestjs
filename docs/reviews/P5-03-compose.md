# P5-03 / 第40章Docker部分独立审查

2026-11-17，固定点`d0f110c`，全文复核至`7965302`。作者root；只读独立审查者`p2_foundation_standards`与`p2_foundation_spec`。K8s未交付，本记录不批准全章或G5。

## Standards

早期一项P2：锁回滚或证据写入失败会跳过末尾down，可能遗留本次资源。修为外层finally总尝试清理随机隔离项目，保留primaryError，不让清理异常覆盖主失败。全文结论Docker三点通过、原P2关闭，无新增硬缺陷；Node原生模型与Java SQL排空各自边界清楚。

审查者实际执行结构检查，未运行Docker或信号操作；完整演练依据作者下载核对的CI。Wrapper/Compose失败历史保留，未把部分通过或退出143单独当作排空完成。

## Spec

Docker部分文本与源码通过，无新增阻断。符合P5-03一键启动、真实依赖就绪、健康检查、优雅停止与说明；演练先观察真实SQL锁等待再TERM，核对HTTP/事务/审计/重启，区分在途排空与空闲stop。范围为认证任务API+MySQL完整运行链，未提前承诺P6全部原语业务集成。

审查者没有运行容器；旧失败轮次只支持已完成阶段，完整CI证据由作者独立核对。40章K8s部分继续未交付，保持draft，不因Docker部分审查通过标整章完成。

## 逐点评分

五维依次通俗/准确/映射/边界/迁移，各0~2。两轴分别评分一致；评分不等于真实读者试学。

| 知识点 | Standards | Spec |
|---|---|---|
| ch40-kp01 镜像与卷 | 2/2/2/2/2 | 2/2/2/2/2 |
| ch40-kp02 就绪探针 | 2/2/2/2/2 | 2/2/2/2/2 |
| ch40-kp03 TERM排空 | 2/2/2/2/2 | 2/2/2/2/2 |

## 实际执行闭环

7965302的[容器44002059576](https://github.com/zrayjay/java-for-nestjs/actions/runs/44002059576)完整通过。作者下载job133768296325日志及compose-lifecycle-evidence artifact，确认五项checkpoint、运行用户10001:10001、Engine28.0.4/Compose2.38.2、进程退出143且非OOM、实际SQL/audit提交与重启读取。独立Node真实SIGTERM模型1项通过，无失败/跳过。

镜像构建已实际使用Temurin21.0.13+9：JDK镜像索引sha256为`06c1f157dacf6b68198bd9a4186dd4c34ef24e65d7bbbe2a0cf8e62571ad38c9`，JRE为`0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515`。本次应用image id为`f0e358d9dca8845bbf1e5ed7677bdd51069a04535cff4ba2c5731cea2c7af535`，只作为该次构建证据，不假称跨构建可重复的发布摘要。

7965302的[Docs44002059613](https://github.com/zrayjay/java-for-nestjs/actions/runs/44002059613)成功；Foundation最终状态及后续证据保存增补见[进度](../progress/P5-03.md)。仅P5-03进入in_review，40章等待P5-04，未自动done。

592445f仅增加重启前独立drain.log归档并保存本审查记录；[容器44002591734](https://github.com/zrayjay/java-for-nestjs/actions/runs/44002591734)再次完整通过，作者下载artifact实际核对排空开始/完成日志及五checkpoint，Node真实信号1项通过。[Docs44002591751](https://github.com/zrayjay/java-for-nestjs/actions/runs/44002591751)成功；7965302 Foundation44002059546最终成功。以上后补执行核验归作者，不冒称独立审查者重跑。
