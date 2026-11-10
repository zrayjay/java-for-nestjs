import assert from 'node:assert/strict'
import test from 'node:test'
import { observeMemory } from './memory-lab.js'

test('value copying, shared mutation and local rebinding are distinct', () => {
  assert.deepEqual(observeMemory(), {
    primitive: 1,
    shared: 2,
    sameReference: false,
    sameText: true,
  })
})
