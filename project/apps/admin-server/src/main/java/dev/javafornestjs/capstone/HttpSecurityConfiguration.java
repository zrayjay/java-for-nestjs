package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.web.RequestErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain capstoneSecurity(HttpSecurity http, RequestErrors errors,
            @Value("${management.server.port:-1}") int managementPort) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/readiness",
                                "/livez", "/readyz").permitAll()
                        .requestMatchers(request -> managementPort > 0 && request.getLocalPort() == managementPort
                                && "GET".equals(request.getMethod())
                                && "/actuator/prometheus".equals(request.getRequestURI())).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handlers -> handlers.authenticationEntryPoint(
                        (request, response, cause) -> errors.authenticationRequired(request, response)))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(
                                (request, response, cause) -> errors.authenticationRequired(request, response))
                        .jwt(jwt -> { }))
                .build();
    }
}
