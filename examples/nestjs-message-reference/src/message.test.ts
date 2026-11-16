import { test, beforeEach, afterEach, describe, mock } from 'node:test'
import amqp from 'amqplib'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import {
  BrokerLab,
  MessageStore,
  decode,
  localUrl,
  type TaskEvent,
} from './message.js'

const event = (): TaskEvent => ({
  eventId: randomUUID(),
  kind: 'task.created',
  taskKey: 'task-one',
})
test('restricted envelope and explicit disposable service targets', () => {
  const valid = event()
  assert.deepEqual(decode(Buffer.from(JSON.stringify(valid))), valid)
  for (const value of [
    '{',
    '{}',
    '[]',
    JSON.stringify({ ...valid, extra: true }),
    JSON.stringify({ ...valid, taskKey: 1 }),
    JSON.stringify({ ...valid, kind: 'other' }),
    JSON.stringify({ ...valid, eventId: 'not-a-uuid' }),
    `${JSON.stringify(valid)}{}`,
  ])
    assert.throws(() => decode(Buffer.from(value)))
  assert.throws(() => decode(Buffer.alloc(1025)))
  assert.throws(() => decode(Buffer.from([0xff])))
  assert.throws(() => decode(Buffer.from(JSON.stringify(valid), 'utf16le')))
  assert.deepEqual(decode(Buffer.from('\uFEFF' + JSON.stringify(valid))), valid)
  assert.equal(
    localUrl('amqp://lab:password@127.0.0.1/message_lab?heartbeat=5', 'amqp:')
      .pathname,
    '/message_lab',
  )
  for (const url of [
    undefined,
    'amqp://lab:password@remote/message_lab',
    'amqp://lab:password@localhost/production',
    'amqp://lab:password@localhost/message_lab?heartbeat=0',
    'amqp://lab:password@localhost/message_lab#other',
  ])
    assert.throws(() => localUrl(url, 'amqp:'))
  assert.throws(() =>
    localUrl(
      'mysql://lab:password@localhost/message_lab?database=production',
      'mysql:',
    ),
  )
})

describe(
  'actual MySQL and RabbitMQ; Java Flyway migration must run first',
  { timeout: 60000 },
  () => {
    let store: MessageStore
    let broker: BrokerLab
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
    async function once(work: () => void | Promise<void> = () => {}) {
      const channel = await broker.consumer()
      try {
        return await broker.consume(channel, store, work)
      } finally {
        await channel.close()
      }
    }
    async function assertDead() {
      const dead = await broker.readDead()
      assert.equal(
        dead.properties.headers?.['x-death']?.[0]?.reason,
        'rejected',
      )
    }
    test('publisher ACK does not imply routability or consumption', async () => {
      const payload = event()
      assert.deepEqual(await broker.probeUnroutable(payload), {
        confirmed: true,
        returned: true,
      })
      await broker.send(payload)
      assert.equal((await once()).outcome, 'APPLIED')
      assert.equal(await store.effects(payload.taskKey), 1)
      assert.equal(await store.markers(), 1)
    })
    test('commit then close before ACK causes broker redelivery with one SQL effect', async () => {
      const payload = event()
      await broker.send(payload)
      const channel = await broker.consumer()
      try {
        assert.equal(
          (await broker.consume(channel, store, () => {}, true)).outcome,
          'COMMITTED_UNACKED',
        )
      } finally {
        await channel.close().catch(() => {})
      }
      const duplicate = await once(() => {
        throw new Error('Duplicate must not execute effect')
      })
      assert.equal(duplicate.outcome, 'DUPLICATE')
      assert.equal(duplicate.redelivered, true)
      assert.equal(await store.effects(payload.taskKey), 1)
      assert.equal(await store.markers(), 1)
    })
    test('three precommit failures rollback SQL then native dead-letter', async () => {
      const payload = event()
      await broker.send(payload)
      for (let attempt = 0; attempt < 3; attempt++) {
        const result = await once(() => {
          throw new Error('Injected before SQL commit')
        })
        assert.equal(result.attempt, attempt)
        assert.equal(result.outcome, attempt < 2 ? 'RETRY' : 'DEAD')
        assert.equal(await store.effects(payload.taskKey), 0)
        assert.equal(await store.markers(), 0)
      }
      await assertDead()
    })
    test('transient recovery, poison payloads, invalid headers and conflicting identities', async () => {
      const payload = event()
      await broker.send(payload)
      assert.equal(
        (
          await once(() => {
            throw new Error('One failure')
          })
        ).outcome,
        'RETRY',
      )
      const recovered = await once()
      assert.equal(recovered.outcome, 'APPLIED')
      assert.equal(recovered.attempt, 1)
      for (const bytes of [Buffer.from('{'), Buffer.alloc(1025)]) {
        await broker.sendRaw(bytes, 0)
        assert.equal((await once()).outcome, 'DEAD')
        await assertDead()
      }
      for (const attempt of [-1, 3, '1', 0.5]) {
        await broker.sendRaw(Buffer.from(JSON.stringify(event())), attempt)
        assert.equal((await once()).outcome, 'DEAD')
        await assertDead()
      }
      await broker.send({ ...payload, taskKey: 'other-task' })
      assert.equal((await once()).outcome, 'DEAD')
      await assertDead()
      assert.equal(await store.effects(payload.taskKey), 1)
      assert.equal(await store.effects('other-task'), 0)
      assert.equal(await store.markers(), 1)
    })
    test('channel acquisition failure closes the already connected real broker connection', async () => {
      const url = localUrl(process.env.MESSAGE_AMQP_URL, 'amqp:').toString()
      const connection = await amqp.connect(url, { timeout: 3000 })
      const closeSpy = mock.method(connection, 'close')
      mock.method(connection, 'createChannel', async () => {
        throw new Error('Injected channel acquisition failure')
      })
      try {
        await assert.rejects(
          BrokerLab.open(url, async () => connection),
          /Injected channel acquisition failure/,
        )
        assert.equal(closeSpy.mock.callCount(), 1)
      } finally {
        mock.restoreAll()
      }
    })
    test('concurrent duplicate deliveries serialize on consumer/event unique key', async () => {
      const payload = event()
      await broker.send(payload)
      await broker.send(payload)
      const results = await Promise.all([once(), once()])
      assert.deepEqual(results.map((result) => result.outcome).sort(), [
        'APPLIED',
        'DUPLICATE',
      ])
      assert.equal(await store.effects(payload.taskKey), 1)
      assert.equal(await store.markers(), 1)
    })
  },
)
