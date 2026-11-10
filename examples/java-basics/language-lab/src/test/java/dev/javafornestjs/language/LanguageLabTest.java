package dev.javafornestjs.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LanguageLabTest {
    @Test
    void intArithmeticAndUnboxingHaveRealRuntimeBoundaries() {
        int maximum = Integer.MAX_VALUE;
        assertEquals(Integer.MIN_VALUE, maximum + 1);
        assertThrows(ArithmeticException.class, () -> Math.addExact(maximum, 1));
        assertThrows(NullPointerException.class, () -> LanguageLab.unbox(null));
    }

    @Test
    void recordCopiesMutableInputAndUsesComponentEquality() {
        var roles = new ArrayList<>(List.of("reader"));
        var account = new LanguageLab.Account("u1", roles);
        roles.add("admin");
        assertEquals(List.of("reader"), account.roles());
        assertEquals(new LanguageLab.Account("u1", List.of("reader")), account);
        assertThrows(UnsupportedOperationException.class, () -> account.roles().add("admin"));
        assertThrows(IllegalArgumentException.class, () -> new LanguageLab.Account(" ", roles));
    }

    @Test
    void inheritanceAndSealedSwitchHaveDifferentResponsibilities() {
        LanguageLab.Rule rule = new LanguageLab.MinimumRule(10);
        assertEquals("minimum", rule.name());
        assertTrue(rule.accepts(10));
        assertEquals("card:1234", LanguageLab.describe(new LanguageLab.Card("1234")));
        assertEquals("cash:20", LanguageLab.describe(new LanguageLab.Cash(20)));
    }

    @Test
    void valueKeysAndSnapshotAreStableAgainstInputMutation() {
        var roles = new ArrayList<>(List.of("reader"));
        var key = new LanguageLab.Account("u1", roles);
        var balances = new HashMap<LanguageLab.Account, Integer>();
        balances.put(key, 20);
        roles.add("admin");
        var equivalent = new LanguageLab.Account("u1", List.of("reader"));
        assertEquals(20, LanguageLab.lookup(balances, equivalent));
        assertEquals(1, new HashSet<>(List.of(key, equivalent)).size());
        var copy = LanguageLab.snapshot(roles);
        roles.clear();
        assertEquals(List.of("reader", "admin"), copy);
        assertThrows(UnsupportedOperationException.class, () -> copy.add("other"));
    }

    @Test
    void pipelineAndOptionalFollowExplicitContracts() {
        assertEquals(60, LanguageLab.total(List.of(1, 10, 20)));
        var account = new LanguageLab.Account("u1", List.of());
        var found = LanguageLab.find(List.of(account), "u1");
        assertEquals("u1", found.orElseThrow());
        assertTrue(LanguageLab.find(List.of(account), "missing").isEmpty());
        var calls = new AtomicInteger();
        assertEquals("u1", found.orElseGet(() -> {
            calls.incrementAndGet();
            return "fallback";
        }));
        assertEquals(0, calls.get());
        assertEquals("u1", found.orElse(fallback(calls)));
        assertEquals(1, calls.get());
    }

    private static String fallback(AtomicInteger calls) {
        calls.incrementAndGet();
        return "fallback";
    }

    @Test
    void streamIsLazyAndCannotBeReusedAfterTerminalOperation() {
        var calls = new AtomicInteger();
        var stream = List.of(1, 2).stream().map(value -> {
            calls.incrementAndGet();
            return value * 2;
        });
        assertEquals(0, calls.get());
        assertEquals(List.of(2, 4), stream.toList());
        assertEquals(2, calls.get());
        assertThrows(IllegalStateException.class, stream::toList);
    }

    @Test
    void resourceClosesAndSecondaryCloseErrorIsSuppressed() throws Exception {
        assertEquals(List.of("body", "close"), LanguageLab.closeTrace(false, false));
        var failure = assertThrows(IOException.class, () -> LanguageLab.closeTrace(true, true));
        assertEquals("body-failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close-failure", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void runtimeAnnotationMetadataMustBeReadExplicitly() {
        var tag = LanguageLab.TaggedRule.class.getAnnotation(LanguageLab.Tag.class);
        assertNotNull(tag);
        assertEquals("risk", tag.value());
    }

    @Test
    void virtualThreadsAreActualThreadExecutionsWithControlledOverlap() throws Exception {
        var result = ConcurrencyLab.virtualTasks();
        assertEquals(List.of(10, 20), result.values());
        assertTrue(result.bothVirtual());
    }

    @Test
    void waitingTimeoutDoesNotAutomaticallyCancelTheTask() throws Exception {
        assertTrue(ConcurrencyLab.timeoutDoesNotCancel());
    }

    @Test
    void futureFailureCarriesTheOriginalCause() throws Exception {
        var cause = ConcurrencyLab.failureCause();
        assertEquals(IllegalArgumentException.class, cause.getClass());
        assertEquals("bad-input", cause.getMessage());
    }
}
