import { readFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { validateCoreTree } from './lib/build-model.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const target = join(root, 'examples/java-basics/greeting-core/target')
const tree = JSON.parse(
  await readFile(join(target, 'dependency-tree.json'), 'utf8'),
)
const count = validateCoreTree(tree)
const jdeps = process.env.JAVA_HOME
  ? join(
      process.env.JAVA_HOME,
      'bin',
      process.platform === 'win32' ? 'jdeps.exe' : 'jdeps',
    )
  : 'jdeps'
const result = spawnSync(
  jdeps,
  [
    '--multi-release',
    '21',
    '-s',
    join(target, 'greeting-core-0.1.0-SNAPSHOT.jar'),
  ],
  { cwd: root, encoding: 'utf8', timeout: 30_000 },
)
if (result.error) throw result.error
assert.equal(result.status, 0, result.stdout + result.stderr)
const targets = [...result.stdout.matchAll(/ -> (.+)$/gm)].map((match) =>
  match[1].trim(),
)
assert.deepEqual(
  targets,
  ['java.base'],
  'Core bytecode must depend only on java.base in this example',
)
const javap = process.env.JAVA_HOME
  ? join(
      process.env.JAVA_HOME,
      'bin',
      process.platform === 'win32' ? 'javap.exe' : 'javap',
    )
  : 'javap'
const bytecode = spawnSync(
  javap,
  [
    '-verbose',
    '-classpath',
    join(target, 'greeting-core-0.1.0-SNAPSHOT.jar'),
    'dev.javafornestjs.greeting.GreetingPolicy',
  ],
  { cwd: root, encoding: 'utf8', timeout: 30_000 },
)
if (bytecode.error) throw bytecode.error
assert.equal(bytecode.status, 0, bytecode.stdout + bytecode.stderr)
assert.match(bytecode.stdout, /major version: 65/)
console.log(
  `BUILD_MODEL: ${count} resolved dependencies inspected; core bytecode -> java.base only; major65`,
)
