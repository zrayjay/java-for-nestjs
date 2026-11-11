package dev.javafornestjs.data;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
public class SqlCounter implements Interceptor {
    private final AtomicInteger statements = new AtomicInteger();
    public void reset() { statements.set(0); }
    public int count() { return statements.get(); }
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        statements.incrementAndGet();
        return invocation.proceed();
    }
}
