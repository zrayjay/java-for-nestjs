package dev.javafornestjs.runtime;

public final class MemoryLab {
    private MemoryLab() { }

    public static final class Box {
        private int value;
        public Box(int value) { this.value = value; }
        public int value() { return value; }
    }

    public record Result(int primitive, int shared, boolean sameReference, boolean sameText) { }

    private static void change(int primitive, Box box) {
        primitive = 99;
        box.value = 2;
        box = new Box(99);
    }

    public static Result observe() {
        int primitive = 1;
        Box original = new Box(1);
        Box alias = original;
        change(primitive, alias);
        String first = new String(new char[] {'o', 'k'});
        String second = new String(new char[] {'o', 'k'});
        return new Result(primitive, original.value(), first == second, first.equals(second));
    }

    public static void main(String[] args) {
        System.out.println(observe());
    }
}
