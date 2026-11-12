import type { Redis } from 'ioredis'

const fill = `
local current = redis.call('GET', KEYS[1]) or '0'
if current ~= ARGV[1] then return 0 end
redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[3])
return 1`
const invalidate = `
redis.call('INCR', KEYS[1])
redis.call('DEL', KEYS[2])
return 1`

/** Internal teaching primitive; authorize/load must query the caller's current DB scope. */
export class ScopedCache {
  private readonly flights = new Map<string, Promise<string | null>>()
  constructor(
    private readonly redis: Redis,
    private readonly namespace: string,
    private readonly positiveMs: number,
    private readonly negativeMs: number,
    private readonly jitter: (ttl: number) => number,
  ) {
    if (
      !/^[a-zA-Z0-9_-]{1,60}$/.test(namespace) ||
      !Number.isSafeInteger(positiveMs) ||
      positiveMs < 1 ||
      !Number.isSafeInteger(negativeMs) ||
      negativeMs < 1
    )
      throw new Error('Invalid cache configuration')
  }
  private base(scope: string, id: number) {
    if (
      !/^[a-zA-Z0-9_-]{1,60}$/.test(scope) ||
      !Number.isSafeInteger(id) ||
      id < 1
    )
      throw new Error('Invalid cache key')
    return `${this.namespace}:{${scope}:${id}}`
  }
  async read(
    scope: string,
    id: number,
    authorize: () => Promise<void>,
    scopedLoad: () => Promise<string | null>,
  ) {
    await authorize()
    const base = this.base(scope, id)
    const generation = (await this.redis.get(`${base}:epoch`)) ?? '0'
    const stored = await this.redis.get(`${base}:value`)
    if (stored !== null) {
      if (stored === 'N') return null
      if (stored.startsWith('V')) return stored.slice(1)
      throw new Error('Invalid cache encoding')
    }
    const key = `${base}:${generation}`
    const existing = this.flights.get(key)
    if (existing) return existing
    const pending = (async () => {
      const rechecked = await this.redis.get(`${base}:value`)
      if (rechecked !== null) {
        if (rechecked === 'N') return null
        if (rechecked.startsWith('V')) return rechecked.slice(1)
        throw new Error('Invalid cache encoding')
      }
      const value = await scopedLoad()
      const ttl = value === null ? this.negativeMs : this.positiveMs
      const extra = this.jitter(ttl)
      if (
        !Number.isSafeInteger(extra) ||
        extra < 0 ||
        extra > Math.floor(ttl / 5) ||
        !Number.isSafeInteger(ttl + extra)
      )
        throw new Error('Jitter must be 0..20 percent')
      await this.redis.eval(
        fill,
        2,
        `${base}:epoch`,
        `${base}:value`,
        generation,
        ttl + extra,
        value === null ? 'N' : `V${value}`,
      )
      return value
    })()
    this.flights.set(key, pending)
    try {
      return await pending
    } finally {
      this.flights.delete(key)
    }
  }
  /** Call after DB commit for every affected scope; an outage is surfaced, never hidden. */
  async invalidateAfterCommit(scope: string, id: number) {
    const base = this.base(scope, id)
    await this.redis.eval(invalidate, 2, `${base}:epoch`, `${base}:value`)
  }
}
