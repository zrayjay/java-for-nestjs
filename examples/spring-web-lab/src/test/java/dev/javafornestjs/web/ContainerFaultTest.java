package dev.javafornestjs.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    record FirstClient(Port port) { }
    record SecondClient(Port port) { }
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
    @TestConfiguration(proxyBeanMethods = false)
    static class RepairedCycle {
        @Bean
        Port port() { return new Port() { }; }
        @Bean
        FirstClient firstClient(Port port) { return new FirstClient(port); }
        @Bean
        SecondClient secondClient(Port port) { return new SecondClient(port); }
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
        new ApplicationContextRunner().withUserConfiguration(Missing.class)
                .withBean(Port.class, () -> new Port() { }).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(context.getBean(Port.class), context.getBean(Consumer.class).port());
                });
    }
    @Test
    void constructorCycleFailsInsteadOfCreatingPartiallyInitializedPeers() {
        new ApplicationContextRunner().withUserConfiguration(Cycle.class).run(context ->
                assertTrue(causedBy(context.getStartupFailure(), BeanCurrentlyInCreationException.class)));
        // Repair the graph: both clients depend on one shared port, not on each other.
        new ApplicationContextRunner().withUserConfiguration(RepairedCycle.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertSame(context.getBean(FirstClient.class).port(), context.getBean(SecondClient.class).port());
        });
    }
}
