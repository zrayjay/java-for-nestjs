import assert from 'node:assert/strict'
import { performance } from 'node:perf_hooks'

const origin = new URL(
  process.env.CAPSTONE_BASE_URL ?? 'http://127.0.0.1:18092',
)
assert.ok(
  ['127.0.0.1', 'localhost'].includes(origin.hostname),
  'Use a local disposable deployment',
)
const username = process.env.CAPSTONE_BENCH_USER
const password = process.env.CAPSTONE_BENCH_PASSWORD
assert.ok(username && password, 'Set the fixture clerk credentials')

const workers = 6
const requests = 240
const warmup = 30
const rounds = 3
const p95LimitMs = 250

function request(path, options) {
  return fetch(new URL('/api/v1' + path, origin), {
    ...options,
    signal: AbortSignal.timeout(5_000),
  })
}

function json(headers, body) {
  return {
    method: 'POST',
    headers: { ...headers, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  }
}

const login = await request('/auth/login', json({}, { username, password }))
assert.equal(login.status, 200)
const { accessToken, refreshToken } = await login.json()
assert.ok(accessToken && refreshToken)
const headers = { Authorization: 'Bearer ' + accessToken }
const path = '/orders?page=1&size=20'

function percentile(sorted, fraction) {
  return Math.round(sorted[Math.ceil(sorted.length * fraction) - 1] * 100) / 100
}

async function measure(count) {
  const durations = []
  let errors = 0
  let total
  let next = 0
  const start = performance.now()
  await Promise.all(
    Array.from({ length: workers }, async () => {
      while (next++ < count) {
        const begun = performance.now()
        try {
          const response = await request(path, { headers })
          const page = await response.json()
          assert.equal(response.status, 200)
          assert.equal(page.page, 1)
          assert.equal(page.size, 20)
          assert.ok(Array.isArray(page.items) && page.items.length > 0)
          total ??= page.total
          assert.equal(page.total, total)
          durations.push(performance.now() - begun)
        } catch {
          errors++
        }
      }
    }),
  )
  const seconds = (performance.now() - start) / 1000
  durations.sort((a, b) => a - b)
  return {
    requests: count,
    success: durations.length,
    errors,
    visibleOrders: total,
    p50Ms: percentile(durations, 0.5),
    p95Ms: percentile(durations, 0.95),
    p99Ms: percentile(durations, 0.99),
    successfulRequestsPerSecond: Math.round(durations.length / seconds),
  }
}

try {
  const initial = await measure(warmup)
  assert.equal(initial.errors, 0, 'Warmup failed')
  console.log({ workers, warmup, p95LimitMs })
  for (let round = 1; round <= rounds; round++) {
    const summary = await measure(requests)
    console.log({ round, ...summary })
    if (summary.errors || summary.p95Ms > p95LimitMs) process.exitCode = 1
  }
} finally {
  const logout = await request('/auth/logout', json(headers, { refreshToken }))
  assert.equal(logout.status, 204)
}
