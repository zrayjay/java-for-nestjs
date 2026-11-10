import { test } from 'node:test'
import assert from 'node:assert/strict'
import { hasMixCompilation } from '../lib/runtime-evidence.mjs'

test('recognizes numbered HotSpot compilation records, including blocking mode', () => {
  assert.equal(
    hasMixCompilation(
      '59    1    b        dev.javafornestjs.runtime.RuntimeLab::mix (8 bytes)',
    ),
    true,
  )
  assert.equal(
    hasMixCompilation(
      '  61   2       3    dev.javafornestjs.runtime.RuntimeLab::mix (8 bytes)',
    ),
    true,
  )
})

test('exclusion and non-compilable diagnostics are not JIT success evidence', () => {
  const excluded =
    '### Excluding compile: static dev.javafornestjs.runtime.RuntimeLab::mix\nmade not compilable on level 4 dev.javafornestjs.runtime.RuntimeLab::mix (8 bytes) excluded by CompileCommand\nCHECKSUM=380327344'
  assert.equal(hasMixCompilation(excluded), false)
  assert.equal(
    hasMixCompilation(
      '59 1 b dev.javafornestjs.runtime.RuntimeLab::mix (8 bytes) made not compilable',
    ),
    false,
  )
})

test('a configured compilation command and correct checksum do not prove compilation', () => {
  assert.equal(
    hasMixCompilation(
      'CompileCommand: compileonly dev/javafornestjs/runtime/RuntimeLab.mix bool compileonly = true\nCHECKSUM=380327344',
    ),
    false,
  )
})
