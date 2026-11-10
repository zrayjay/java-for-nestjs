import { after, before, beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { client } from './client.js'
import {
  callbackRollback,
  catchApplicationError,
  commit,
  Failure,
  independentCommit,
  readTwice,
  wrongClient,
} from './transactions.js'

before(() => client.$connect())
after(() => client.$disconnect())
beforeEach(async () => {
  await client.entry.deleteMany()
  await client.balance.upsert({
    where: { id: 1 },
    create: { id: 1, amount: 100 },
    update: { amount: 100 },
  })
})
const labels = async () =>
  (await client.entry.findMany({ orderBy: { id: 'asc' } })).map(
    (row) => row.label,
  )

test('normal callback return commits the shared one-entry contract', async () => {
  await commit()
  assert.deepEqual(await labels(), ['commit'])
})
test('an uncaught application Error rolls back', async () => {
  await assert.rejects(callbackRollback(), Failure)
  assert.deepEqual(await labels(), [])
})
test('the root-client negative example escapes the intended transaction', async () => {
  await assert.rejects(wrongClient(), Failure)
  assert.deepEqual(await labels(), ['outside'])
})
test('a caught application Error does not automatically mark Spring-style rollback-only', async () => {
  await catchApplicationError()
  assert.deepEqual(await labels(), ['inner-required', 'outer-required'])
})
test('an explicitly separate transaction can survive outer rollback', async () => {
  await assert.rejects(independentCommit(), Failure)
  assert.deepEqual(await labels(), ['inner-independent'])
})
test('READ COMMITTED sees the committed update on the second read', async () => {
  assert.deepEqual(await readTwice('ReadCommitted'), [100, 200])
})
test('REPEATABLE READ keeps the first consistent read snapshot', async () => {
  assert.deepEqual(await readTwice('RepeatableRead'), [100, 100])
})
