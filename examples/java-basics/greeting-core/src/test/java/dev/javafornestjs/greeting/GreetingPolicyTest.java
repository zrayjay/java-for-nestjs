package dev.javafornestjs.greeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GreetingPolicyTest {
    private final GreetingPolicy policy = new GreetingPolicy();

    @Test
    void producesTheSharedGreeting() {
        assertEquals("Hello, NestJS!", policy.greet("NestJS"));
        assertEquals("Hello, reader_21!", policy.greet("reader_21"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " reader", "reader\n", "1reader", "读者", "abcdefghijklmnopqrstuvwxyzABCDEFG"})
    void rejectsNamesOutsideTheSharedContract(String name) {
        assertThrows(IllegalArgumentException.class, () -> policy.greet(name));
    }
}
