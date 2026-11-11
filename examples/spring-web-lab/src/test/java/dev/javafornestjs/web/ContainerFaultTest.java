package dev.javafornestjs.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class ContainerFaultTest {
    interface Port { }
    record Consumer(Port port) { }
    record First(Second second) { }
    record Second(First first) { }
    @TestConfiguration(proxyBeanMethods = false)
    static class Missing {
        @Bean
        Consumer consumer(Port port) { return new Consumer(port); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Cycle {
        @Bean
        First first(Second second) { return new First(second); }
        @Bean
        Second second(First first) { return new Second(first); }
    }
    private boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) { return true; }
        }
        return false;
    }
    @Test
    void missingDependencyFailsForTheSpecificUnregisteredPort() {
        new ApplicationContextRunner().withUserConfiguration(Missing.class).run(context ->
                assertTrue(causedBy(context.getStartupFailure(), NoSuchBeanDefinitionException.class)));
    }
    @Test
    void constructorCycleFailsInsteadOfCreatingPartiallyInitializedPeers() {
        new ApplicationContextRunner().withUserConfiguration(Cycle.class).run(context ->
                assertTrue(causedBy(context.getStartupFailure(), BeanCurrentlyInCreationException.class)));
    }
}
