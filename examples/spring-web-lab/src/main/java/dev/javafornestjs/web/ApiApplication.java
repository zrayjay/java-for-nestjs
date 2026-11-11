package dev.javafornestjs.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(ApiProperties.class)
public class ApiApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ApiApplication.class, args);
        if (context instanceof ServletWebServerApplicationContext web) {
            System.out.println("JAVA_API_PORT=" + web.getWebServer().getPort());
        }
    }
}
