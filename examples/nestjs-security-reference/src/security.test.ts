import assert from 'node:assert/strict'
import { afterEach, before, beforeEach, test } from 'node:test'
import { hash } from 'bcryptjs'
import { decodeJwt, generateKeyPair, SignJWT, type JWTPayload } from 'jose'
import { createDataClient } from '@java-for-nestjs/prisma-reference/data-client'
import {
  AuthService,
  AUDIENCE,
  ISSUER,
  type Client,
  type SecurityClock,
} from './auth.js'
import { createSecurityApi } from './app.js'
import type { SigningKeys } from './keys.js'

const PASSWORD = 'fixture-password-only'
class MutableClock implements SecurityClock {
  value = 1794355200 // 2026-11-11T00:00:00Z
  now(): number {
    return this.value
  }
}
let keys: SigningKeys
let client: Client
let clock: MutableClock
let app: Awaited<ReturnType<typeof createSecurityApi>> | undefined
before(async () => {
  keys = await generateKeyPair('RS256', { modulusLength: 2048 })
})
beforeEach(async () => {
  app = undefined
  client = createDataClient(process.env.DATA_DATABASE_URL)
  for (const table of [
    'sec_audit',
    'sec_refresh_tokens',
    'sec_sessions',
    'sec_user_roles',
    'sec_role_permissions',
    'sec_permissions',
    'sec_roles',
    'sec_users',
    'sec_departments',
    'data_tasks',
    'data_owners',
  ]) {
    // Fixed, internal table allowlist; no request-controlled identifier is used.
    if (table === 'sec_audit') await client.secAudit.deleteMany()
    else if (table === 'sec_refresh_tokens')
      await client.secRefreshToken.deleteMany()
    else if (table === 'sec_sessions') await client.secSession.deleteMany()
    else if (table === 'sec_user_roles') await client.secUserRole.deleteMany()
    else if (table === 'sec_role_permissions')
      await client.secRolePermission.deleteMany()
    else if (table === 'sec_permissions')
      await client.secPermission.deleteMany()
    else if (table === 'sec_roles') await client.secRole.deleteMany()
    else if (table === 'sec_users') await client.secUser.deleteMany()
    else if (table === 'sec_departments')
      await client.secDepartment.deleteMany()
    else if (table === 'data_tasks') await client.dataTask.deleteMany()
    else if (table === 'data_owners') await client.dataOwner.deleteMany()
  }
  await client.secDepartment.createMany({
    data: [
      { id: 1, name: 'Engineering' },
      { id: 2, name: 'Sales' },
    ],
  })
  const names = ['alice', 'bob', 'cara', 'admin', 'erin']
  await client.dataOwner.createMany({
    data: names.map((name, index) => ({ id: index + 1, name })),
  })
  const passwordHash = await hash(PASSWORD, 10)
  await client.secUser.createMany({
    data: names.map((username, index) => ({
      id: index + 1,
      username,
      passwordHash,
      departmentId: index === 1 ? 2 : 1,
    })),
  })
  await client.secRole.createMany({
    data: [
      { id: 1, name: 'member' },
      { id: 2, name: 'reader' },
      { id: 3, name: 'admin' },
    ],
  })
  await client.secPermission.createMany({
    data: [
      'task:read:department',
      'task:write:own',
      'task:read:any',
      'task:write:any',
      'role:manage',
    ].map((code, index) => ({ id: index + 1, code })),
  })
  await client.secRolePermission.createMany({
    data: [
      [1, 1],
      [1, 2],
      [2, 1],
      [3, 3],
      [3, 4],
      [3, 5],
    ].map(([roleId, permissionId]) => ({ roleId, permissionId })),
  })
  await client.secUserRole.createMany({
    data: [
      [1, 1],
      [2, 1],
      [3, 2],
      [4, 3],
      [5, 1],
    ].map(([userId, roleId]) => ({ userId, roleId })),
  })
  clock = new MutableClock()
  app = await createSecurityApi(client, keys, clock)
})
afterEach(async () => {
  await app?.close()
  await client?.$disconnect()
})
async function request(
  method: string,
  path: string,
  access?: string,
  body?: string,
  extra?: Record<string, string>,
) {
  return fetch((await app!.getUrl()) + path, {
    method,
    body,
    signal: AbortSignal.timeout(15000),
    headers: {
      'Content-Type': 'application/json',
      ...(access ? { Authorization: `Bearer ${access}` } : {}),
      ...extra,
    },
  })
}
interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
}
async function login(username: string): Promise<TokenPair> {
  const response = await request(
    'POST',
    '/auth/login',
    undefined,
    JSON.stringify({ username, password: PASSWORD }),
  )
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('cache-control'), 'no-store')
  return (await response.json()) as TokenPair
}
const refresh = (token: string) =>
  request(
    'POST',
    '/auth/refresh',
    undefined,
    JSON.stringify({ refreshToken: token }),
  )
