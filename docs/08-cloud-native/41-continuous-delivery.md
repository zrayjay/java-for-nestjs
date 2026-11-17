---
title: '41 · 自动交付与回滚'
description: 从版本化镜像、阻断式扫描到真实集群滚动升级与回滚闭环。
chapter_id: '41'
teaching_status: draft
baseline: Temurin21.0.13+9 noble / MySQL8.4.7 / kind v1.34.0 / Trivy 0.33.1 Action / GitHub Actions
---

# 41 · 自动交付与回滚

## 章节范围与先修

先完成35/40章。本章在已验证的教学镜像与K8s清单上交付：CI构建测试、镜像漏洞扫描阻断、版本追溯链与真实部署/升级/回滚演练。GitHub Actions是本仓库唯一实际管道，不声称GitLab CI或生产CD能力；不发布外部registry镜像。[操作说明](../../project/deploy/k8s/TEACHING.md)、[交付清单](../../.github/workflows/delivery.yml)、[回滚演练脚本](../../scripts/verify-k8s-delivery.mjs)与[进度证据](../progress/P5-05.md)均可定位。

## 知识点

### ch41-kp01 · 版本化镜像与追溯链

#### 1. 一句话定义

每次交付产出唯一可追溯的镜像：git提交→镜像标签→容器内构建标记→Deployment镜像字段四点可同时核对。

#### 2. 为什么需要它

"最新代码"不是版本；镜像标签可被重指，回滚需要知道回到哪个具体产物。没有构建期标识，线上容器无法回答"我是什么时候的什么代码"。

#### 3. 生活类比

产品批次号对应镜像标签，装箱单对应构建标记：外箱标签可被换贴，箱内单据才是内证。只有两端同时核对，才确认没有调包。

#### 4. Node.js / TypeScript / NestJS 技术对照

Node镜像同样以构建参数注入版本（如`npm version`或git SHA写进环境变量），追溯链概念与语言无关。当前仓库的Node参照仍是[原生HTTP生命周期模型](../../examples/container-node-reference/README.md)，未发布版本化Node镜像；不能据Java链通过声称Node交付链等价。

#### 5. 两端代码对照

镜像构建期注入标记，运行期可观测：

```dockerfile
ARG BUILD_MARKER=unversioned
ENV BUILD_MARKER=${BUILD_MARKER}
```

CI以提交SHA构造标记：`--build-arg BUILD_MARKER=ci-${{ github.sha }}`。演练中`docker run --rm <image> printenv BUILD_MARKER`与`kubectl exec <pod> -- printenv BUILD_MARKER`分别验证镜像内与集群内Pod内的实际值。

#### 6. 原理与流程图

```mermaid
flowchart LR
  G[git提交SHA] --> B[构建参数BUILD_MARKER]
  B --> I[镜像:tag]
  I --> D[Deployment镜像字段]
  D --> P[Pod容器printenv]
  P -.核对.-> G
```

四点核对链上任何一点对不上（标签漂移、缓存复用旧层、set image笔误）都会被演练断言捕获。tag只是指针，摘要才是内容身份；本教学链以标记+标签双保险，生产应以摘要固定。

#### 7. 类比边界与错误直觉

latest标签不能作为版本；同一tag重复推送会静默改变含义。构建标记可被伪造（构建者自己写），不是密码学签名；不可信来源的镜像需要签名与摘要校验，不在本章范围。`unversioned`默认值的存在本身就是警示信号。

#### 8. 企业应用场景与选型

生产交付通常把镜像推到registry并用摘要或不可变tag引用；教学环境用kind本地load跳过registry，追溯链其余环节保持一致。SBOM、签名（cosign）与准入策略是后续演进，当前未声明。

#### 9. 常见错误与排查

Pod内marker与预期不符先查`kubectl rollout history`确认实际部署的revision与镜像字段；本地kind演练记得`kind load`两个tag；缓存命中的旧镜像层不会重新求值ARG，改标记必须传新构建参数。

#### 10. 实操练习与验收

基础题：解释tag与摘要的区别。迁移题：Nest镜像如何注入等价版本信息。思考题：为什么ENV标记可被`kubectl exec`看到而构建秘密不行？执行真实演练见kp03。

### ch41-kp02 · 阻断式镜像扫描

#### 1. 一句话定义

流水线对每次构建产物做漏洞扫描，未修复的HIGH/CRITICAL漏洞使交付失败，而不是事后告警。

#### 2. 为什么需要它

镜像含基础镜像、JRE与依赖三层来源的已知漏洞面；不阻断的扫描报告会被忽略。依赖升级会引入新CVE，阻断让漂移在合入前显形。

#### 3. 生活类比

出厂质检不合格就不得入库，而不是贴张"有瑕疵"的标签继续卖。质检标准（严重级别阈值）是明确契约，不是质检员心情。

#### 4. Node.js / TypeScript / NestJS 技术对照

Node侧对应`pnpm audit --audit-level high`（本仓库已在用）与镜像扫描互补：npm audit看应用依赖，Trivy看整镜像包括基础层。两者通过的集合不等于零漏洞，只是"已知且未修复的高危为零"。

#### 5. 两端代码对照

CI扫描步骤，失败即阻断后续部署：

```yaml
- uses: aquasecurity/trivy-action@0.33.1
  with:
    image-ref: java-for-nestjs-teaching:ci
    exit-code: '1'
    ignore-unfixed: true
    severity: HIGH,CRITICAL
```

JSON报告无论成败都上传为artifact；LOW/MEDIUM数量如实保留在报告中，不隐藏也不阻断。

#### 6. 原理与流程图

构建→扫描→（阻断或放行）→集群演练。扫描器比对镜像内包数据库与CVE源；`ignore-unfixed: true`只关注已有修复版本的漏洞，无法修复的漏洞不阻断但仍出现在报告里。

