import test from 'node:test'
import assert from 'node:assert/strict'
import { validateCoreTree } from '../lib/build-model.mjs'

const root = (children) => ({
  groupId: 'dev.javafornestjs',
  artifactId: 'greeting-core',
  children,
})

test('core accepts test-only dependencies but rejects framework leakage nested in a runtime graph', () => {
  assert.equal(
    validateCoreTree(
      root([
        {
          groupId: 'org.junit.jupiter',
          artifactId: 'junit-jupiter',
          scope: 'test',
        },
      ]),
    ),
    1,
  )
  assert.throws(
    () =>
      validateCoreTree(
        root([
          {
            groupId: 'example',
            artifactId: 'shared',
            scope: 'compile',
            children: [
              {
                groupId: 'org.springframework',
                artifactId: 'spring-context',
                scope: 'runtime',
              },
            ],
          },
        ]),
      ),
    /Core runtime framework dependency/,
  )
})

test('wrong module and empty evidence cannot masquerade as the inspected core model', () => {
  assert.throws(() =>
    validateCoreTree({ groupId: 'example', artifactId: 'other', children: [] }),
  )
  assert.throws(() => validateCoreTree(root([])), /empty stand-in/)
})
