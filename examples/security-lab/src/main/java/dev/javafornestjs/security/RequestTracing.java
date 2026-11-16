package dev.javafornestjs.security;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class RequestTracing {
    private final Tracer tracer;
    public RequestTracing(Tracer tracer) { this.tracer = tracer; }
    private static final TextMapGetter<HttpServletRequest> HEADERS = new TextMapGetter<>() {
        @Override public Iterable<String> keys(HttpServletRequest carrier) { return List.of("traceparent", "tracestate"); }
        @Override public String get(HttpServletRequest carrier, String key) { return carrier.getHeader(key); }
    };
    public Span server(HttpServletRequest request, String requestId) {
        Context parent = W3CTraceContextPropagator.getInstance().extract(Context.root(), request, HEADERS);
        return tracer.spanBuilder("http " + RequestTelemetry.route(request.getRequestURI()))
                .setSpanKind(SpanKind.SERVER).setParent(parent).setAttribute("request.id", requestId)
                .setAttribute("http.route", RequestTelemetry.route(request.getRequestURI())).startSpan();
    }
    public void audit(Runnable work) {
        var span = tracer.spanBuilder("sql.audit").setAttribute("db.system", "mysql")
                .setAttribute("request.id", MDC.get("requestId") == null ? "non-http" : MDC.get("requestId")).startSpan();
        try (Scope scope = span.makeCurrent()) {
            work.run();
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException | Error error) {
            span.setStatus(StatusCode.ERROR); // No exception message, SQL, parameters or stack export.
            throw error;
        } finally { span.end(); }
    }
    public static final class LogExporter implements SpanExporter {
        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            var logger = LoggerFactory.getLogger(RequestTracing.class);
            for (var span : spans) {
                logger.atInfo().addKeyValue("event", "trace.completed").addKeyValue("traceId", span.getTraceId())
                        .addKeyValue("spanId", span.getSpanId()).addKeyValue("parentSpanId", span.getParentSpanId())
                        .addKeyValue("name", span.getName()).addKeyValue("status", span.getStatus().getStatusCode().name())
                        .addKeyValue("requestId", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("request.id")))
                        .addKeyValue("elapsedMs", (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000.0)
                        .log("Teaching span completed");
            }
            return CompletableResultCode.ofSuccess();
        }
        @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    }
}
