# P4集成测试章节：两轴独立审查

2026-11-16。固定范围`b8675f8...17bccf9`，只读审查者p2_foundation_standards、p2_foundation_spec，覆盖35章三点及新增MockMvc。不含36/37或G4。

## Standards

通过，无需修订的实质问题。逐点十段、类比映射反例、真实代码和命令齐备；准确区分MockMvc调用不经TCP与类仍启动服务器、类共享容器与每例隔离、Node实际fetch与Supertest、共同黑盒合同与完整schema。新增MockMvc正反例及最终行数与正文一致。

审查者实际运行结构检查，未重跑HTTP/容器；跨环境证据由作者提供。章末待验文字按执行证据更新。

## Spec

通过，无新增缺失、错误或越界。C35的MockMvc、Testcontainers、API契约和数据隔离有真实运行物：MockMvc有效创建和精确400、不写无效数据；真实MySQL/Redis/Flyway及事务回滚；两独立打包进程同题合同。没有将内存任务合同扩写为安全、文件或SQL合同。

审查者只读核对源码和文本，未执行测试。

## 每知识点评分

五维通俗/准确/映射/边界/迁移，各0~2。分数不是读者学习测量。

| 知识点 | Standards | Spec |
|---|---|---|
| ch35-kp01 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch35-kp02 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |
| ch35-kp03 | 2/2/2/2/2=10 | 2/2/2/2/2=10 |

## 执行证据与结论

[Foundation43818392057](https://github.com/zrayjay/java-for-nestjs/actions/runs/43818392057)四环境完整成功，包含新增MockMvc和两进程HTTP合同；[Compatibility43818595517](https://github.com/zrayjay/java-for-nestjs/actions/runs/43818595517)在Linux Boot3/Boot4×Temurin21/25四job完整成功。作者实际核对两个Boot3日志，各4项、无失败/错误/跳过、BUILD SUCCESS。Boot4仅小探针对照。

本机TEMP工具丢失后的首次命令失败，重建Temurin21后Spring Web最终17项verify通过，含新增MockMvc；命令和边界见P4-02进度，不将失败阶段写成通过。第35章in_review，未自动done。
