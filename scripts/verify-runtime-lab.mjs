import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { hasMixCompilation } from './lib/runtime-evidence.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const javaTool = (name) =>
  process.env.JAVA_HOME
    ? join(
        process.env.JAVA_HOME,
        'bin',
        name + (process.platform === 'win32' ? '.exe' : ''),
      )
    : name
const classes = join(root, 'examples/java-basics/runtime-lab/target/classes')
const main = 'dev.javafornestjs.runtime.RuntimeLab'
function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: 'utf8',
    timeout: 30_000,
  })
  if (result.status !== 0)
    throw new Error(result.error?.message || result.stderr || result.stdout)
  return result.stdout + result.stderr
}
const bytecode = run(javaTool('javap'), [
  '-verbose',
  '-c',
  '-p',
  '-classpath',
  classes,
  main,
])
if (
  !bytecode.includes('major version: 65') ||
  !bytecode.includes('invokestatic')
)
  throw new Error('Expected Java21 bytecode evidence')
const interpreted = run(javaTool('java'), [
  '-Xint',
  '-cp',
  classes,
  main,
  'compute',
])
const compiled = run(javaTool('java'), [
  '-Xbatch',
  '-XX:-TieredCompilation',
  '-XX:CompileThreshold=100',
  '-XX:+PrintCompilation',
  '-XX:CompileCommand=compileonly,dev.javafornestjs.runtime.RuntimeLab::mix',
  '-cp',
  classes,
  main,
  'compute',
])
const value = (output) => output.match(/CHECKSUM=(-?\d+)/)?.[1]
const expectedChecksum = '380327344'
if (
  value(interpreted) !== expectedChecksum ||
  value(interpreted) !== value(compiled) ||
  !hasMixCompilation(compiled)
)
  throw new Error('JIT/interpreter evidence missing or result mismatch')
const nodeOutput = run(process.execPath, [
  'examples/nestjs-reference/dist/runtime/runtime-lab.js',
])
const nodeResult = JSON.parse(nodeOutput.trim())
assert.deepEqual(nodeResult.events, ['sync-start', 'cpu-finished', 'timer'])
if (
  String(nodeResult.checksum) !== value(interpreted) ||
  nodeResult.worker !== nodeResult.checksum
)
  throw new Error('Cross-runtime int32 contract differs')
const gc = run(javaTool('java'), [
  '-Xms16m',
  '-Xmx32m',
  '-XX:+UseSerialGC',
  '-Xlog:gc',
  '-cp',
  classes,
  main,
  'allocate',
])
if (!gc.includes('Pause Young') || !gc.includes('ALLOCATION_DONE=262144'))
  throw new Error('Controlled allocation/GC evidence missing')
const threads = run(javaTool('java'), ['-cp', classes, main, 'threads'])
if (!threads.includes('COUNTERS=1,2'))
  throw new Error('Lost update/atomic contract differs')
console.log(
  JSON.stringify(
    {
      bytecodeMajor: 65,
      checksum: Number(value(interpreted)),
      jitMethod: 'RuntimeLab::mix',
      gc: 'Serial young collection observed',
      nodeEvents: nodeResult.events,
      counters: [1, 2],
    },
    null,
    2,
  ),
)