#### 7. 类比边界与错误直觉

扫描通过不等于无漏洞：未知CVE、LOW/MEDIUM、以及扫描器数据库的时效都是边界。`ignore-unfixed`是务实选择而非放任——上游没补丁的漏洞阻断也没有出路，但升级路径出现时要重新审视。绝不以加白名单换取通过；确需豁免必须单独提交理由，这是仓库明确约定。

#### 8. 企业应用场景与选型

生产通常每日重扫已发布镜像（新CVE会追上旧产物）并把扫描接到准入控制。教学管道只扫当次构建；数据库与策略由Action版本固定。

#### 9. 常见错误与排查

扫描失败先看JSON报告的VulnerabilityID与FixedVersion，优先升级依赖而不是忽略；Trivy数据库下载失败是网络问题不是漏洞问题，重跑即可；基础镜像升级要同步兼容矩阵。

#### 10. 实操练习与验收

基础题：为什么exit-code=1是阻断的关键。迁移题：对照pnpm audit与镜像扫描各自覆盖面。思考题：ignore-unfixed的取舍何时会反过来咬人？完整验收见kp03的CI证据。

### ch41-kp03 · 滚动升级与回滚闭环

#### 1. 一句话定义

升级以滚动替换发布新版本，回滚以`rollout undo`回到上一revision，两者期间业务请求不失败、数据与会话不丢失。

#### 2. 为什么需要它

部署的价值在"坏了能回"。第40章验证了同镜像替换；本章验证真实版本差异（v1/v2内容不同）下的前进与后退，以及回滚后业务连续性。

#### 3. 生活类比

新版手册上架时旧版仍在柜台可用，新版确认可用才撤旧版；发现新版有错，直接从柜库取回上一版，顾客手中的办理凭证仍然有效。回滚恢复的是"曾经正确的版本"，不是时光倒流——期间产生的数据仍在新版数据卷里。

#### 4. Node.js / TypeScript / NestJS 技术对照

Node应用同样依赖编排系统的滚动与回滚；PM2等进程管理器的reload/restart不具备revision历史。当前仓库未部署同题Nest业务到K8s，回滚语义以Java链实测为准。

#### 5. 两端代码对照

演练核心命令链：

```sh
kubectl -n teaching set image deployment/api api=java-for-nestjs-teaching:v2
kubectl -n teaching rollout status deployment/api
kubectl -n teaching rollout undo deployment/api
kubectl -n teaching rollout history deployment/api
```

断言不只看退出码：Deployment镜像字段从v1→v2→v1、Pod内`printenv BUILD_MARKER`随之变化、两次滚动期间集群内客户端对Service持续请求零失败、回滚后用v1期间的原refresh token仍能换新令牌读到v1期间创建的任务。

#### 6. 原理与流程图

```mermaid
sequenceDiagram
  participant D as 演练脚本
  participant K as kind集群
  participant L as 集群内客户端
  D->>K: 部署v1，登录建任务
  D->>L: 启动持续请求
  D->>K: set image v2
  K->>K: maxSurge新Pod就绪后撤旧Pod
  D->>K: undo
  K->>K: 回到revision v1镜像
  D->>L: 请求零失败
  D->>D: 原refresh token读任务成功
```

undo利用ReplicaSet历史，秒级回到已知good revision；数据在PVC与MySQL中跨版本保留。会话/密钥由keys PVC承载，与镜像版本正交。

#### 7. 类比边界与错误直觉

回滚应用不回滚数据：v2期间写入的数据在回滚后仍在，若v2做了不兼容schema迁移，undo救不了——需要前向修复或数据回滚，这是数据库迁移章节的契约（先兼容后破坏）。零失败依赖就绪门禁与摘除传播，恶意慢请求或预算耗尽仍可能失败。revision历史有限（默认10），不是无限后悔药。

#### 8. 企业应用场景与选型

生产把"回滚演练"作为发布前置检查：不能回滚的变更不上线。金丝雀/蓝绿是滚动更新的演进，教学规模未引入。恢复目标（RTO）以实测演练为准，不写空头承诺。

#### 9. 常见错误与排查

undo后marker没变查rollout history确认revision顺序；滚动卡住查新Pod readiness与镜像load；零失败断言失败先分辨摘除传播间隙（短暂503/超时）与真实错误；回滚后Token失效查keys PVC是否被删——密钥与会话都靠它。

#### 10. 实操练习与验收

基础题：解释revision与ReplicaSet的关系。迁移题：对照PM2 reload与Deployment滚动。思考题：什么schema变更会让undo失效？kind集群执行：

```powershell
node scripts/verify-k8s-delivery.mjs
```

要求零失败完成：双marker镜像构建并验证、v1部署登录建任务、v2滚动零失败且marker实测为v2、undo后镜像与marker回到v1且零失败、原refresh token读回任务。CI见[delivery.yml](../../.github/workflows/delivery.yml)，真实证据见[P5-05](../progress/P5-05.md)；扫描阻断与回滚闭环都通过才算本章交付。

## 练习提示与官方资料

先区分"发布完成"与"业务验证通过"，再谈自动化程度。回滚不是失败兜底的遮羞布，是发布策略的一部分。

- [Deployment回滚](https://kubernetes.io/zh-cn/docs/concepts/workloads/controllers/deployment/#rollback-to-a-previous-revision)
- [Trivy Action](https://github.com/aquasecurity/trivy-action)
- [镜像与漏洞扫描](https://docs.github.com/zh-cn/code-security/supply-chain-security)
- [pnpm audit](https://pnpm.io/cli/audit)
- [ADR0013](../adr/0013-delivery-rollback-runtime.md)
