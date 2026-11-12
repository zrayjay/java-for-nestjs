import { createDataClient } from '@java-for-nestjs/prisma-reference/data-client'
import { createSecurityApi } from './app.js'
import { loadKeys } from './keys.js'

async function main(): Promise<void> {
  const raw = process.env.PORT ?? '8090'
  if (!/^(0|[1-9][0-9]*)$/.test(raw) || Number(raw) > 65535)
    throw new Error('Invalid PORT')
  const keys = await loadKeys(
    process.env.SECURITY_PRIVATE_KEY_PATH,
    process.env.SECURITY_PUBLIC_KEY_PATH,
  )
  const client = createDataClient(process.env.DATA_DATABASE_URL)
  try {
    const app = await createSecurityApi(client, keys, undefined, Number(raw))
    app.enableShutdownHooks()
    console.log(`NEST_SECURITY_PORT=${new URL(await app.getUrl()).port}`)
  } catch (error) {
    await client.$disconnect()
    throw error
  }
}
main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : 'Startup failed')
  process.exitCode = 1
})
