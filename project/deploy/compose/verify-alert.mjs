import assert from 'node:assert/strict'
import { setTimeout } from 'node:timers/promises'

const phase = process.argv[2]
assert.ok(
  [
    'healthy',
    'firing',
    'recovered',
    'api-errors-sample',
    'api-errors-firing',
  ].includes(phase),
  'Specify healthy, firing, recovered, api-errors-sample or api-errors-firing',
)
if (phase === 'api-errors-sample') {
  const api = new URL(process.env.CAPSTONE_BASE_URL ?? 'http://127.0.0.1:18092')
  assert.ok(['127.0.0.1', 'localhost'].includes(api.hostname))
  await Promise.all(
    Array.from({ length: 10 }, async () => {
      const response = await fetch(new URL('/api/v1/auth/login', api), {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          username: 'metric-probe',
          password: 'disposable-metric-probe-password',
        }),
        signal: AbortSignal.timeout(40_000),
      })
      assert.equal(response.status, 500)
      const error = await response.json()
      assert.equal(error.code, 'INTERNAL_ERROR')
      assert.ok(error.requestId)
      assert.equal(error.requestId, response.headers.get('x-request-id'))
    }),
  )
  console.log('Observed ten correlated API 500 responses')
  process.exit(0)
}
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
    const apiErrorAlert = phase === 'api-errors-firing'
    const alertName = apiErrorAlert
      ? 'CapstoneApiServerErrors'
      : 'CapstoneScrapeUnavailable'
    observed = {
      scrape: up.result[0]?.value[1],
      alert: state.alerts.find(({ labels }) => labels.alertname === alertName)
        ?.state,
    }
    const expected =
      phase === 'firing'
        ? { scrape: '0', alert: 'firing' }
        : { scrape: '1', alert: apiErrorAlert ? 'firing' : undefined }
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
