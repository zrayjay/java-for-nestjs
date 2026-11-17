package dev.javafornestjs.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit disposable container fixture, never active in the ordinary security entry. */
@Configuration(proxyBeanMethods = false)
@Profile("compose")
public class ComposeTeachingConfiguration {
    @Bean
    @DependsOn("securityMigration")
    CommandLineRunner composeAccount(JdbcTemplate jdbc, PasswordEncoder passwords, PlatformTransactionManager manager, Environment environment) {
        if (!"disposable".equals(environment.getProperty("TEACHING_DEPLOYMENT"))
                || !"compose".equals(environment.getProperty("DATA_DEPLOYMENT_MODE"))) {
            throw new IllegalArgumentException("Compose account requires explicit disposable teaching deployment");
        }
        String password = environment.getProperty("TEACHING_ADMIN_PASSWORD");
        if (password == null || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Explicit teaching administrator password must be 16..72 UTF8 bytes");
        }
        return args -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sec_users WHERE username='compose-admin'", Integer.class) == 1) {
                System.out.println("TEACHING_ACCOUNT retained; existing password unchanged");
                return;
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sec_users", Integer.class) != 0) {
                throw new IllegalStateException("Refusing to seed a nonempty identity database");
            }
            jdbc.update("INSERT INTO sec_departments(id,name) VALUES (100,'Compose lesson')");
            jdbc.update("INSERT INTO data_owners(id,name) VALUES (100,'Compose administrator')");
            jdbc.update("INSERT INTO sec_users(id,username,password_hash,department_id) VALUES (100,'compose-admin',?,100)", passwords.encode(password));
            jdbc.update("INSERT INTO sec_roles(id,name) VALUES (100,'compose-admin')");
            jdbc.update("INSERT INTO sec_permissions(id,code) VALUES (100,'task:read:any'),(101,'task:write:any'),(102,'role:manage')");
            jdbc.update("INSERT INTO sec_role_permissions(role_id,permission_id) VALUES (100,100),(100,101),(100,102)");
            jdbc.update("INSERT INTO sec_user_roles(user_id,role_id) VALUES (100,100)");
            System.out.println("TEACHING_ACCOUNT initialized in explicit disposable deployment");
        });
    }
}
