import assert from 'node:assert/strict'
import { setTimeout } from 'node:timers/promises'

const phase = process.argv[2]
assert.ok(
  ['healthy', 'firing', 'recovered'].includes(phase),
  'Specify healthy, firing or recovered',
)
const origin = new URL(
  process.env.CAPSTONE_PROMETHEUS_URL ?? 'http://127.0.0.1:19092',
)
assert.ok(['127.0.0.1', 'localhost'].includes(origin.hostname))

async function prometheus(path) {
  const response = await fetch(new URL(path, origin), {
    signal: AbortSignal.timeout(3500),
  })
  if (!response.ok)
    throw new Error('Prometheus responded with ' + response.status)
  const body = await response.json()
  assert.equal(body.status, 'success')
  return body.data
}

const deadline = Date.now() + 65_000
let observed
while (Date.now() < deadline) {
  try {
    const up = await prometheus(
      '/api/v1/query?query=' + encodeURIComponent('up{job="capstone"}'),
    )
    const state = await prometheus('/api/v1/alerts')
    observed = {
      scrape: up.result[0]?.value[1],
      alert: state.alerts.find(
        ({ labels }) => labels.alertname === 'CapstoneScrapeUnavailable',
      )?.state,
    }
    const expected =
      phase === 'firing' ? { scrape: '0', alert: 'firing' } : { scrape: '1' }
    if (
      observed.scrape === expected.scrape &&
      observed.alert === expected.alert
    ) {
      console.log('Capstone monitoring ' + phase, observed)
      process.exit(0)
    }
  } catch (error) {
    observed = error.message
  }
  await setTimeout(2000)
}
console.error('Capstone monitoring did not reach ' + phase, observed)
process.exitCode = 1
