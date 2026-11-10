import { test } from 'node:test'
import assert from 'node:assert/strict'
import { checksum, eventLoopOrder, mix, workerChecksum } from './runtime-lab'

test('int32 arithmetic agrees with the defined comparison contract', () => {
  assert.equal(mix(1), 38)
  assert.equal(checksum(3), 114)
  assert.equal(mix(2_147_483_647), 2_147_483_624)
})

test('a zero-delay timer cannot preempt synchronous CPU work', async () => {
  assert.deepEqual(await eventLoopOrder(), [
    'sync-start',
    'cpu-finished',
    'timer',
  ])
})

test('an actual worker performs the same calculation in another JS isolate', async () => {
  assert.equal(await workerChecksum(100_000), checksum(100_000))
})
