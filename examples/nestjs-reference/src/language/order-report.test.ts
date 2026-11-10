import assert from 'node:assert/strict'
import test from 'node:test'
import { summarize } from './order-report.js'

test('validated unique orders share the Java sorted grouping contract', () => {
  assert.deepEqual(
    summarize([
      { id: 'o1', customer: 'alice', quantity: 2 },
      { id: 'o2', customer: 'bob', quantity: 1 },
      { id: 'o3', customer: 'alice', quantity: 3 },
    ]),
    ['alice:5', 'bob:1'],
  )
  assert.deepEqual(summarize([]), [])
})

test('invalid, duplicate and int32-overflow data are rejected explicitly', () => {
  assert.throws(
    () =>
      Reflect.apply(summarize, undefined, [
        [{ id: null, customer: 'alice', quantity: 1 }],
      ]),
    /invalid/,
  )
  assert.throws(
    () => summarize([{ id: 'o1', customer: 'alice', quantity: -1 }]),
    /invalid/,
  )
  const first = { id: 'o1', customer: 'alice', quantity: 1 }
  assert.throws(() => summarize([first, first]), /duplicate/)
  assert.throws(
    () =>
      summarize([
        { ...first, quantity: 2147483647 },
        { ...first, id: 'o2' },
      ]),
    /overflow/,
  )
})
