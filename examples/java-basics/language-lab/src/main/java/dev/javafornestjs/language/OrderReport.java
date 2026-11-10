package dev.javafornestjs.language;

import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

public final class OrderReport {
    private OrderReport() { }

    public record Line(String id, String customer, int quantity) {
        public Line {
            if (id == null || !id.matches("[a-z][a-z0-9-]{0,15}")
                    || customer == null || !customer.matches("[a-z][a-z0-9-]{0,15}") || quantity < 0) {
                throw new IllegalArgumentException("invalid order line");
            }
        }
    }

    public static List<String> summarize(List<Line> lines) {
        var ids = new HashSet<String>();
        var totals = new TreeMap<String, Integer>();
        for (Line line : lines) {
            if (!ids.add(line.id())) {
                throw new IllegalArgumentException("duplicate order id: " + line.id());
            }
            totals.merge(line.customer(), line.quantity(), Math::addExact);
        }
        return totals.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList();
    }
}
