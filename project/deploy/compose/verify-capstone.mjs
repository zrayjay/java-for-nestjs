import assert from 'node:assert/strict'

const origin = new URL(process.env.CAPSTONE_BASE_URL ?? 'http://127.0.0.1:18092')
assert.ok(['127.0.0.1', 'localhost'].includes(origin.hostname), 'Use an isolated local deployment')
const username = process.env.CAPSTONE_ADMIN_USERNAME
const password = process.env.CAPSTONE_ADMIN_PASSWORD
assert.ok(username && password, 'Set the test administrator credentials')

async function request(path, options) {
  return fetch(new URL(path, origin), { ...options, signal: AbortSignal.timeout(5000) })
}

for (const path of ['/actuator/health', '/actuator/health/readiness']) {
  const health = await request(path)
  assert.equal(health.status, 200, path)
  assert.equal((await health.json()).status, 'UP', path)
}

const page = await request('/')
assert.equal(page.status, 200)
assert.match(page.headers.get('content-type'), /^text\/html/)
const html = await page.text()
assert.match(html, /id="app"/)
const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"\s]+\.(js|css))"/g)]
assert.deepEqual(assets.map(([, , type]) => type).sort(), ['css', 'js'])
for (const [, path, type] of assets) {
  const response = await request(path)
  assert.equal(response.status, 200, path)
  assert.match(response.headers.get('content-type'), type === 'js' ? /javascript/ : /text\/css/)
  const content = await response.text()
  assert.ok(content.length > 0)
  if (type === 'js') assert.ok(content.includes('/api/v1/auth/login'))
}

const unauthenticated = await request('/api/v1/me')
assert.equal(unauthenticated.status, 401)
assert.ok(unauthenticated.headers.get('x-request-id'))
const login = await request('/api/v1/auth/login', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username, password }),
})
assert.equal(login.status, 200)
const { accessToken, refreshToken } = await login.json()
assert.ok(accessToken && refreshToken)
const headers = { Authorization: 'Bearer ' + accessToken }
try {
  const identity = await request('/api/v1/me', { headers })
  assert.equal(identity.status, 200)
  const account = await identity.json()
  assert.equal(account.username, username)
  assert.ok(account.permissions.includes('system:admin'))
  const navigation = await request('/api/v1/me/menus', { headers })
  assert.equal(navigation.status, 200)
  assert.ok((await navigation.json()).some(({ routeKey }) => routeKey === 'users'))
} finally {
  const logout = await request('/api/v1/auth/logout', {
    method: 'POST',
    headers: { ...headers, 'content-type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  assert.equal(logout.status, 204)
}
console.log('Capstone Compose passed health, same-origin HTML and assets, and authenticated API probes')
