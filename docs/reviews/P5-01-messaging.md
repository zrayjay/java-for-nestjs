# P5-01 / 第38章独立审查

2026-11-16，固定点`efa332a`，源码/正文复核至`44b8ec9`；后续`754a9d2`仅补兼容检查测试夹具中的新增文件。作者root；只读独立审查者`p2_foundation_standards`及`p2_foundation_spec`。

## Standards

早期发现一项P2：Node open/close在保护区外获取channel，失败会漏关connection。修后获取放在try内，open失败和close的finally均关闭连接。新增真实连接上仅注入createChannel失败并断言真实close调用，保留代理/SQL实验。

全文结论：第38章范围通过，无新增硬缺陷。十段式、映射、流程图、三级练习完整；明确basicGet实验、classic DLX风险和Outbox未交付。审查者实际运行结构检查及Node无服务合同1项，未运行真实Broker/MySQL；真实服务依据作者另行核对的CI。

## Spec

早期发现双端编码不一致：Java Jackson字节入口自动识别UTF16/32，Node固定UTF8。Java改严格UTF8再解析；两端覆盖坏UTF8/UTF16拒绝及UTF8 BOM正例。全文复核无剩余阻断，正文、README、映射和进度一致，确认/幂等/三次重试链语义不夸大。审查者没有独立查询CI或运行服务。

## 逐知识点评分

五维依次通俗/准确/映射/边界/迁移，各0~2；两轴独立给出相同评分，不代表真实读者效果。

| 知识点 | Standards | Spec |
|---|---|---|
| ch38-kp01 确认边界 | 2/2/2/2/2 | 2/2/2/2/2 |
| ch38-kp02 提交与幂等 | 2/2/2/2/2 | 2/2/2/2/2 |
| ch38-kp03 重试死信 | 2/2/2/2/2 | 2/2/2/2/2 |

## 执行证据及状态

修订后`44b8ec9`的[消息43865152211](https://github.com/zrayjay/java-for-nestjs/actions/runs/43865152211)Temurin21/25均完整成功；作者通过jobs API下载两个job日志，分别确认Java7（2合同+5服务）/Node7（1合同+6服务），0失败/错误/跳过，包括新增连接失败清理。首轮4a4b98c的7/6没有替代该证据。

新增版本检查依赖的测试夹具未同步，导致44b8ec9的Docs43865152184失败，已在754a9d2补入三条真实文件路径；本机工具23项及114页面/锚点通过。新Docs/基础CI结果另见进度，未消除旧失败记录。章节进入in_review仍需适用CI通过，不自动done或G5通过。
