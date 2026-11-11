import { createApi } from './api.js'

async function main(): Promise<void> {
  const raw = process.env.PORT ?? '8086'
  if (!/^(0|[1-9][0-9]*)$/.test(raw) || Number(raw) > 65535)
    throw new Error('Invalid PORT')
  const app = await createApi(Number(raw))
  console.log(`NEST_API_PORT=${new URL(await app.getUrl()).port}`)
  app.enableShutdownHooks()
}
main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : 'Startup failed')
  process.exitCode = 1
})
