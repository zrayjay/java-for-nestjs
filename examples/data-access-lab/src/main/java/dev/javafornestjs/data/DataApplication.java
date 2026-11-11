package dev.javafornestjs.data;

import dev.javafornestjs.web.ApiApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@Import({ApiApplication.class, DataConfiguration.class})
public class DataApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(DataApplication.class);
        application.setAdditionalProfiles("data");
        application.run(args);
    }
}
