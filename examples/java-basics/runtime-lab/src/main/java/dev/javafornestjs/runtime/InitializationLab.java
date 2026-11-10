package dev.javafornestjs.runtime;

public final class InitializationLab {
    private static int initializations;

    private InitializationLab() { }

    private static int initialize() {
        initializations++;
        return 42;
    }

    private static final class Lazy {
        private static final int VALUE = initialize();
    }

    public static void main(String[] args) {
        Class<?> type = Lazy.class;
        int before = initializations;
        int first = Lazy.VALUE;
        int second = Lazy.VALUE;
        System.out.println("INITIALIZATIONS=" + before + "," + initializations
                + ";VALUES=" + first + "," + second + ";TYPE=" + type.getSimpleName());
    }
}
