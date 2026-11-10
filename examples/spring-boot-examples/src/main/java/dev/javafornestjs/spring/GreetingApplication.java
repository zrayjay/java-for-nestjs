package dev.javafornestjs.spring;

import dev.javafornestjs.greeting.GreetingPolicy;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GreetingApplication {
    @Bean
    GreetingPolicy greetingPolicy() {
        return new GreetingPolicy();
    }

    public static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(GreetingApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run();
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected zero or one learner name.");
        }
        try (var context = start()) {
            System.out.println(context.getBean(GreetingService.class)
                    .greet(args.length == 0 ? "NestJS" : args[0]));
        }
    }
}
