import { randomUUID } from 'node:crypto'
import {
  CallHandler,
  CanActivate,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common'
import { finalize } from 'rxjs'

export interface TraceRequest {
  labTrace?: {
    id: string
    events: string[]
    finished: Promise<void>
    finish(): void
  }
}
export interface TraceResponse {
  setHeader(name: string, value: string): void
  once(event: 'finish', callback: () => void): void
}
@Injectable()
export class RequestTrace {
  private readonly entries = new Map<
    string,
    NonNullable<TraceRequest['labTrace']>
  >()
  begin(request: TraceRequest, response: TraceResponse): void {
    let finish!: () => void
    const entry = {
      id: randomUUID(),
      events: ['middleware.before'],
      finished: new Promise<void>((resolve) => {
        finish = resolve
      }),
      finish: () => finish(),
    }
    request.labTrace = entry
    this.entries.set(entry.id, entry)
    if (this.entries.size > 100)
      this.entries.delete(this.entries.keys().next().value!)
    response.setHeader('X-Lab-Request-Id', entry.id)
    response.once('finish', () => {
      entry.events.push('middleware.finish')
      entry.finish()
    })
  }
  add(request: TraceRequest, event: string): void {
    request.labTrace?.events.push(event)
  }
  async take(id: string): Promise<string[]> {
    const entry = this.entries.get(id)
    if (!entry) throw new Error('Trace not found')
    let timeout: ReturnType<typeof setTimeout> | undefined
    try {
      await Promise.race([
        entry.finished,
        new Promise<never>((_, reject) => {
          timeout = setTimeout(
            () => reject(new Error('Trace incomplete')),
            5000,
          )
        }),
      ])
      return [...entry.events]
    } finally {
      clearTimeout(timeout)
      this.entries.delete(id)
    }
  }
}
@Injectable()
export class TraceGuard implements CanActivate {
  constructor(private readonly trace: RequestTrace) {}
  canActivate(context: ExecutionContext): boolean {
    this.trace.add(context.switchToHttp().getRequest<TraceRequest>(), 'guard')
    return true
  }
}
@Injectable()
export class TraceInterceptor implements NestInterceptor {
  constructor(private readonly trace: RequestTrace) {}
  intercept(context: ExecutionContext, next: CallHandler) {
    const request = context.switchToHttp().getRequest<TraceRequest>()
    this.trace.add(request, 'interceptor.before')
    return next
      .handle()
      .pipe(finalize(() => this.trace.add(request, 'interceptor.finalize')))
  }
}
