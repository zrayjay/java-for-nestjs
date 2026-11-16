import {
  ROOT_CONTEXT,
  SpanKind,
  SpanStatusCode,
  trace,
} from '@opentelemetry/api'
import { AsyncLocalStorageContextManager } from '@opentelemetry/context-async-hooks'
import {
  ExportResultCode,
  W3CTraceContextPropagator,
  type ExportResult,
} from '@opentelemetry/core'
import {
  AlwaysOnSampler,
  SimpleSpanProcessor,
  TracerProvider,
  type ReadableSpan,
  type SpanExporter,
} from '@opentelemetry/sdk-trace'

export interface TraceRecord {
  event: 'trace.completed'
  traceId: string
  spanId: string
  parentSpanId: string
  name: string
  kind: 'SERVER' | 'INTERNAL'
  status: 'ERROR' | 'OK' | 'UNSET'
  requestId: string | undefined
  elapsedMs: number
  aborted: boolean
}
export class RequestTracing {
  private readonly manager = new AsyncLocalStorageContextManager().enable()
  private readonly propagator = new W3CTraceContextPropagator()
  private readonly provider
  private readonly tracer
  constructor(emit: (record: TraceRecord) => void) {
    const exporter: SpanExporter = {
      export(spans: ReadableSpan[], done: (result: ExportResult) => void) {
        for (const span of spans)
          emit({
            event: 'trace.completed',
            traceId: span.spanContext().traceId,
            spanId: span.spanContext().spanId,
            parentSpanId: span.parentSpanContext?.spanId ?? '0000000000000000',
            name: span.name,
            kind: span.kind === SpanKind.SERVER ? 'SERVER' : 'INTERNAL',
            status:
              span.status.code === SpanStatusCode.ERROR
                ? 'ERROR'
                : span.status.code === SpanStatusCode.OK
                  ? 'OK'
                  : 'UNSET',
            requestId:
              typeof span.attributes['request.id'] === 'string'
                ? span.attributes['request.id']
                : undefined,
            elapsedMs: span.duration[0] * 1000 + span.duration[1] / 1e6,
            aborted: span.attributes['request.aborted'] === true,
          })
        done({ code: ExportResultCode.SUCCESS })
      },
      shutdown: async () => {},
    }
    this.provider = new TracerProvider({
      sampler: new AlwaysOnSampler(),
      spanProcessors: [new SimpleSpanProcessor({ exporter })],
    })
    this.tracer = this.provider.getTracer('teaching.security')
  }
  server(
    route: string,
    carrier: { traceparent?: string; tracestate?: string },
    requestId: string,
  ) {
    const parent = this.propagator.extract(ROOT_CONTEXT, carrier, {
      keys: () => ['traceparent', 'tracestate'],
      get: (headers, key) =>
        key === 'traceparent'
          ? headers.traceparent
          : key === 'tracestate'
            ? headers.tracestate
            : undefined,
    })
    const span = this.tracer.startSpan(
      `http ${route}`,
      {
        kind: SpanKind.SERVER,
        attributes: { 'http.route': route, 'request.id': requestId },
      },
      parent,
    )
    const current = trace.setSpan(parent, span)
    return {
      context: span.spanContext(),
      run: <T>(work: () => T) => this.manager.with(current, work),
      end: (status: number, aborted = false) => {
        if (aborted) span.setAttribute('request.aborted', true)
        else span.setAttribute('http.response.status_code', status)
        if (aborted || status >= 500)
          span.setStatus({ code: SpanStatusCode.ERROR })
        span.end()
      },
    }
  }
  child<T>(requestId: string, work: () => Promise<T>): Promise<T> {
    const parent = this.manager.active()
    const span = this.tracer.startSpan(
      'sql.audit',
      { attributes: { 'db.system': 'mysql', 'request.id': requestId } },
      parent,
    )
    return this.manager.with(trace.setSpan(parent, span), async () => {
      try {
        const result = await work()
        span.setStatus({ code: SpanStatusCode.OK })
        return result
      } catch (error) {
        span.setStatus({ code: SpanStatusCode.ERROR }) // Deliberately omit exception text and SQL parameters.
        throw error
      } finally {
        span.end()
      }
    })
  }
  async shutdown() {
    try {
      await this.provider.shutdown()
    } finally {
      this.manager.disable()
    }
  }
}
