import 'reflect-metadata'
import { NestFactory } from '@nestjs/core'
import { AppModule } from './app.module.js'
import { GreetingService } from './greeting/greeting.service.js'

async function main(): Promise<void> {
  const args = process.argv.slice(2)
  if (args.length > 1) throw new Error('Expected zero or one learner name.')
  const app = await NestFactory.createApplicationContext(AppModule, { logger: false })
  try {
    console.log(app.get(GreetingService).greet(args[0] ?? 'NestJS'))
  } finally {
    await app.close()
  }
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : 'Startup failed')
  process.exitCode = 1
})
