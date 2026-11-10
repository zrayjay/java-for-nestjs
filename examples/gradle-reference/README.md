# Gradle9.2.1任务与缓存对照

独立小实验，不迁移Maven或业务代码。完整JDK21/25、Gradle9.2.1、自动验证需PowerShell7。官方版本/校验来源见[ADR-0005](../../docs/adr/0005-gradle-comparison-runtime.md)；先从官方发行页取得Gradle并核对SHA256，解压后把其bin放当前会话PATH，或把gradle.bat完整路径传给脚本。

仓库根运行：

```powershell
./scripts/verify-gradle-reference.ps1
# 未加入PATH时：
./scripts/verify-gradle-reference.ps1 -GradleCommand 'C:\Tools\gradle-9.2.1\bin\gradle.bat'
```

Linux用`pwsh -NoProfile -File scripts/verify-gradle-reference.ps1`。任务本身不解析任何第三方依赖，可在发行包已就绪后--offline运行；不能把这一点写成“首次下载也不需要网络”。

脚本先核对真实Gradle版本，然后：强制执行generateGreeting，固定输入NestJS得到Hello, NestJS!；同输入再次运行要求UP-TO-DATE；只删除本实验build/greeting.txt，再运行要求FROM-CACHE且内容一致；改变输入Java运行verifyGreeting要求Hello, Java!；最后dry-run要求generateGreeting在依赖它的verifyGreeting之前。缓存依赖Gradle用户目录，不清空用户其他缓存，不执行递归删除。

@CacheableTask、@Input与@OutputFile使此小任务的输入/输出可被追踪；这不是“所有Gradle任务默认可缓存”的证明。这里只验证本地构建缓存和任务依赖，不测远端缓存、configuration cache、Java编译、与Maven速度比较或生产迁移。实际通过结果见[P2-03](../../docs/progress/P2-03.md)与[G2](../../docs/progress/G2.md)，未运行的环境保持未验证。

本机首次默认运行复现已知Windows环回套接字临时路径问题，尚未执行构建即失败；仅本次终端设置`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\JERRY\java-for-nestjs`后成功。该路径是此机器工作区，不是读者应照抄的通用系统设置；先区分实际错误与环境，再按已有G0记录诊断。CI保持默认环境独立验证，不能把本机绕行写成所有Windows默认通过。
