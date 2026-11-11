import { createApi } from '@java-for-nestjs/nestjs-reference/api'
import { createDataClient } from '@java-for-nestjs/prisma-reference/data-client'
import { PrismaTasks } from './tasks.js'

async function main(): Promise<void> {
  const raw = process.env.PORT ?? '8088'
  if (!/^(0|[1-9][0-9]*)$/.test(raw) || Number(raw) > 65535)
    throw new Error('Invalid PORT')
  const client = createDataClient(process.env.DATA_DATABASE_URL)
  try {
    const app = await createApi(Number(raw), new PrismaTasks(client))
    app.enableShutdownHooks()
    console.log(`NEST_DATA_PORT=${new URL(await app.getUrl()).port}`)
  } catch (error) {
    await client.$disconnect()
    throw error
  }
}
main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : 'Startup failed')
  process.exitCode = 1
})
