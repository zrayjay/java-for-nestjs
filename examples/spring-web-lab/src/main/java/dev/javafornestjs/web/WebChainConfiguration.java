package dev.javafornestjs.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "lab.security.mode", havingValue = "open", matchIfMissing = true)
public class WebChainConfiguration implements WebMvcConfigurer {
    private final RequestTrace traces;
    public WebChainConfiguration(RequestTrace traces) { this.traces = traces; }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> outerFilter() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
                traces.begin(request, response);
                traces.add(request, "filter.before");
                try { chain.doFilter(request, response); }
                finally {
                    traces.add(request, "filter.after");
                    traces.finish(request);
                }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setName("lab-outer");
        registration.setOrder(-200);
        return registration;
    }
    @Bean
    SecurityFilterChain sampleSecurity(HttpSecurity http) throws Exception {
        var inside = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
                traces.add(request, "security.before");
                try { chain.doFilter(request, response); }
                finally { traces.add(request, "security.after"); }
            }
        };
        return http.csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules.anyRequest().permitAll())
                .addFilterBefore(inside, AnonymousAuthenticationFilter.class).build();
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                traces.add(request, "interceptor.pre");
                return true;
            }
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                                    ModelAndView view) { traces.add(request, "interceptor.post"); }
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                         Exception failure) { traces.add(request, "interceptor.after"); }
        });
    }
}
