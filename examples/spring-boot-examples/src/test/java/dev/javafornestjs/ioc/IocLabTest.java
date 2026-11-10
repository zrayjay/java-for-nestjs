package dev.javafornestjs.ioc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class IocLabTest {
    @Test
    void constructorWiringAndSingletonArePerContainer() {
        try (var first = new AnnotationConfigApplicationContext(IocLab.Config.class);
                var second = new AnnotationConfigApplicationContext(IocLab.Config.class)) {
            var greeter = first.getBean(IocLab.Greeter.class);
            assertEquals("Hello @ 09:00", greeter.greet());
            assertSame(greeter, first.getBean(IocLab.Greeter.class));
            assertNotSame(greeter, second.getBean(IocLab.Greeter.class));
        }
    }

    @Test
    void missingConstructorDependencyFailsDuringRefresh() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IocLab.Greeter.class);
            assertThrows(UnsatisfiedDependencyException.class, context::refresh);
        }
    }

    @Test
    void prototypeIsNewPerLookupButInjectedOnlyOnceIntoSingleton() {
        try (var context = new AnnotationConfigApplicationContext(IocLab.Config.class)) {
            var first = context.getBean(IocLab.PrototypeToken.class);
            var second = context.getBean(IocLab.PrototypeToken.class);
            assertNotSame(first, second);
            assertTrue(first.initialized());
            var holder = context.getBean(IocLab.Holder.class);
            assertSame(holder.token(), context.getBean(IocLab.Holder.class).token());
            var provider = context.getBean(IocLab.ProviderHolder.class);
            assertNotSame(provider.next(), provider.next());
        }
    }

    @Test
    void prototypeDestructionIsTheCallersResponsibility() {
        IocLab.PrototypeToken token;
        try (var context = new AnnotationConfigApplicationContext(IocLab.Config.class)) {
            token = context.getBean(IocLab.PrototypeToken.class);
        }
        assertFalse(token.closed());
        token.close();
        assertTrue(token.closed());
    }

    @Test
    void initializationPostProcessingProxyAndDestructionAreObservable() {
        IocLab.Events events;
        try (var context = new AnnotationConfigApplicationContext(IocLab.Config.class)) {
            events = context.getBean(IocLab.Events.class);
            assertEquals(List.of("constructed", "before-init", "init", "after-init"), events.snapshot());
            var session = context.getBean(IocLab.Session.class);
            assertTrue(Proxy.isProxyClass(session.getClass()));
            assertEquals("resource-ready", session.execute());
            assertEquals(List.of("constructed", "before-init", "init", "after-init", "advice", "use"),
                    events.snapshot());
            assertEquals("resource-ready", session.viaSelf());
            assertEquals(List.of("constructed", "before-init", "init", "after-init", "advice", "use", "use"),
                    events.snapshot());
        }
        assertEquals(List.of("constructed", "before-init", "init", "after-init", "advice", "use", "use", "destroy"),
                events.snapshot());
    }
}
