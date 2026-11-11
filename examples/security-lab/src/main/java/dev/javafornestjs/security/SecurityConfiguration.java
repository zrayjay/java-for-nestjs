package dev.javafornestjs.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.javafornestjs.security.mapper.SecuredTaskMapper;
import dev.javafornestjs.web.TaskRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
@MapperScan(basePackageClasses = SecuredTaskMapper.class)
public class SecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock securityClock() { return Clock.systemUTC(); }
    @Bean
    PasswordEncoder passwords() { return new BCryptPasswordEncoder(10); }
    @Bean
    KeyPair tokenKeys(SecurityProperties properties, Environment environment) throws Exception { return TokenKeys.load(properties, environment); }
    @Bean
    JwtEncoder jwtEncoder(KeyPair pair) {
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("teaching-rsa").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    @Bean
    @DependsOn("dataMigration")
    Flyway securityMigration(DataSource source) {
        var migration = Flyway.configure().dataSource(source).locations("classpath:db/security-migration")
                .table("security_schema_history").baselineOnMigrate(true).baselineVersion("0").cleanDisabled(true).load();
        migration.migrate();
        return migration;
    }
    @Bean
    @DependsOn("securityMigration")
    AuthService authService(JdbcTemplate jdbc, PlatformTransactionManager transactions, PasswordEncoder passwords,
                            JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        return new AuthService(jdbc, new TransactionTemplate(transactions), passwords, encoder, clock, properties);
    }
    @Bean
    @Primary
    @DependsOn("securityMigration")
    TaskRepository securedTasks(SecuredTaskMapper mapper, AuthService auth) { return new PolicyTaskRepository(mapper, auth); }
    @Bean
    JwtDecoder jwtDecoder(KeyPair pair, Clock clock, SecurityProperties properties, AuthService auth) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> liveSession = jwt -> jwt.getAudience().contains(properties.audience()) && auth.valid(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer()), liveSession));
        return decoder;
    }
    private static void denied(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
    @Bean
    SecurityFilterChain protectedApi(HttpSecurity http, JwtDecoder decoder, AuthService auth) throws Exception {
        return http.csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/auth/login", "/auth/refresh", "/health", "/actuator/health", "/openapi.json").permitAll()
                        .requestMatchers(HttpMethod.GET, "/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .requestMatchers("/admin/**").hasAuthority("role:manage")
                        .requestMatchers(HttpMethod.GET, "/tasks", "/tasks/**").hasAnyAuthority("task:read:department", "task:read:any")
                        .requestMatchers(HttpMethod.POST, "/tasks").hasAnyAuthority("task:write:own", "task:write:any")
                        .requestMatchers(HttpMethod.PUT, "/tasks/**").hasAnyAuthority("task:write:own", "task:write:any")
                        .requestMatchers(HttpMethod.DELETE, "/tasks/**").hasAnyAuthority("task:write:own", "task:write:any")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> denied(response, 401, "unauthorized"))
                        .accessDeniedHandler((request, response, error) -> denied(response, 403, "forbidden")))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, error) -> denied(response, 401, "unauthorized"))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(auth::authenticate)))
                .build();
    }
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> correlationFilter() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
                String requestId = UUID.randomUUID().toString();
                MDC.put("requestId", requestId);
                response.setHeader("X-Request-Id", requestId);
                try { chain.doFilter(request, response); }
                finally { MDC.remove("requestId"); }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(-200);
        registration.setName("secured-correlation");
        return registration;
    }
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictIntegerInput() {
        return builder -> builder.postConfigurer(mapper -> {
            mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
