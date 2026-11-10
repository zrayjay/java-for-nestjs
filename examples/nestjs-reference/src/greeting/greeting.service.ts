import { Injectable } from '@nestjs/common'
import { GreetingPolicy } from './greeting.policy.js'

@Injectable()
export class GreetingService {
  constructor(private readonly policy: GreetingPolicy) {}

  greet(name: string): string {
    return this.policy.greet(name)
  }
}
