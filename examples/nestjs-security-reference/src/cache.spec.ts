import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { after, test } from 'node:test'
import { Redis } from 'ioredis'
import { ScopedCache } from './cache.js'

const url = process.env.CACHE_REDIS_URL
if (!url || !/^redis:\/\/(127\.0\.0\.1|localhost):\d+\/?$/.test(url))
  throw new Error('CACHE_REDIS_URL must name a disposable loopback Redis')
const redis = new Redis(url, {
  maxRetriesPerRequest: 1,
  enableOfflineQueue: false,
  lazyConnect: true,
})
await redis.connect()
const namespace = `node-${randomUUID()}`
const cache = new ScopedCache(redis, namespace, 5000, 1000, (ttl) =>
  Math.floor(ttl / 5),
)
const allow = async () => {}
after(async () => {
  // Delete only this test's namespace, never FLUSHDB or unrelated keys.
  const keys = await redis.keys(`${namespace}:*`)
  if (keys.length) await redis.del(...keys)
  await redis.quit()
})
test('positive and null hits have bounded TTL jitter and scope isolation', async () => {
  let loads = 0
  const load = async () => {
    loads++
    return 'task'
  }
  assert.equal(await cache.read('department1', 1, allow, load), 'task')
  assert.equal(await cache.read('department1', 1, allow, load), 'task')
  assert.equal(loads, 1)
  const ttl = await redis.pttl(`${namespace}:{department1:1}:value`)
  assert.ok(ttl > 0 && ttl <= 6000)
  await cache.read('department2', 1, allow, load)
  assert.equal(loads, 2)
  let misses = 0
  const absent = async () => {
    misses++
    return null
  }
  assert.equal(await cache.read('department1', 2, allow, absent), null)
  assert.equal(await cache.read('department1', 2, allow, absent), null)
  assert.equal(misses, 1)
  const negative = await redis.pttl(`${namespace}:{department1:2}:value`)
  assert.ok(negative > 0 && negative <= 1200)
  // Force server-side expiration, then wait for actual Redis absence with a bound.
  await redis.pexpire(`${namespace}:{department1:2}:value`, 1)
  const deadline = Date.now() + 2000
  while (await redis.exists(`${namespace}:{department1:2}:value`)) {
    assert.ok(Date.now() < deadline, 'Redis expiration timed out')
    await new Promise((resolve) => setTimeout(resolve, 5))
  }
  await cache.read('department1', 2, allow, absent)
  assert.equal(misses, 2)
})
test('hit never bypasses current authorization or caches a denial', async () => {
  await cache.read('department1', 3, allow, async () => 'secret')
  let loads = 0
  await assert.rejects(
    cache.read(
      'department1',
      3,
      async () => {
        throw new Error('forbidden')
      },
      async () => {
        loads++
        return 'leak'
      },
    ),
    /forbidden/,
  )
  assert.equal(loads, 0)
})
test('concurrent local misses share one load and failed loads can retry', async () => {
  let loads = 0
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const requests = Array.from({ length: 20 }, () =>
    cache.read('department1', 4, allow, async () => {
      loads++
      await gate
      return 'shared'
    }),
  )
  // Wait for the loader to enter; late leaders recheck Redis before starting another load.
  while (loads === 0) await new Promise((resolve) => setTimeout(resolve, 1))
  release()
  assert.deepEqual(await Promise.all(requests), Array(20).fill('shared'))
  assert.equal(loads, 1)
  await assert.rejects(
    cache.read('department1', 5, allow, async () => {
      throw new Error('database down')
    }),
    /database down/,
  )
  assert.equal(
    await cache.read('department1', 5, allow, async () => 'recovered'),
    'recovered',
  )
})
test('post-commit invalidation prevents an old in-flight load repopulating Redis', async () => {
  let started!: () => void
  let release!: () => void
  const entered = new Promise<void>((resolve) => {
    started = resolve
  })
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const old = cache.read('department1', 6, allow, async () => {
    started()
    await gate
    return 'old'
  })
  await entered
  await cache.invalidateAfterCommit('department1', 6)
  assert.equal(
    await cache.read('department1', 6, allow, async () => 'new'),
    'new',
  )
  release()
  assert.equal(await old, 'old') // Already started readers are not retroactively canceled.
  assert.equal(
    await cache.read('department1', 6, allow, async () => 'wrong'),
    'new',
  )
})
test('Redis failure is surfaced instead of silently serving or loading data', async () => {
  const stopped = new Redis(url, {
    lazyConnect: true,
    enableOfflineQueue: false,
  })
  stopped.disconnect()
  let loads = 0
  const broken = new ScopedCache(stopped, 'unavailable', 1000, 100, () => 0)
  await assert.rejects(
    broken.read('department1', 1, allow, async () => {
      loads++
      return 'x'
    }),
  )
  assert.equal(loads, 0)
})
