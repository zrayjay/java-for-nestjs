import { test } from 'node:test'
import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

test(
  'local Promise race deadline leaves the started operation active',
  { timeout: 3000 },
  async () => {
    let release: (() => void) | undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    let effects = 0
    const operation = gate.then(() => ++effects)
    try {
      await assert.rejects(
        Promise.race([
          operation,
          delay(30).then(() => {
            throw new Error('Local observation deadline')
          }),
        ]),
        /Local observation deadline/,
      )
      assert.equal(effects, 0)
    } finally {
      assert.ok(release)
      release()
    }
    assert.equal(await operation, 1)
    assert.equal(effects, 1)
  },
)
