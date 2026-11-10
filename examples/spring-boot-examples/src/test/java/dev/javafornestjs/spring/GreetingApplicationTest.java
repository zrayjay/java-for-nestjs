package dev.javafornestjs.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GreetingApplicationTest {
    @Test
    void assembledSpringContextUsesTheSharedPolicy() {
        try (var context = GreetingApplication.start()) {
            var service = context.getBean(GreetingService.class);
            assertEquals("Hello, NestJS!", service.greet("NestJS"));
            assertThrows(IllegalArgumentException.class, () -> service.greet("1reader"));
        }
    }
}
