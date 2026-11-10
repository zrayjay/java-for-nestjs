import assert from 'node:assert/strict'
import test from 'node:test'
import {
  account,
  closeTrace,
  describe,
  find,
  nameOf,
  promiseTasks,
  readTag,
  TaggedRule,
  total,
} from './language-lab.js'

test('JS number is not Java int, and interface assignment is structural', () => {
  assert.equal(2147483647 + 1, 2147483648)
  assert.equal((2147483647 + 1) | 0, -2147483648)
  assert.equal(nameOf({ name: () => 'n' }), 'n')
})

test('explicit freezing and copying enforce this account boundary', () => {
  const roles = ['reader']
  const value = account('u1', roles)
  roles.push('admin')
  assert.deepEqual(value.roles, ['reader'])
  assert.equal(Object.isFrozen(value), true)
  assert.equal(Object.isFrozen(value.roles), true)
  assert.throws(() => account(' ', []), /id required/)
  assert.equal(describe({ kind: 'card', lastFour: '1234' }), 'card:1234')
  assert.equal(describe({ kind: 'cash', units: 20 }), 'cash:20')
})

test('JS object keys retain identity while normalized string keys compare values', () => {
  const first = account('u1', ['reader'])
  const equivalent = account('u1', ['reader'])
  const objects = new Map([[first, 20]])
  assert.equal(objects.get(equivalent), undefined)
  assert.equal(new Set([first, equivalent]).size, 2)
  assert.equal(new Map([[first.id, 20]]).get(equivalent.id), 20)
})

test('eager array pipelines and lazy fallback have observable differences', () => {
  assert.equal(total([1, 10, 20]), 60)
  const values = [account('u1', [])]
  assert.equal(find(values, 'u1'), 'u1')
  assert.equal(find(values, 'missing'), undefined)
  let calls = 0
  const mapped = [1, 2].map((value) => {
    calls++
    return value * 2
  })
  assert.equal(calls, 2)
  assert.deepEqual(mapped, [2, 4])
  let fallbacks = 0
  const fallback = (): string => {
    fallbacks++
    return 'fallback'
  }
  assert.equal(find(values, 'u1') ?? fallback(), 'u1')
  assert.equal(fallbacks, 0)
})

test('plain finally can replace a body error instead of suppressing it', () => {
  assert.deepEqual(closeTrace(false, false), ['body', 'close'])
  assert.throws(() => closeTrace(true, true), /close-failure/)
})

test('legacy TS decorator executes registration while metadata reading stays explicit', () => {
  assert.equal(readTag(TaggedRule), 'risk')
  assert.equal(readTag(class Untagged {}), undefined)
})

test('Promise gates coordinate async continuations without proving parallel JS threads', async () => {
  assert.deepEqual(await promiseTasks(), {
    values: [10, 20],
    events: ['start:1', 'start:2', 'finish:1', 'finish:2'],
  })
})

test('JS closure can observe later assignment of a captured local', () => {
  let value = 1
  const read = (): number => value
  value = 2
  assert.equal(read(), 2)
})
