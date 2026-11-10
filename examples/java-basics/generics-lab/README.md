# 第08章泛型实验

仓库根执行，使用完整JDK21或25、Node22.20.0及锁定依赖；Windows将`./mvnw`替换为`.\mvnw.cmd`。

```sh
./mvnw --batch-mode --no-transfer-progress -pl examples/java-basics/generics-lab -am verify
npx --yes pnpm@10.34.9 examples:build
npx --yes pnpm@10.34.9 examples:test
```

不需要Docker。Java9项测试覆盖PECS复制、交集上界、运行时擦除/声明元数据、raw type堆污染及6个真实编译用例（1正例、5负例）。负例分别为不变List赋值、extends写入、相同擦除重载、基本类型实参、违反Number上界；通过标准是javac实际拒绝且产生ERROR诊断。所有夹具都在JUnit临时目录，不把故意错误源码放入正常生产编译目录。

TS新增3项运行测试，验证同题复制、可变数组赋值的类型漏洞和unknown输入守卫。`compileOnlyErrors`中的两个`@ts-expect-error`由真实tsc检查，不能执行该函数；若预期错误消失，tsc应报未使用指令而使构建失败。

生产演示只使用受约束API；raw type及故意污染只出现在明确标记的测试反例。反射检查区分普通对象类、擦除后的方法签名和声明处的Signature元数据，不说“所有泛型信息都不存在”。

所有主线CI组合运行同一Maven verify和TS build/test。实际结果、范围和逐知识点评分见[第08章](../../../docs/02-java-language/08-generics.md)及其验证记录；不把单测通过当成真实外部输入验证或生产安全保证。
