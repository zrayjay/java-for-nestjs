import { computed, inject, provide, shallowRef } from 'vue'
import type { InjectionKey } from 'vue'
import {
  currentMenus,
  login,
  logout,
  me,
  refresh,
} from './api/generated/sdk.gen'
import type {
  CurrentMenusResponse,
  LoginResponse,
  MeResponse,
} from './api/generated/types.gen'
import { ApiError, bearer, expectData, request } from './api/http'
import type { Result } from './api/http'

type SecuredOperation<T> = (
  headers: Record<string, string>,
  tokens: LoginResponse,
) => Promise<Result<T>>

const sessionKey: InjectionKey<ReturnType<typeof createSession>> =
  Symbol('capstone-session')

export function provideSession() {
  const session = createSession()
  provide(sessionKey, session)
  return session
}

export function useSession() {
  const session = inject(sessionKey)
  if (!session) throw new Error('Session provider is missing')
  return session
}

function expired() {
  return new ApiError(401, 'UNAUTHENTICATED', '会话已过期，请重新登录。')
}

function verifyTokens(pair: LoginResponse) {
  if (
    typeof pair?.accessToken !== 'string' ||
    !pair.accessToken ||
    typeof pair.refreshToken !== 'string' ||
    !pair.refreshToken
  )
    throw new ApiError(0, 'EMPTY_RESPONSE', '服务返回的凭据不完整。')
  return pair
}

export function createSession() {
  const profile = shallowRef<MeResponse | null>(null)
  const menus = shallowRef<CurrentMenusResponse>([])
  const pending = shallowRef(false)
  const error = shallowRef<ApiError | null>(null)
  let tokens: LoginResponse | undefined
  let generation = 0
  let refreshFlight: Promise<LoginResponse> | undefined
  let syncFlight: Promise<void> | undefined

  function clear() {
    generation++
    tokens = undefined
    refreshFlight = undefined
    syncFlight = undefined
    profile.value = null
    menus.value = []
  }

  async function rotate(current: LoginResponse, expected: number) {
    if (!tokens || generation !== expected) throw expired()
    if (tokens !== current) return tokens
    if (!refreshFlight) {
      const flight = expectData(() =>
        refresh({ body: { refreshToken: current.refreshToken } }),
      )
        .then((fresh) => {
          if (generation !== expected) throw expired()
          tokens = verifyTokens(fresh)
          return tokens
        })
        .catch((failure: unknown) => {
          if (generation === expected) {
            clear()
            error.value = expired()
          }
          throw failure
        })
        .finally(() => {
          if (refreshFlight === flight) refreshFlight = undefined
        })
      refreshFlight = flight
    }
    return refreshFlight
  }

  async function secured<T>(
    operation: SecuredOperation<T>,
  ): Promise<T | undefined> {
    const current = tokens
    const expected = generation
    if (!current) throw expired()
    function execute(pair: LoginResponse) {
      return request(() => operation(bearer(pair.accessToken), pair))
    }
    try {
      const result = await execute(current)
      if (generation !== expected) throw expired()
      return result
    } catch (failure) {
      if (
        !(failure instanceof ApiError) ||
        failure.status !== 401 ||
        generation !== expected
      )
        throw failure
      const fresh = await rotate(current, expected)
      try {
        const result = await execute(fresh)
        if (generation !== expected) throw expired()
        return result
      } catch (retryFailure) {
        if (
          generation === expected &&
          retryFailure instanceof ApiError &&
          retryFailure.status === 401
        ) {
          clear()
          error.value = expired()
        }
        throw retryFailure
      }
    }
  }

  async function data<T>(operation: SecuredOperation<T>): Promise<T> {
    const result = await secured(operation)
    if (result === undefined)
      throw new ApiError(0, 'EMPTY_RESPONSE', '服务返回的结果不完整。')
    return result
  }

  function sync() {
    if (syncFlight) return syncFlight
    const expected = generation
    const flight = Promise.all([
      data((headers) => me({ headers })),
      data((headers) => currentMenus({ headers })),
    ])
      .then(([account, navigation]) => {
        if (expected === generation) {
          profile.value = account
          menus.value = navigation
        }
      })
      .finally(() => {
        if (syncFlight === flight) syncFlight = undefined
      })
    syncFlight = flight
    return flight
  }

  async function signIn(credentials: { username: string; password: string }) {
    if (pending.value) return
    pending.value = true
    error.value = null
    try {
      tokens = verifyTokens(
        await expectData(() => login({ body: credentials })),
      )
      generation++
      await sync()
    } catch (failure) {
      clear()
      error.value = failure instanceof ApiError ? failure : expired()
    } finally {
      pending.value = false
    }
  }

  async function signOut() {
    if (pending.value || !tokens) return
    pending.value = true
    try {
      await secured((headers, current) =>
        logout({ headers, body: { refreshToken: current.refreshToken } }),
      )
    } catch (failure) {
      if (failure instanceof ApiError && failure.status !== 401)
        error.value = failure
    } finally {
      clear()
      pending.value = false
    }
  }

  return {
    profile: computed(() => profile.value),
    menus: computed(() => menus.value),
    pending: computed(() => pending.value),
    error: computed(() => error.value),
    data,
    secured,
    sync,
    signIn,
    signOut,
  }
}
