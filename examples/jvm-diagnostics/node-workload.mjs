import { performance } from 'node:perf_hooks'
import { join } from 'node:path'
import { writeHeapSnapshot } from 'node:v8'
import { mkdirSync, writeFileSync } from 'node:fs'

const output = process.argv[2]
if (!output) throw new Error('Explicit disposable artifact directory required')
mkdirSync(output, { recursive: true })
const retained = Array.from({ length: 30000 }, (_, index) => ({
  index,
  label: 'teaching-only',
  payload: 'x'.repeat(128),
}))
const snapshot = writeHeapSnapshot(join(output, 'node.heapsnapshot'))
function cpuWork(deadline) {
  let checksum = 7
  while (performance.now() < deadline) {
    for (let index = 0; index < 50000; index++)
      checksum = Math.imul(checksum, 1664525) + 1013904223
    // Create garbage that is observable to the runtime without retaining it indefinitely.
    globalThis.diagnosticsScratch = Array.from({ length: 100 }, (_, index) => ({
      index,
      checksum,
    }))
  }
  return checksum
}
const started = performance.now()
const checksum = cpuWork(started + 5000)
const report = {
  retained: retained.length,
  checksum,
  measuredMs: performance.now() - started,
  snapshot,
}
writeFileSync(
  join(output, 'node-result.json'),
  JSON.stringify(report, null, 2) + '\n',
)
console.log(JSON.stringify(report))
