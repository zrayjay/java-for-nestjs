import {
  Inject,
  Injectable,
  OnApplicationBootstrap,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common'

export const LAB_CONFIGURATION = Symbol('LAB_CONFIGURATION')
export interface LabConfiguration {
  label: string
}
export function readConfiguration(
  environment: NodeJS.ProcessEnv,
): LabConfiguration {
  const label = environment.LAB_LABEL ?? 'lesson'
  if (label.trim().length === 0) throw new Error('LAB_LABEL must be nonblank')
  return Object.freeze({ label })
}
@Injectable()
export class LifecycleProbe
  implements OnModuleInit, OnApplicationBootstrap, OnModuleDestroy
{
  readonly events: string[] = ['construct']
  constructor(
    @Inject(LAB_CONFIGURATION) readonly configuration: LabConfiguration,
  ) {}
  onModuleInit(): void {
    this.events.push('init')
  }
  onApplicationBootstrap(): void {
    this.events.push('ready')
  }
  onModuleDestroy(): void {
    this.events.push('destroy')
  }
}
