import { test, describe, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'
import { BrokerLab, MessageStore, type TaskEvent } from './message.js'
import { OutboxRelay } from './outbox.js'

describe('actual SQL Outbox and broker recovery', { timeout: 60000 }, () => {
  let store: MessageStore
  let broker: BrokerLab
  const event = (): TaskEvent => ({
    eventId: randomUUID(),
    kind: 'task.created',
    taskKey: 'task-one',
  })
  beforeEach(async () => {
    store = new MessageStore(process.env.MESSAGE_DATABASE_URL)
    await store.resetFixture()
    broker = await BrokerLab.open(process.env.MESSAGE_AMQP_URL)
  })
  afterEach(async () => {
    try {
      await broker?.close()
    } finally {
      await store?.close()
    }
  })
  async function consume() {
    const channel = await broker.consumer()
    try {
      return (await broker.consume(channel, store, () => {})).outcome
    } finally {
      await channel.close()
    }
  }
  test('business and Outbox rollback together', async () => {
    await assert.rejects(
      store.enqueueTask(event(), () => {
        throw new Error('Injected before commit')
      }),
      /Injected/,
    )
    assert.equal(await store.businessTasks(), 0)
    assert.equal(await store.pending(), 0)
  })
  test('committed business survives actual unroutable publication then recovers', async () => {
    const payload = event()
    await store.enqueueTask(payload, () => {})
    const failing = new OutboxRelay(
      store,
      async (message) => {
        assert.deepEqual(await broker.probeUnroutable(message), {
          confirmed: true,
          returned: true,
        })
        throw new Error('Actual mandatory return: no route')
      },
      100,
      100,
    )
    await assert.rejects(failing.runOnce(), /Actual mandatory return/)
    assert.equal(await store.businessTasks(), 1)
    assert.equal(await store.pending(), 1)
    await delay(150)
    const healthy = new OutboxRelay(
      store,
      (message) => broker.send(message),
      5000,
      100,
    )
    assert.equal(await healthy.runOnce(), 'SENT')
    assert.equal(await consume(), 'APPLIED')
    assert.equal(await store.effects(payload.taskKey), 1)
    assert.equal(await store.pending(), 0)
  })
  test('real confirm then injected crash before mark duplicates delivery but not effect', async () => {
    const payload = event()
    await store.enqueueTask(payload, () => {})
    const first = new OutboxRelay(
      store,
      (message) => broker.send(message),
      100,
      100,
    )
    await assert.rejects(
      first.runOnce(() => {
        throw new Error('Injected after real confirm before mark')
      }),
      /Injected/,
    )
    assert.equal(await consume(), 'APPLIED')
    assert.equal(await store.pending(), 1)
    await delay(150)
    const recovered = new OutboxRelay(
      store,
      (message) => broker.send(message),
      5000,
      100,
    )
    assert.equal(await recovered.runOnce(), 'SENT')
    assert.equal(await consume(), 'DUPLICATE')
    assert.equal(await store.effects(payload.taskKey), 1)
    assert.equal(await store.markers(), 1)
    assert.equal(await store.pending(), 0)
  })
  test('leases exclude concurrent claim and stale worker marking but never fence publishing', async () => {
    const payload = event()
    await store.enqueueTask(payload, () => {})
    const first = await store.claim(100)
    assert.ok(first)
    assert.equal(await store.claim(100), undefined)
    await delay(150)
    const current = await store.claim(5000)
    assert.ok(current)
    await broker.send(first.event)
    assert.equal(await store.markSent(first), false)
    await broker.send(current.event)
    assert.equal(await store.markSent(current), true)
    assert.equal(await consume(), 'APPLIED')
    assert.equal(await consume(), 'DUPLICATE')
    assert.equal(await store.effects(payload.taskKey), 1)
    await store.resetFixture()
    await store.enqueueTask(event(), () => {})
    const claims = await Promise.all([store.claim(5000), store.claim(5000)])
    assert.equal(claims.filter(Boolean).length, 1)
  })
  test('local circuit blocks repeated actual routing failures then probes recovery', async () => {
    await store.enqueueTask(event(), () => {})
    let fail = true
    const relay = new OutboxRelay(
      store,
      async (message) => {
        if (fail) {
          assert.equal((await broker.probeUnroutable(message)).returned, true)
          throw new Error('Actual mandatory return')
        }
        await broker.send(message)
      },
      100,
      200,
    )
    await assert.rejects(relay.runOnce(), /Actual mandatory return/)
    await delay(150)
    await assert.rejects(relay.runOnce(), /Actual mandatory return/)
    assert.equal(await relay.runOnce(), 'CIRCUIT_OPEN')
    assert.equal(await store.pending(), 1)
    await delay(250)
    fail = false
    assert.equal(await relay.runOnce(), 'SENT')
    assert.equal(await consume(), 'APPLIED')
    assert.equal(await store.pending(), 0)
  })
})
