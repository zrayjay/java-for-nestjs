import { Module } from '@nestjs/common'
import { GreetingPolicy } from './greeting.policy.js'
import { GreetingService } from './greeting.service.js'

@Module({ providers: [GreetingPolicy, GreetingService], exports: [GreetingService] })
export class GreetingModule {}
