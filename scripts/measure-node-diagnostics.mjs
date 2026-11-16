import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  statSync,
} from 'node:fs'
import { resolve, relative, sep, isAbsolute } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, process.argv[2] ?? 'target/node-diagnostics')
const withinTarget = relative(resolve(root, 'target'), output)
assert.ok(
  withinTarget &&
    !isAbsolute(withinTarget) &&
    withinTarget !== '..' &&
    !withinTarget.startsWith(`..${sep}`),
  'Artifacts must remain under repository target/',
)
assert.ok(
  !existsSync(output),
  'Choose a new output directory; evidence is never overwritten',
)
mkdirSync(output, { recursive: true })
const result = spawnSync(
  process.execPath,
  [
    '--max-old-space-size=96',
    '--trace-gc',
    '--cpu-prof',
    `--cpu-prof-dir=${output}`,
    '--cpu-prof-name=node.cpuprofile',
    '--heap-prof',
    `--heap-prof-dir=${output}`,
    '--heap-prof-name=node.heapprofile',
    'examples/jvm-diagnostics/node-workload.mjs',
    output,
  ],
  { cwd: root, encoding: 'utf8', timeout: 25000, maxBuffer: 2 * 1024 * 1024 },
)
writeFileSync(resolve(output, 'node.stdout.txt'), result.stdout ?? '')
writeFileSync(resolve(output, 'node.stderr.txt'), result.stderr ?? '')
assert.ifError(result.error)
assert.equal(
  result.status,
  0,
  'Disposable Node fixture failed; inspect captured stderr',
)
const read = (name) => JSON.parse(readFileSync(resolve(output, name), 'utf8'))
const cpu = read('node.cpuprofile')
assert.ok(
  cpu.nodes.some((node) => node.callFrame.functionName === 'cpuWork'),
  'CPU profile must identify the fixture',
)
assert.ok(cpu.samples.length > 0, 'CPU profile must contain actual samples')
const heap = read('node.heapprofile')
assert.ok(
  heap.samples.length > 0,
  'Allocation profile must contain actual samples',
)
const snapshot = read('node.heapsnapshot')
assert.ok(
  snapshot.snapshot.node_count > 0 && snapshot.snapshot.edge_count > 0,
  'Heap snapshot must contain a real graph',
)
assert.ok(
  /Scavenge|Mark-Compact|Mark-sweep/.test(result.stdout),
  'No actual V8 GC event observed',
)
const workload = read('node-result.json')
assert.equal(workload.retained, 30000)
const report = {
  node: process.version,
  profileSamples: cpu.samples.length,
  heapProfileSamples: heap.samples.length,
  snapshotNodes: snapshot.snapshot.node_count,
  snapshotBytes: statSync(resolve(output, 'node.heapsnapshot')).size,
  measuredMs: workload.measuredMs,
  gcObserved: true,
}
writeFileSync(
  resolve(output, 'report.json'),
  JSON.stringify(report, null, 2) + '\n',
)
console.log(JSON.stringify(report, null, 2))
