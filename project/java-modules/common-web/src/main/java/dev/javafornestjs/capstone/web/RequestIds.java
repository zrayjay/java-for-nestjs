package dev.javafornestjs.capstone.web;

import dev.javafornestjs.capstone.core.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIds extends OncePerRequestFilter implements RequestCorrelation {
    private static final String KEY = RequestIds.class.getName();
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIds.class);

    static String read(HttpServletRequest request) {
        return (String) request.getAttribute(KEY);
    }

    @Override
    public String currentId() {
        var context = RequestContextHolder.getRequestAttributes();
        if (context instanceof ServletRequestAttributes attributes) {
            String id = read(attributes.getRequest());
            if (id != null) {
                return id;
            }
        }
        return UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(KEY, id);
        response.setHeader("X-Request-Id", id);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            if (request.getRequestURI().startsWith("/api/v1/")) {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                LOGGER.atInfo()
                        .addKeyValue("requestId", id)
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", pattern == null ? "unmatched" : pattern.toString())
                        .addKeyValue("status", completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .log("Capstone request completed");
            }
        }
    }
}
