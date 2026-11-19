# K8s 教学部署（P5-04）

仓库根目录执行。复用[Compose教学镜像](../compose/TEACHING.md)（多阶段构建、非root 10001、优雅停止20秒），本清单在同一镜像上交付 Deployment、Service、三类探针、资源请求/限制与滚动更新。集群为kind测试集群，不是生产集群；没有声明TLS、公网入口、生产凭据管理或高可用。

先决：Docker Engine、kubectl，以及kind（或任何能解析集群内DNS的测试集群）。镜像需先构建并载入节点：

```powershell
docker build -f project/deploy/compose/Dockerfile -t java-for-nestjs-teaching:local .
kind load docker-image java-for-nestjs-teaching:local --name p5-teaching
kubectl apply -f project/deploy/k8s/teaching.yaml
kubectl -n teaching get pods -w
```

`teaching`命名空间内创建：`mysql` Deployment（RWO卷、Recreate策略）与同名Service、`keys-init`一次性Job把RSA密钥写入独立PVC、`api` Deployment（startup/liveness/readiness探针、资源限额、`maxUnavailable: 0`）与`api` Service。数据库Service命名为`mysql`且与API同命名空间，因此已验证数据层守卫的固定URL `jdbc:mysql://mysql:3306/data_lab` 在集群内DNS真实解析，不需要放宽任何安全约束。

密钥与数据分别使用独立PVC，节点存储由集群StorageClass提供（kind默认`standard` local-path）。Secret默认值为可丢弃教学密码；任何共享环境先`kubectl create secret`覆盖。PVC保留不是备份。

## 副本与首次启动

api Deployment默认`replicas: 1`：空库首启时演示管理员fixture明确拒绝在非空库上seed，两个副本同时首启会在该插入上竞争。第一个Pod Ready后扩容安全：

```powershell
kubectl -n teaching scale deployment/api --replicas=2
```

## 滚动更新

`maxUnavailable: 0, maxSurge: 1`：新Pod通过readiness前不终止旧Pod。preStop休眠5秒让端点摘除传播，再发SIGTERM，应用20秒排空、40秒grace。同镜像`rollout restart`验证Pod替换机制本身；带新镜像标签的版本发布与回滚演练属P5-05，不在本清单冒充。

```powershell
kubectl -n teaching rollout restart deployment/api
kubectl -n teaching rollout status deployment/api
```

## 自动演练

```powershell
node scripts/verify-k8s-teaching.mjs
```

脚本删除并重建`teaching`命名空间，真实走完：空卷迁移与登录/创建→停MySQL（scale 0）观察readiness 503、liveness 200与Service摘流→恢复→扩到2副本→`rollout restart`期间集群内客户端Pod持续请求Service零失败→旧Pod以0/143优雅退出→新副本仍读到任务。证据写入`target/k8s-evidence`。集群与PVC保留供检查；脚本不删除不是它创建的集群。CI见[k8s.yml](../../../.github/workflows/k8s.yml)，真实结果见[P5-04进度](../../../docs/progress/P5-04.md)。

## 排错

Pod起不来先看`kubectl -n teaching describe pod`与`kubectl -n teaching logs`。`wait-mysql` init容器卡住查MySQL Pod与Secret密码；`wait-keys`卡住查`keys-init` Job日志与PVC权限。滚动卡住查新Pod readiness与DB连接；退出137查grace预算与排空超时。清除全部教学数据：`kubectl delete namespace teaching`（PVC与密钥一并丢弃，旧Token全部失效）。

## 版本发布与回滚演练

使用独立的kind集群，驱动通过`kind-p5-delivery`上下文访问它。仓库根目录执行：

```powershell
kind create cluster --name p5-delivery --image kindest/node:v1.34.0 --wait 180s
docker pull --platform linux/amd64 mysql:8.4.7
docker save --platform linux/amd64 -o target/delivery-mysql-amd64.tar mysql:8.4.7
kind load image-archive target/delivery-mysql-amd64.tar --name p5-delivery
$env:GITHUB_SHA = git rev-parse HEAD
node scripts/verify-k8s-delivery.mjs
```

示例适用于amd64节点。若kind未加入PATH，将`KIND_BIN`设为kind程序的绝对路径。驱动在发现已有`teaching`命名空间时终止，因此每次演练需要干净集群。

`target/delivery-evidence/summary.json`记录三次版本检查、两次切换期间的请求数及失败数，以及回滚后会话和任务验证结果。同目录包含发布历史、Pod与事件记录。检查完毕可用`kind delete cluster --name p5-delivery`清理该专用集群。GitHub Actions先扫描实际参与演练的两份镜像，见[交付工作流](../../../.github/workflows/delivery.yml)和[第41章](../../../docs/08-cloud-native/41-continuous-delivery.md)。
