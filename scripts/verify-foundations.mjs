import { spawnSync } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = fileURLToPath(new URL('../', import.meta.url))
const tool = (name) =>
  process.env.JAVA_HOME
    ? join(
        process.env.JAVA_HOME,
        'bin',
        name + (process.platform === 'win32' ? '.exe' : ''),
      )
    : name
function run(name, args, expected = 0) {
  const result = spawnSync(tool(name), args, {
    cwd: root,
    encoding: 'utf8',
    timeout: 30_000,
  })
  if (result.error) throw result.error
  if (expected === 0)
    assert.equal(result.status, 0, result.stdout + result.stderr)
  else assert.notEqual(result.status, 0, 'Negative case unexpectedly succeeded')
  return result.stdout + result.stderr
}
const output = join(root, 'examples/java-basics/runtime-lab/target/foundations')
await mkdir(output, { recursive: true })
const main = 'dev.javafornestjs.runtime.MemoryLab'
const source = join(
  root,
  'examples/java-basics/runtime-lab/src/main/java/dev/javafornestjs/runtime/MemoryLab.java',
)
run('javac', ['--release', '21', '-g', '-d', output, source])
const expected =
  'Result[primitive=1, shared=2, sameReference=false, sameText=true]'
assert.equal(run('java', ['-cp', output, main]).trim(), expected)
const bytecode = run('javap', ['-verbose', '-p', '-classpath', output, main])
assert.match(bytecode, /major version: 65/)
assert.match(bytecode, /LineNumberTable/)
assert.match(bytecode, /LocalVariableTable/)
const jar = join(output, 'memory-lab.jar')
run('jar', [
  '--create',
  '--file',
  jar,
  '--main-class',
  main,
  '-C',
  output,
  'dev',
])
assert.equal(run('java', ['-jar', jar]).trim(), expected)
const missing = run(
  'java',
  ['-cp', output, 'dev.javafornestjs.runtime.Absent'],
  1,
)
assert.match(
  missing,
  /ClassNotFoundException: dev\.javafornestjs\.runtime\.Absent/,
)
const emptyClasspath = join(output, 'empty')
await mkdir(emptyClasspath, { recursive: true })
const wrongRoot = run('java', ['-cp', emptyClasspath, main], 1)
assert.match(
  wrongRoot,
  /ClassNotFoundException: dev\.javafornestjs\.runtime\.MemoryLab/,
)
const moduleDescription = run('java', ['--describe-module', 'java.base'])
assert.match(moduleDescription, /exports java\.lang(?:\r?\n|$)/)
console.log(
  'FOUNDATIONS: release21 major65 + debug tables + classpath + executable JAR + 2 expected startup failures + java.base exports verified',
)
