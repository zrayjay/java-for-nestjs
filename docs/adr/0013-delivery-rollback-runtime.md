# ADR-0013：P5自动交付与回滚运行物

2026-11-17，P5-04审查闭环后采用。P5-05交付第41章：CI构建测试、镜像扫描、版本追溯与真实部署/回滚演练。不部署生产环境、不发布外部registry镜像、不声明GitLab CI能力；GitHub Actions是本仓库唯一实际交付管道。

教学镜像新增`ARG/ENV BUILD_MARKER`：构建期注入的版本标识默认`unversioned`，运行期可在容器内`printenv BUILD_MARKER`验证。这是镜像内容差异的最小可观测载体；不修改已审查的Java代码与安全守卫。版本追溯链是git提交SHA→镜像标签→容器内marker→Deployment镜像字段，四者可在演练中同时核对。

Trivy Action使用固定版本，分别扫描kind实际加载的v1与v2镜像。门禁涵盖有无上游修复的HIGH/CRITICAL漏洞，允许带理由和到期时间的逐条豁免。CI归档门禁生成的JSON报告，报告的范围由严重级别和豁免规则决定。出现未处理的高危问题或扫描自身失败时，工作流停止在部署之前。

回滚演练在kind集群真实执行：部署v1镜像→登录创建任务→滚动到v2镜像（集群内持续请求零失败、容器内marker变为v2）→`kubectl rollout undo`回滚（marker回到v1、任务与原refresh token仍可用、期间请求零失败）。回滚证据包含rollout历史、前后Deployment镜像字段、marker实测值与业务连续性；不把"undo成功"简化为命令退出码。

第40章K8s清单与演练保持P5-04边界；第41章复用同一镜像与清单，交付物新增delivery工作流与演练脚本。扫描阻断与回滚闭环都有证据后41章才离开planned；G5仍需全部P5任务与独立验收，不在本ADR内宣称。
