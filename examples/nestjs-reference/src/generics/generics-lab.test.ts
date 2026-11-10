import { test } from 'node:test'
import assert from 'node:assert/strict'
import { copyValues, isNumberArray, maxValue } from './generics-lab'

test('the numeric copy contract produces the same elements as Java', () => {
  const target: number[] = []
  copyValues([1, 2], target)
  assert.deepEqual(target, [1, 2])
  assert.deepEqual(maxValue({ value: 2 }, { value: 4 }), { value: 4 })
})

test('TypeScript mutable array assignability does not provide Java list invariance', () => {
  const numbers: number[] = [1]
  const widened: (number | string)[] = numbers
  widened.push('polluted')
  assert.equal(typeof numbers[1], 'string')
  assert.throws(() => numbers[1].toFixed(), TypeError)
})

test('runtime input needs a real validator after generic types disappear', () => {
  const input: unknown = JSON.parse('[1, "wrong"]')
  assert.equal(isNumberArray(input), false)
  assert.equal(isNumberArray([1, 2]), true)
})
