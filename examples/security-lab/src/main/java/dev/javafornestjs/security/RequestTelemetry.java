package dev.javafornestjs.security;

import java.util.Set;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTelemetry.class);
    private static final Set<String> ROUTES = Set.of("/auth/login", "/auth/refresh", "/auth/logout", "/me",
            "/tasks", "/health", "/actuator/health", "/actuator/metrics", "/openapi.json");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private RequestTelemetry() { }
    static String route(String path) {
        if (ROUTES.contains(path)) { return path; }
        if (path.matches("/tasks/[^/]+")) { return "/tasks/{id}"; }
        if (path.matches("/admin/users/[^/]+/roles")) { return "/admin/users/{id}/roles"; }
        if (path.matches("/actuator/metrics/[^/]+")) { return "/actuator/metrics/{name}"; }
        return "<unmatched>";
    }
    static void completed(String method, String path, int status, long started, SpanContext context) {
        var event = status >= 500 ? LOG.atError() : status >= 400 ? LOG.atWarn() : LOG.atInfo();
        // requestId already comes from MDC; duplicating it as a key/value breaks Boot ECS encoding.
        event.addKeyValue("event", "request.completed")
                .addKeyValue("traceId", context.getTraceId()).addKeyValue("spanId", context.getSpanId())
                .addKeyValue("method", METHODS.contains(method) ? method : "OTHER").addKeyValue("route", route(path))
                .addKeyValue("status", status).addKeyValue("elapsedMs", Math.max(0, System.nanoTime() - started) / 1_000_000.0)
                .log("Teaching request completed");
    }
}
