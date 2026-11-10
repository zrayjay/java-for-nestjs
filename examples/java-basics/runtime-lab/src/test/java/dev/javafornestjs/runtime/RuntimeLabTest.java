package dev.javafornestjs.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimeLabTest {
    @Test
    void arithmeticHasExplicitInt32Semantics() {
        assertEquals(38, RuntimeLab.mix(1));
        assertEquals(114, RuntimeLab.checksum(3));
        assertEquals(2147483624, RuntimeLab.mix(Integer.MAX_VALUE));
    }

    @Test
    void controlledInterleavingExposesLostUpdateAndAtomicAlternative() throws Exception {
        assertEquals(new RuntimeLab.RaceResult(1, 2), RuntimeLab.sharedCounter());
    }
}
