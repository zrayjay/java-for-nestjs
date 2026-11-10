package dev.javafornestjs.cli;

import dev.javafornestjs.greeting.GreetingPolicy;

public final class GreetingCli {
    private GreetingCli() { }

    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected zero or one learner name.");
        }
        System.out.println(new GreetingPolicy().greet(args.length == 0 ? "NestJS" : args[0]));
    }
}
