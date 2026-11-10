package dev.javafornestjs.greeting;

public final class GreetingPolicy {
    public String greet(String name) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Name must be 1-32 ASCII letters, digits, '_' or '-', starting with a letter.");
        }
        return "Hello, " + name + "!";
    }
}