async function create(access: string, title: string) {
  const response = await request(
    'POST',
    '/tasks',
    access,
    JSON.stringify({ title, details: { priority: 2 } }),
  )
  assert.equal(response.status, 201)
  return (await response.json()) as { id: number; title: string }
}

test('password hashing and explicit Bearer authentication preserve request isolation', async () => {
  const stored = await client.secUser.findUniqueOrThrow({ where: { id: 1 } })
  assert.ok(stored.passwordHash !== PASSWORD)
  assert.equal((await request('GET', '/tasks')).status, 401)
  const first = await login('alice')
  assert.equal((await request('GET', '/me', first.accessToken)).status, 200)
  assert.equal(
    (
      await request('GET', '/me', undefined, undefined, {
        Cookie: `accessToken=${first.accessToken}`,
      })
    ).status,
    401,
  )
  assert.equal((await request('GET', '/me')).status, 401)
  assert.equal((await request('POST', '/tasks', undefined, '{')).status, 401)
})
test('invalid credentials and disabled users have the same generic denial', async () => {
  for (const username of ['alice', 'unknown']) {
    const response = await request(
      'POST',
      '/auth/login',
      undefined,
      JSON.stringify({ username, password: 'wrong' }),
    )
    assert.equal(response.status, 401)
    assert.deepEqual(await response.json(), { code: 'unauthorized' })
  }
  await client.secUser.update({ where: { id: 1 }, data: { enabled: false } })
  assert.equal(
    (
      await request(
        'POST',
        '/auth/login',
        undefined,
        JSON.stringify({ username: 'alice', password: PASSWORD }),
      )
    ).status,
    401,
  )
})
test('department filtering precedes limit and ownership is part of the mutation', async () => {
  const bob = await login('bob'),
    alice = await login('alice')
  const foreign = await create(bob.accessToken, 'Bob task')
  const own = await create(alice.accessToken, 'Alice task')
  const rows = (await (
    await request('GET', '/tasks?limit=1', alice.accessToken)
  ).json()) as { id: number }[]
  assert.deepEqual(
    rows.map((row) => row.id),
    [own.id],
  )
  assert.equal(
    (await request('GET', `/tasks/${foreign.id}`, alice.accessToken)).status,
    404,
  )
  assert.equal(
    (
      await request(
        'PUT',
        `/tasks/${foreign.id}`,
        alice.accessToken,
        '{"title":"Stolen","details":{"priority":1}}',
      )
    ).status,
    404,
  )
  assert.equal(
    (await request('DELETE', `/tasks/${foreign.id}`, alice.accessToken)).status,
    404,
  )
  const erin = await login('erin')
  assert.equal(
    (await request('GET', `/tasks/${own.id}`, erin.accessToken)).status,
    200,
  )
  assert.equal(
    (await request('DELETE', `/tasks/${own.id}`, erin.accessToken)).status,
    404,
  )
  const admin = await login('admin')
  assert.equal(
    (await request('GET', `/tasks/${foreign.id}`, admin.accessToken)).status,
    200,
  )
  assert.equal(
    (await request('DELETE', `/tasks/${foreign.id}`, admin.accessToken)).status,
    204,
  )
  assert.equal(
    (await client.dataTask.findUniqueOrThrow({ where: { id: own.id } })).title,
    'Alice task',
  )
})
test('button permission metadata does not replace server-side denial', async () => {
  const reader = await login('cara')
  const me = (await (
    await request('GET', '/me', reader.accessToken)
  ).json()) as { permissions: string[] }
  assert.ok(
    !me.permissions.some((permission) => permission.startsWith('task:write')),
  )
  assert.equal(
    (
      await request(
        'POST',
        '/tasks',
        reader.accessToken,
        '{"title":"Forbidden","details":{"priority":2}}',
      )
    ).status,
    403,
  )
  assert.equal(
    (await request('POST', '/tasks', reader.accessToken, '{')).status,
    403,
  )
  assert.equal(
    (
      await request(
        'PUT',
        '/admin/users/3/roles',
        reader.accessToken,
        '{"roles":[3]}',
      )
    ).status,
    403,
  )
  assert.equal(await client.dataTask.count(), 0)
})
test('role changes affect an already-issued access token and invalid roles roll back', async () => {
  const alice = await login('alice'),
    admin = await login('admin')
  assert.equal(
    (
      await request(
        'PUT',
        '/admin/users/1/roles',
        admin.accessToken,
        '{"roles":[2]}',
      )
    ).status,
    204,
  )
  assert.equal(
    (
      await request(
        'POST',
        '/tasks',
        alice.accessToken,
        '{"title":"Revoked permission","details":{"priority":2}}',
      )
    ).status,
    403,
  )
  assert.equal((await request('GET', '/me', alice.accessToken)).status, 200)
  for (const body of [
    '{"roles":[999]}',
    '{"roles":[1,1]}',
    '{"roles":[1.0]}',
    '{"roles":[1],"constructor":{}}',
  ])
    assert.equal(
      (await request('PUT', '/admin/users/1/roles', admin.accessToken, body))
        .status,
      400,
    )
  assert.equal(
    (await client.secUserRole.findFirstOrThrow({ where: { userId: 1 } }))
      .roleId,
    2,
  )
})
test('access expiry is strict while a still-valid refresh issues new access', async () => {
  const first = await login('alice')
  clock.value += 300
  assert.equal((await request('GET', '/me', first.accessToken)).status, 401)
  const response = await refresh(first.refreshToken)
  assert.equal(response.status, 200)
  const next = (await response.json()) as TokenPair
  assert.equal((await request('GET', '/me', next.accessToken)).status, 200)
})
test('refresh replay commits family revocation before returning unauthorized', async () => {
  const first = await login('alice')
  const response = await refresh(first.refreshToken)
  assert.equal(response.status, 200)
  const next = (await response.json()) as TokenPair
  assert.ok(next.refreshToken !== first.refreshToken)
  assert.equal((await request('GET', '/me', next.accessToken)).status, 200)
  assert.equal((await refresh(first.refreshToken)).status, 401)
  assert.equal((await request('GET', '/me', next.accessToken)).status, 401)
  assert.equal((await refresh(next.refreshToken)).status, 401)
  assert.equal(
    await client.secSession.count({ where: { revokedAt: { not: null } } }),
    1,
  )
  for (const row of await client.secRefreshToken.findMany()) {
    assert.match(row.tokenHash, /^[0-9a-f]{64}$/)
    assert.ok(row.tokenHash !== first.refreshToken)
  }
})
test('concurrent refresh permits one rotation and revokes the replayed family', async () => {
  const first = await login('alice')
  const responses = await Promise.all([
    refresh(first.refreshToken),
    refresh(first.refreshToken),
  ])
  assert.deepEqual(
    responses.map((response) => response.status).sort(),
    [200, 401],
  )
  const winner = (await responses
    .find((response) => response.status === 200)!
    .json()) as TokenPair
  assert.equal((await request('GET', '/me', winner.accessToken)).status, 401)
})
test('logout and disabled accounts reject both access and refresh', async () => {
  const first = await login('alice')
  assert.equal(
    (await request('POST', '/auth/logout', first.accessToken)).status,
    204,
  )
  assert.equal((await request('GET', '/me', first.accessToken)).status, 401)
  assert.equal((await refresh(first.refreshToken)).status, 401)
  const next = await login('alice')
  await client.secUser.update({ where: { id: 1 }, data: { enabled: false } })
  assert.equal((await request('GET', '/me', next.accessToken)).status, 401)
  assert.equal((await refresh(next.refreshToken)).status, 401)
})
test('refresh expiry rejects exactly at the session lifetime boundary', async () => {
  const first = await login('alice')
  clock.value += 604800
  assert.equal((await refresh(first.refreshToken)).status, 401)
})

