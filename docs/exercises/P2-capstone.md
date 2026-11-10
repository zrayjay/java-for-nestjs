# P2综合练习：无框架订单数量汇总

目标：只用Java标准库完成输入不变量、去重、分组、检查溢出和排序；TS端实现同一外部契约。无需Spring、HTTP或数据库。数量不是货币，不代表生产订单状态/权限/支付模型。

输入三条：o2/bob/1、o1/alice/2、o3/alice/3，预期按客户排序输出`[alice:5,bob:1]`。输入首次客户顺序故意不同于输出，确保排序断言不能被插入顺序偶然满足。空列表输出空；ID/客户名为1~16个小写ASCII字母数字或横线且以字母起始；数量为0~2147483647整数；重复订单ID、非法数据、同客户累计超int上限均拒绝，不静默覆盖或溢出。重复策略是拒绝，不是生产幂等策略的通用结论。

先完成：

1. Java用record构造器维护行不变量；用Set核对ID、Map分组、检查加法，输出稳定排序。
2. TS显式验证number整数与int范围；不能依赖TS静态类型消除全部输入错误，也不能用普通number加法直接模仿Java int溢出。
3. 为正常、空、负数、重复和溢出写断言；说明是否修改输入、何时部分结果不可外泄。

运行命令与环境见[语言实验](../../examples/java-basics/language-lab/README.md)。本地实际测试与独立验收另记[G2](../progress/G2.md)，不能只因答案存在就宣布读者已独立完成。

<details><summary>完成后查看参考实现与解释</summary>

[Java OrderReport](../../examples/java-basics/language-lab/src/main/java/dev/javafornestjs/language/OrderReport.java)及[测试](../../examples/java-basics/language-lab/src/test/java/dev/javafornestjs/language/OrderReportTest.java)；[TS参照](../../examples/nestjs-reference/src/language/order-report.ts)及[测试](../../examples/nestjs-reference/src/language/order-report.test.ts)。

Java用Math.addExact拒绝超范围，TS需显式上限检查；两端异常类型不要求同名。TreeMap与TS显式字典序排序实现本例小写ASCII客户契约，不扩写为自然语言地区排序。汇总使用局部状态，失败时不返回部分报告；这不是数据库事务保证，也没有跨进程副作用。

</details>
