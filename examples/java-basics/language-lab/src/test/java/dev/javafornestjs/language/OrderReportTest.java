package dev.javafornestjs.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderReportTest {
    @Test
    void groupsValidatedUniqueOrdersAndSortsTheReportWithoutSpring() {
        var lines = List.of(new OrderReport.Line("o2", "bob", 1), new OrderReport.Line("o1", "alice", 2),
                new OrderReport.Line("o3", "alice", 3));
        assertEquals(List.of("alice:5", "bob:1"), OrderReport.summarize(lines));
        assertEquals(List.of(), OrderReport.summarize(List.of()));
    }

    @Test
    void rejectsInvalidDuplicateOrOverflowingDataRatherThanSilentlyCorruptingTheReport() {
        assertThrows(IllegalArgumentException.class, () -> new OrderReport.Line(null, "alice", 1));
        assertThrows(IllegalArgumentException.class, () -> new OrderReport.Line("o1", "alice", -1));
        var first = new OrderReport.Line("o1", "alice", 1);
        assertThrows(IllegalArgumentException.class, () -> OrderReport.summarize(List.of(first, first)));
        assertThrows(ArithmeticException.class, () -> OrderReport.summarize(List.of(
                new OrderReport.Line("o1", "alice", Integer.MAX_VALUE),
                new OrderReport.Line("o2", "alice", 1))));
    }
}
