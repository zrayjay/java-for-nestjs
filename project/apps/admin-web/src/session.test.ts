import { afterEach, describe, expect, it, vi } from 'vitest'
import { client } from './api/generated/client.gen'
import { users } from './api/generated/sdk.gen'
import { createSession } from './session'

const originalConfig = client.getConfig()
const original = {
  accessToken: 'initial-access',
  refreshToken: 'initial-refresh',
  expiresIn: 300,
}
const rotated = {
  accessToken: 'rotated-access',
  refreshToken: 'rotated-refresh',
  expiresIn: 300,
}
const account = {
  id: '1',
  displayName: 'Operator',
  username: 'operator',
  departmentId: '2',
  permissions: ['system:admin'],
}
const navigation = [{ id: '1', label: '用户', routeKey: 'users', children: [] }]
const page = { items: [], total: 0, page: 1, size: 20 }

afterEach(() => client.setConfig(originalConfig))

function respond(body: unknown, status = 200) {
  return Response.json(body, {
    status,
    headers: { 'X-Request-Id': 'fixture-request' },
  })
}

function useFixture(refreshStatus: number | 'lost' | 'incomplete' = 200) {
  const counters = { refreshes: 0, oldRequests: 0, retriedRequests: 0 }
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = new Request(input, init)
    const pathname = new URL(request.url).pathname
    const auth = request.headers.get('Authorization')
    switch (pathname) {
      case '/api/v1/auth/login':
        return respond(original)
      case '/api/v1/me':
        return respond(account)
      case '/api/v1/me/menus':
        return respond(navigation)
      case '/api/v1/auth/refresh':
        counters.refreshes++
        expect(await request.json()).toEqual({
          refreshToken: original.refreshToken,
        })
        if (refreshStatus === 'lost') throw new TypeError('Connection closed')
        if (refreshStatus === 'incomplete')
          return respond({ accessToken: 'new' })
        return refreshStatus === 200
          ? respond(rotated)
          : respond({ code: 'UNAUTHENTICATED' }, refreshStatus)
      case '/api/v1/users':
        if (auth === 'Bearer ' + original.accessToken) {
          counters.oldRequests++
          return respond({ code: 'UNAUTHENTICATED' }, 401)
        }
        expect(auth).toBe('Bearer ' + rotated.accessToken)
        counters.retriedRequests++
        return respond(page)
      case '/api/v1/auth/logout':
        if (auth === 'Bearer ' + original.accessToken)
          return respond({ code: 'UNAUTHENTICATED' }, 401)
        expect(auth).toBe('Bearer ' + rotated.accessToken)
        expect(await request.json()).toEqual({
          refreshToken: rotated.refreshToken,
        })
        return new Response(null, { status: 204 })
      default:
        throw new Error('Unexpected SDK URL ' + pathname)
    }
  })
  client.setConfig({ baseUrl: 'https://capstone.test', fetch })
  return counters
}

describe('administrator session', () => {
  it('shares one refresh across concurrent 401 responses and retries both SDK calls', async () => {
    const calls = useFixture()
    const session = createSession()
    await session.signIn({ username: 'operator', password: 'test password' })
    expect(session.profile.value).toEqual(account)
    const list = () => session.data((headers) => users({ headers }))
    await expect(Promise.all([list(), list()])).resolves.toEqual([page, page])
    expect(calls).toEqual({ refreshes: 1, oldRequests: 2, retriedRequests: 2 })
    expect(session.menus.value).toEqual(navigation)
  })

  it('clears private state when refresh credentials have been revoked', async () => {
    const calls = useFixture(401)
    const session = createSession()
    await session.signIn({ username: 'operator', password: 'test password' })
    await expect(
      session.data((headers) => users({ headers })),
    ).rejects.toMatchObject({ status: 401 })
    expect(calls).toEqual({ refreshes: 1, oldRequests: 1, retriedRequests: 0 })
    expect(session.profile.value).toBeNull()
    expect(session.menus.value).toEqual([])
    expect(session.error.value?.message).toContain('重新登录')
  })

  it('uses the rotated access and refresh token together when signing out', async () => {
    const calls = useFixture()
    const session = createSession()
    await session.signIn({ username: 'operator', password: 'test password' })
    await session.signOut()
    expect(calls.refreshes).toBe(1)
    expect(session.profile.value).toBeNull()
    expect(session.menus.value).toEqual([])
  })

  it.each(['lost', 'incomplete'] as const)(
    'discards a refresh token after a %s response and never resends it',
    async (response) => {
      const calls = useFixture(response)
      const session = createSession()
      await session.signIn({ username: 'operator', password: 'test password' })
      const list = () => session.data((headers) => users({ headers }))
      await expect(list()).rejects.toBeDefined()
      await expect(list()).rejects.toMatchObject({ status: 401 })
      expect(calls).toEqual({
        refreshes: 1,
        oldRequests: 1,
        retriedRequests: 0,
      })
      expect(session.profile.value).toBeNull()
      expect(session.menus.value).toEqual([])
      expect(session.error.value?.message).toContain('重新登录')
    },
  )
})
