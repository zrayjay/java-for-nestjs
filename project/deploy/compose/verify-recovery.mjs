import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'

const [mode, fixturePath] = process.argv.slice(2)
assert.ok(
  ['seed', 'compare'].includes(mode) && fixturePath,
  'Specify seed or compare and a fixture path',
)
const origin = new URL(
  process.env.CAPSTONE_BASE_URL ?? 'http://127.0.0.1:18092',
)
assert.ok(['localhost', '127.0.0.1'].includes(origin.hostname))
const username = process.env.CAPSTONE_ADMIN_USERNAME
const password = process.env.CAPSTONE_ADMIN_PASSWORD
const clerkPassword = process.env.CAPSTONE_RECOVERY_PASSWORD
assert.ok(
  username && password && clerkPassword,
  'Set the disposable administrator and recovery fixture credentials',
)

async function request(
  path,
  { method = 'GET', body, token, expected = 200 } = {},
) {
  const response = await fetch(new URL('/api/v1' + path, origin), {
    method,
    headers: {
      ...(body && { 'content-type': 'application/json' }),
      ...(token && { Authorization: 'Bearer ' + token }),
    },
    body: body && JSON.stringify(body),
    signal: AbortSignal.timeout(5000),
  })
  assert.equal(response.status, expected, method + ' ' + path)
  return {
    data: expected === 204 ? undefined : await response.json(),
    requestId: response.headers.get('x-request-id'),
  }
}

async function session(username, password, operation) {
  const { data: tokens } = await request('/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  try {
    return await operation(tokens.accessToken)
  } finally {
    await request('/auth/logout', {
      method: 'POST',
      body: { refreshToken: tokens.refreshToken },
      token: tokens.accessToken,
      expected: 204,
    })
  }
}

async function orderEvidence(id, clerkName, adminToken) {
  const route = '/orders/' + id
  const fields = await session(clerkName, clerkPassword, async (token) => ({
    order: (await request(route, { token })).data,
    history: (await request(route + '/history', { token })).data,
  }))
  const audit = (
    await request('/audit-events?resourceType=ORDER&resourceId=' + id, {
      token: adminToken,
    })
  ).data
  return { ...fields, audit: audit.items }
}

await session(username, password, async (adminToken) => {
  if (mode === 'compare') {
    const expected = JSON.parse(await readFile(fixturePath, 'utf8'))
    assert.deepEqual(
      await orderEvidence(expected.order.id, expected.clerkName, adminToken),
      {
        order: expected.order,
        history: expected.history,
        audit: expected.audit,
      },
    )
    console.log(
      'Restored login, order, history and audit match for order ' +
        expected.order.id,
    )
    return
  }

  const clerkName = 'recovery-' + Date.now().toString(36)
  const { data: departments } = await request('/departments', {
    token: adminToken,
  })
  const { data: roles } = await request('/roles?size=100', {
    token: adminToken,
  })
  const departmentId = departments.find(({ code }) => code === 'operations')?.id
  const roleId = roles.items.find(({ code }) => code === 'ORDER_CLERK')?.id
  assert.ok(departmentId && roleId)
  const { data: clerk } = await request('/users', {
    method: 'POST',
    body: {
      username: clerkName,
      password: clerkPassword,
      displayName: 'Recovery verification',
      departmentId,
    },
    token: adminToken,
    expected: 201,
  })
  await request('/users/' + clerk.id + '/roles', {
    method: 'PUT',
    body: { roleIds: [roleId] },
    token: adminToken,
    expected: 204,
  })
  const { id, requestId } = await session(
    clerkName,
    clerkPassword,
    async (token) => {
      const { data: created } = await request('/orders', {
        method: 'POST',
        body: {
          customerName: 'Recovery verification',
          totalAmountCents: 18250,
        },
        token,
        expected: 201,
      })
      const result = await request('/orders/' + created.id + '/submit', {
        method: 'POST',
        body: { version: created.version },
        token,
      })
      return { id: created.id, requestId: result.requestId }
    },
  )
  const evidence = await orderEvidence(id, clerkName, adminToken)
  assert.equal(evidence.order.status, 'SUBMITTED')
  assert.deepEqual(
    evidence.history.map(({ action }) => action),
    ['CREATE', 'SUBMIT'],
  )
  assert.ok(evidence.audit.some((event) => event.requestId === requestId))
  await writeFile(
    fixturePath,
    JSON.stringify({ clerkName, ...evidence }) + '\n',
    { flag: 'wx', mode: 0o600 },
  )
  console.log('Recorded source order ' + id + ' and its two history events')
})
