package dev.javafornestjs.capstone.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIds extends OncePerRequestFilter {
    private static final String KEY = RequestIds.class.getName();

    static String read(HttpServletRequest request) {
        return (String) request.getAttribute(KEY);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(KEY, id);
        response.setHeader("X-Request-Id", id);
        chain.doFilter(request, response);
    }
}
