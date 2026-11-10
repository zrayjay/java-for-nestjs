package dev.javafornestjs.language;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

public final class LanguageLab {
    private LanguageLab() { }

    public record Account(String id, List<String> roles) {
        public Account {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            roles = List.copyOf(roles);
        }
    }

    public sealed interface Payment permits Card, Cash { }
    public record Card(String lastFour) implements Payment { }
    public record Cash(int units) implements Payment { }

    public interface Named { String name(); }
    public abstract static class Rule implements Named {
        protected final String label;
        protected Rule(String label) { this.label = label; }
        @Override
        public String name() { return label; }
        public abstract boolean accepts(int amount);
    }
    public static final class MinimumRule extends Rule {
        private final int minimum;
        public MinimumRule(int minimum) {
            super("minimum");
            this.minimum = minimum;
        }
        @Override
        public boolean accepts(int amount) { return amount >= minimum; }
    }

    public static String describe(Payment payment) {
        return switch (payment) {
            case Card card -> "card:" + card.lastFour();
            case Cash cash -> "cash:" + cash.units();
        };
    }

    public static int unbox(Integer value) { return value; }

    public static List<String> snapshot(List<String> input) { return List.copyOf(input); }

    public static int lookup(Map<Account, Integer> balances, Account key) {
        return balances.getOrDefault(key, 0);
    }

    public static int total(List<Integer> amounts) {
        IntUnaryOperator doubleAmount = value -> value * 2;
        return amounts.stream().filter(value -> value >= 10)
                .mapToInt(doubleAmount::applyAsInt).sum();
    }

    public static Optional<String> find(List<Account> accounts, String id) {
        return accounts.stream().filter(account -> account.id().equals(id)).map(Account::id).findFirst();
    }

    public static List<String> closeTrace(boolean bodyFails, boolean closeFails) throws IOException {
        var trace = new ArrayList<String>();
        try (var resource = new TraceResource(trace, closeFails)) {
            trace.add("body");
            if (bodyFails) {
                throw new IOException("body-failure");
            }
        }
        return trace;
    }

    private record TraceResource(List<String> trace, boolean fails) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            trace.add("close");
            if (fails) {
                throw new IOException("close-failure");
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Tag { String value(); }

    @Tag("risk")
    public static final class TaggedRule { }
}
