import { performance } from 'node:perf_hooks'
import pino, { type DestinationStream } from 'pino'
import { RequestTracing } from './tracing.js'

const knownRoutes = new Set([
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/me',
  '/tasks',
  '/health',
  '/actuator/health',
  '/actuator/metrics',
  '/openapi.json',
])
const methods = new Set([
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'PATCH',
  'HEAD',
  'OPTIONS',
])
export function safeRoute(path: string) {
  if (knownRoutes.has(path)) return path
  if (/^\/tasks\/[^/]+$/.test(path)) return '/tasks/{id}'
  if (/^\/admin\/users\/[^/]+\/roles$/.test(path))
    return '/admin/users/{id}/roles'
  return '<unmatched>'
}
export class RequestTelemetry {
  readonly tracing: RequestTracing
  private readonly logger
  private readonly timers = new Map<
    string,
    { count: number; totalMs: number; errors: number }
  >()
  constructor(destination?: DestinationStream) {
    this.logger = pino(
      { base: undefined, timestamp: pino.stdTimeFunctions.isoTime },
      destination,
    )
    this.tracing = new RequestTracing((record) =>
      this.logger.info(record, 'Teaching span completed'),
    )
  }
  completed(
    method: string,
    path: string,
    status: number,
    started: number,
    requestId: string,
    traceId: string,
    spanId: string,
    aborted = false,
  ) {
    const elapsedMs = Math.max(0, performance.now() - started)
    const route = safeRoute(path),
      safeMethod = methods.has(method) ? method : 'OTHER'
    const key = `${safeMethod} ${route} ${status}`
    const current = this.timers.get(key) ?? { count: 0, totalMs: 0, errors: 0 }
    current.count++
    current.totalMs += elapsedMs
    if (status >= 400) current.errors++
    this.timers.set(key, current)
    const entry = {
      event: 'request.completed',
      requestId,
      traceId,
      spanId,
      method: safeMethod,
      route,
      status,
      elapsedMs,
      aborted,
    }
    if (status >= 500) this.logger.error(entry, 'Teaching request completed')
    else if (status >= 400)
      this.logger.warn(entry, 'Teaching request completed')
    else this.logger.info(entry, 'Teaching request completed')
  }
  snapshot() {
    return [...this.timers.entries()].map(([labels, value]) => ({
      labels,
      ...value,
    }))
  }
  async onModuleDestroy() {
    await this.tracing.shutdown()
  }
}