test('credential version invalidates both tokens and a new login binds the new version', async () => {
  const first = await login('alice')
  await client.secUser.update({
    where: { id: 1 },
    data: { authVersion: { increment: 1 } },
  })
  assert.equal((await request('GET', '/me', first.accessToken)).status, 401)
  assert.equal((await refresh(first.refreshToken)).status, 401)
  const next = await login('alice')
  assert.equal(decodeJwt(next.accessToken).ver, 1)
  assert.equal((await request('GET', '/me', next.accessToken)).status, 200)
  assert.equal(
    (await client.secSession.findFirstOrThrow({ where: { revokedAt: null } }))
      .authVersion,
    1,
  )
})

test('refresh observes current user state after a concurrent credential update lock', async () => {
  const first = await login('alice')
  let locked!: () => void, release!: () => void
  const acquired = new Promise<void>((resolve) => {
    locked = resolve
  })
  const proceed = new Promise<void>((resolve) => {
    release = resolve
  })
  const update = client.$transaction(async (tx) => {
    await tx.secUser.update({
      where: { id: 1 },
      data: { authVersion: { increment: 1 } },
    })
    locked()
    await proceed
  })
  await Promise.race([acquired, update])
  const attempt = refresh(first.refreshToken)
  let timeout: ReturnType<typeof setTimeout> | undefined
  try {
    const completedEarly = await Promise.race([
      attempt.then(() => true),
      new Promise<boolean>((resolve) => {
        timeout = setTimeout(() => resolve(false), 200)
      }),
    ])
    assert.equal(completedEarly, false)
  } finally {
    clearTimeout(timeout)
    release()
    await update
  }
  assert.equal((await attempt).status, 401)
  assert.equal((await request('GET', '/me', first.accessToken)).status, 401)
})

