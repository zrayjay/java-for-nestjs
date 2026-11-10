package dev.javafornestjs.ioc;

import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/** Explicitly registered teaching context, outside the greeting application's scan package. */
public final class IocLab {
    private IocLab() { }

    public interface TimeSource {
        String now();
    }

    public static final class FixedTimeSource implements TimeSource {
        @Override
        public String now() { return "09:00"; }
    }

    public static final class Greeter {
        private final TimeSource source;

        public Greeter(TimeSource source) { this.source = source; }

        public String greet() { return "Hello @ " + source.now(); }
    }

    public static final class PrototypeToken {
        private boolean initialized;
        private boolean closed;

        public void initialize() { initialized = true; }
        public void close() { closed = true; }
        public boolean initialized() { return initialized; }
        public boolean closed() { return closed; }
    }

    public record Holder(PrototypeToken token) { }

    public record ProviderHolder(ObjectProvider<PrototypeToken> provider) {
        public PrototypeToken next() { return provider.getObject(); }
    }

    public static final class Events {
        private final List<String> values = new ArrayList<>();

        public List<String> snapshot() { return List.copyOf(values); }
    }

    public interface Session {
        String execute();
        String viaSelf();
    }

    public static final class ManagedResource implements Session {
        private final Events events;
        private boolean open;

        public ManagedResource(Events events) {
            this.events = events;
            events.values.add("constructed");
        }

        public void open() {
            open = true;
            events.values.add("init");
        }

        @Override
        public String execute() {
            if (!open) {
                throw new IllegalStateException("Resource is not open");
            }
            events.values.add("use");
            return "resource-ready";
        }

        @Override
        public String viaSelf() { return execute(); }

        public void close() {
            open = false;
            events.values.add("destroy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        TimeSource timeSource() { return new FixedTimeSource(); }

        @Bean
        Greeter greeter(TimeSource source) { return new Greeter(source); }

        @Bean(initMethod = "initialize", destroyMethod = "close")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        PrototypeToken token() { return new PrototypeToken(); }

        @Bean
        Holder holder(PrototypeToken token) { return new Holder(token); }

        @Bean
        ProviderHolder providerHolder(ObjectProvider<PrototypeToken> provider) {
            return new ProviderHolder(provider);
        }

        @Bean
        Events events() { return new Events(); }

        @Bean(initMethod = "open", destroyMethod = "close")
        Session session(Events events) { return new ManagedResource(events); }

        @Bean
        static BeanPostProcessor tracingPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof ManagedResource resource) {
                        resource.events.values.add("before-init");
                    }
                    return bean;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof ManagedResource resource) {
                        resource.events.values.add("after-init");
                        var factory = new ProxyFactory(resource);
                        factory.addAdvice((MethodInterceptor) invocation -> {
                            if (invocation.getMethod().getName().equals("execute")) {
                                resource.events.values.add("advice");
                            }
                            return invocation.proceed();
                        });
                        return factory.getProxy();
                    }
                    return bean;
                }
            };
        }
    }
}
