# 第01、03、04、05章基础实验

从仓库根运行，完整JDK21或25，编译目标release21；Node22.20.0，pnpm10.34.9。无需Spring启动、Docker或数据库。

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl examples/java-basics/runtime-lab -am verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
npx --yes pnpm@10.34.9 foundations:verify
```

Linux将Wrapper换为`./mvnw`。`JAVA_HOME`指向包含java/javac/javap/jar/jdb的完整JDK；验证脚本优先使用同一JAVA_HOME下工具，未设置时按PATH解析。

脚本在runtime-lab的target/foundations下直接调用javac --release21 -g编译MemoryLab，再分别用classpath和带Main-Class的JAR运行。固定结果：`Result[primitive=1, shared=2, sameReference=false, sameText=true]`。Java测试与TS测试也独立断言1/2/false/true；TS的对象文本字段比较并非Java String引用模型的等价实现。

字节码必须major65、含行号和局部变量调试表；不存在主类、错误classpath根两个启动负例必须非零，且ClassNotFoundException指向预期类名。JDK模块例仅检查java.base导出java.lang，不声称验证了自定义模块的反射开放或Spring兼容。

## 手动调试练习

```sh
jdb -classpath examples/java-basics/runtime-lab/target/foundations dev.javafornestjs.runtime.MemoryLab
```

在jdb依次输入`stop in dev.javafornestjs.runtime.MemoryLab.change`、`run`。等到Breakpoint hit后输入`locals`查看primitive=1和box引用，再`next`观察局部副本和共享对象的变化。最后`cont`正常结束；中途停止用`exit`。不要把这些命令输入PowerShell。断点命中前不能声称观察到了暂停变量。

本例在javac层明确-g；IDE要选择实际运行的类和本次构建产物。JDWP调试端口不得无鉴权暴露公网；本地jdb启动子VM不需要给部署环境开放调试端口。

## 内存与GC

MemoryLab只证明传值、共享对象修改和局部重新绑定的语义，不测对象物理地址/对象布局/回收时刻。可回收分配与GC沿用runtime:verify的受控SerialGC实验，见[运行时实验](README.md)。GC可达性与文件/socket关闭是不同问题，本实验不以null赋值证明资源关闭。