test('concurrent identities retain their own task owner and audit request', async () => {
  const alice = await login('alice'),
    bob = await login('bob')
  const responses = await Promise.all([
    request(
      'POST',
      '/tasks',
      alice.accessToken,
      '{"title":"Alice concurrent","details":{"priority":2}}',
    ),
    request(
      'POST',
      '/tasks',
      bob.accessToken,
      '{"title":"Bob concurrent","details":{"priority":2}}',
    ),
  ])
  for (const [index, response] of responses.entries()) {
    assert.equal(response.status, 201)
    const row = (await response.json()) as { id: number }
    assert.equal(
      (await client.dataTask.findUniqueOrThrow({ where: { id: row.id } }))
        .ownerId,
      index + 1,
    )
    const event = await client.secAudit.findFirstOrThrow({
      where: { eventCode: 'task.created', resourceId: String(row.id) },
    })
    assert.equal(event.actorId, index + 1)
    assert.equal(event.requestId, response.headers.get('x-request-id'))
  }
  assert.equal((await request('GET', '/me')).status, 401)
})
test('RS256 signature, issuer, audience and mandatory claims are enforced', async () => {
  const first = await login('alice')
  const payload = decodeJwt(first.accessToken)
  const parts = first.accessToken.split('.')
  const tampered = [
    ...parts.slice(0, 2),
    (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1),
  ].join('.')
  const unsigned =
    Buffer.from('{"alg":"none"}').toString('base64url') + '.' + parts[1] + '.'
  const sign = (claims: JWTPayload) =>
    new SignJWT(claims)
      .setProtectedHeader({ alg: 'RS256' })
      .sign(keys.privateKey)
  const noNbf = { ...payload }
  delete noNbf.nbf
  for (const [name, token] of [
    ['signature', tampered],
    ['none', unsigned],
    ['issuer', await sign({ ...payload, iss: 'wrong-issuer' })],
    ['audience', await sign({ ...payload, aud: 'wrong-audience' })],
    ['nbf', await sign(noNbf)],
    ['future', await sign({ ...payload, nbf: clock.now() + 60 })],
  ])
    assert.equal((await request('GET', '/me', token)).status, 401, name)
  for (const missing of [
    'aud',
    'iss',
    'exp',
    'iat',
    'nbf',
    'sub',
    'jti',
    'sid',
    'ver',
  ]) {
    const incomplete = { ...payload }
    delete incomplete[missing]
    assert.equal(
      (await request('GET', '/me', await sign(incomplete))).status,
      401,
      missing,
    )
  }
  assert.equal(payload.iss, ISSUER)
  assert.equal(payload.aud, AUDIENCE)
})
test('audit correlation stores identifiers without passwords or raw tokens', async () => {
  const first = await login('alice')
  const response = await request(
    'POST',
    '/tasks',
    first.accessToken,
    '{"title":"Audited","details":{"priority":2}}',
  )
  assert.equal(response.status, 201)
  const task = (await response.json()) as { id: number }
  const event = await client.secAudit.findFirstOrThrow({
    where: { eventCode: 'task.created', resourceId: String(task.id) },
  })
  assert.equal(event.requestId, response.headers.get('x-request-id'))
  const text = (await client.secAudit.findMany())
    .map((row) => `${row.eventCode}|${row.resourceId}|${row.requestId}`)
    .join('\n')
  for (const secret of [PASSWORD, first.accessToken, first.refreshToken])
    assert.ok(!text.includes(secret))
})
test('audit failure rolls back the task mutation', async (testContext) => {
  const first = await login('alice')
  const auth = app!.get(AuthService)
  const original = auth.audit.bind(auth)
  testContext.mock.method(
    auth,
    'audit',
    async (...args: Parameters<AuthService['audit']>) => {
      if (args[2] === 'task.created') throw new Error('private-audit-detail')
      return original(...args)
    },
  )
  const response = await request(
    'POST',
    '/tasks',
    first.accessToken,
    '{"title":"Must rollback","details":{"priority":2}}',
  )
  assert.equal(response.status, 500)
  assert.deepEqual(await response.json(), { code: 'internal_error' })
  assert.equal(await client.dataTask.count(), 0)
})
