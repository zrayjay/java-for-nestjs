package dev.javafornestjs.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

class ProxyModelTest {
    public interface Work {
        String execute();
        String self();
    }
    public static class Worker implements Work {
        private final List<String> events;
        public Worker(List<String> events) { this.events = events; }
        @Override
        public String execute() {
            events.add("work");
            return "ok";
        }
        @Override
        public String self() { return execute(); }
    }
    public static final class FinalWorker { public String execute() { return "ok"; } }

    @Test
    void realJdkAndCglibProxiesBothLeaveTargetSelfInvocationOutsideAdvice() {
        for (boolean classProxy : List.of(false, true)) {
            var events = new ArrayList<String>();
            var factory = new ProxyFactory(new Worker(events));
            factory.setInterfaces(Work.class);
            factory.setProxyTargetClass(classProxy);
            factory.addAdvice((MethodInterceptor) invocation -> {
                events.add("advice:" + invocation.getMethod().getName());
                return invocation.proceed();
            });
            var proxy = (Work) factory.getProxy();
            assertEquals(classProxy, AopUtils.isCglibProxy(proxy));
            assertEquals(!classProxy, Proxy.isProxyClass(proxy.getClass()));
            assertEquals("ok", proxy.execute());
            assertEquals("ok", proxy.self());
            assertEquals(List.of("advice:execute", "work", "advice:self", "work"), events);
            if (classProxy) { assertTrue(proxy instanceof Worker); }
            else { assertFalse(proxy instanceof Worker); }
        }
    }

    @Test
    void finalClassCannotBeSubclassProxied() {
        var factory = new ProxyFactory(new FinalWorker());
        factory.setProxyTargetClass(true);
        assertThrows(AopConfigException.class, factory::getProxy);
    }
}
