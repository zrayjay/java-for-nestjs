package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.system.AccountsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountBootstrap {
    @Bean
    ApplicationRunner bootstrapAdministrator(AccountsService accounts,
            @Value("${capstone.bootstrap.username}") String username,
            @Value("${capstone.bootstrap.password}") String password) {
        return args -> accounts.bootstrap(username, password);
    }
}
