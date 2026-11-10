import assert from 'node:assert/strict'

export function validateCoreTree(tree) {
  assert.equal(tree.groupId, 'dev.javafornestjs')
  assert.equal(tree.artifactId, 'greeting-core')
  let dependencies = 0
  const visit = (node) => {
    for (const child of node.children ?? []) {
      dependencies++
      assert.equal(
        child.groupId.startsWith('org.springframework') &&
          child.scope !== 'test',
        false,
        `Core runtime framework dependency: ${child.groupId}:${child.artifactId}:${child.scope}`,
      )
      visit(child)
    }
  }
  visit(tree)
  assert.ok(
    dependencies > 0,
    'Expected real test dependency metadata, not an empty stand-in',
  )
  return dependencies
}
