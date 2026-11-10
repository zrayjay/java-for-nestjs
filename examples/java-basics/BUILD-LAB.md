# 第13~16章构建与模块实验

仓库根，完整JDK21/25、Maven Wrapper3.9.11、Node22.20.0、pnpm10.34.9；自动元数据检查另需PowerShell7（CI已有pwsh）。Gradle任务/缓存另有[独立小实验](../gradle-reference/README.md)，不迁移Maven业务构建，不做相对性能保证。

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/java-basics/greeting-cli -am clean verify
java -jar examples/java-basics/greeting-cli/target/greeting-cli-0.1.0-SNAPSHOT.jar NestJS
./scripts/verify-build-model.ps1
npx --yes pnpm@10.34.9 chapters:test
```

Linux改为`./mvnw`；自动脚本用`pwsh -NoProfile -File ./scripts/verify-build-model.ps1`。没有pwsh时，可手动执行以下两条Maven命令，生成相同证据，但不会自动执行PowerShell的XML断言；不要因此声称完整自动守卫通过。

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/java-basics/greeting-core org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree -DoutputType=json -DoutputFile=target/dependency-tree.json
./mvnw --batch-mode --no-transfer-progress -pl examples/java-basics/greeting-core org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom -Doutput=target/effective-pom.xml
```

文件均在greeting-core/target中，不提交生成的完整有效POM。自动脚本XPath核对有效java.version与maven.compiler.release属性均21，若有显式插件release覆盖也须21；属性绑定不一定展开成插件configuration/release XML节点，所以另用javap确认实际产物major65。读取真实依赖树拒绝core的非test Spring依赖，再用同一JDK的jdeps -s核对已构建core JAR只依赖java.base。守卫不是覆盖所有架构规则的完整ArchUnit替代品，仍需审查模块职责、接口和依赖方向。

测试夹具验证嵌套运行时Spring泄漏会被拒绝，错误模块或空树不能代替真实元数据。真实树中JUnit及其传递依赖均为test范围；Boot父POM的版本管理不会自动让core JAR加载Spring。

对照命令：`npx --yes pnpm@10.34.9 --filter @java-for-nestjs/nestjs-reference build`只选择对应workspace脚本；它不等于Maven -am自动选上游。此TS项目没有额外内部workspace上游，不能用它声称已测试pnpm任意多模块依赖图。跨端问候共同契约仍由examples:verify验证。

缓存边界：本机已填充TEMP的Maven仓库，`-Offline -LocalRepository`只用于同一缓存。干净CI不用-o，实际下载固定插件3.8.1/3.5.1。Maven本地仓库缓存不是构建输出缓存，也不是pnpm-lock.yaml那样的完整依赖图锁文件。不要用install成功代替干净Reactor构建的证据。
