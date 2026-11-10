import { Injectable } from '@nestjs/common'

@Injectable()
export class GreetingPolicy {
  greet(name: string): string {
    if (typeof name !== 'string' || name.length > 32 || !/^[A-Za-z]/.test(name) || /[^A-Za-z0-9_-]/.test(name)) {
      throw new Error("Name must be 1-32 ASCII letters, digits, '_' or '-', starting with a letter.")
    }
    return `Hello, ${name}!`
  }
}
