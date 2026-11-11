package dev.javafornestjs.security;

import dev.javafornestjs.data.DataApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({DataApplication.class, SecurityConfiguration.class, AuthController.class, SecurityErrors.class})
public class SecurityApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(SecurityApplication.class);
        application.setAdditionalProfiles("data", "security");
        application.run(args);
    }
}
