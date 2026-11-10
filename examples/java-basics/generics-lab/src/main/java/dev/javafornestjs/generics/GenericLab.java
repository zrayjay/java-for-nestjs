package dev.javafornestjs.generics;

import java.util.List;
import java.util.Objects;

public final class GenericLab {
    private GenericLab() { }

    public static <T> T identity(T value) {
        return value;
    }

    public static <T> void copyValues(List<? extends T> source, List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static <T extends Number & Comparable<? super T>> T maxNatural(T left, T right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
        return left.compareTo(right) >= 0 ? left : right;
    }
}
