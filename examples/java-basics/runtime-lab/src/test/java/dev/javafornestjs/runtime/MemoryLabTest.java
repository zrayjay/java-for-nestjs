package dev.javafornestjs.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MemoryLabTest {
    @Test
    void passesValuesAndCopiesReferencesWithoutRebindingTheCaller() {
        assertEquals(new MemoryLab.Result(1, 2, false, true), MemoryLab.observe());
    }
}
