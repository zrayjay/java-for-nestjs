package dev.javafornestjs.spring;

import dev.javafornestjs.greeting.GreetingPolicy;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    private final GreetingPolicy policy;

    public GreetingService(GreetingPolicy policy) {
        this.policy = policy;
    }

    public String greet(String name) {
        return policy.greet(name);
    }
}
