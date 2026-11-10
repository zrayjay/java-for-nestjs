import 'reflect-metadata'
import {
  Inject,
  Injectable,
  Module,
  Scope,
  type OnModuleInit,
  type OnApplicationBootstrap,
  type OnModuleDestroy,
  type BeforeApplicationShutdown,
  type OnApplicationShutdown,
} from '@nestjs/common'

export const TIME_SOURCE = Symbol('TIME_SOURCE')
export interface TimeSource {
  now(): string
}

@Injectable()
export class FixedTimeSource implements TimeSource {
  now(): string {
    return '09:00'
  }
}

@Injectable()
export class Greeter {
  constructor(@Inject(TIME_SOURCE) private readonly source: TimeSource) {}
  greet(): string {
    return `Hello @ ${this.source.now()}`
  }
}

@Module({
  providers: [{ provide: TIME_SOURCE, useClass: FixedTimeSource }],
  exports: [TIME_SOURCE],
})
export class TimeModule {}

@Module({ imports: [TimeModule], providers: [Greeter], exports: [Greeter] })
export class IocGreetingModule {}

@Module({ providers: [{ provide: TIME_SOURCE, useClass: FixedTimeSource }] })
export class PrivateTimeModule {}

@Module({ imports: [PrivateTimeModule], providers: [Greeter] })
export class MissingExportModule {}

@Injectable({ scope: Scope.TRANSIENT })
export class TransientToken {}

@Injectable({ scope: Scope.REQUEST })
export class RequestToken implements OnModuleDestroy {
  closed = false
  onModuleDestroy(): void {
    this.closed = true
  }
}

@Injectable()
export class Holder {
  constructor(readonly token: TransientToken) {}
}

@Module({ providers: [TransientToken, RequestToken, Holder] })
export class ScopeModule {}

@Injectable()
export class LifecycleProbe
  implements
    OnModuleInit,
    OnApplicationBootstrap,
    OnModuleDestroy,
    BeforeApplicationShutdown,
    OnApplicationShutdown
{
  readonly events = ['constructed']
  onModuleInit(): void {
    this.events.push('module-init')
  }
  onApplicationBootstrap(): void {
    this.events.push('bootstrap')
  }
  onModuleDestroy(): void {
    this.events.push('module-destroy')
  }
  beforeApplicationShutdown(): void {
    this.events.push('before-shutdown')
  }
  onApplicationShutdown(): void {
    this.events.push('shutdown')
  }
}
