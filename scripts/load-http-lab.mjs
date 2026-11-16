import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdirSync, existsSync, writeFileSync } from 'node:fs'
import { join, resolve, relative, sep, isAbsolute } from 'node:path'
import { performance } from 'node:perf_hooks'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, process.argv[2] ?? 'target/http-load')
const inside = relative(resolve(root, 'target'), output)
assert.ok(
  inside &&
    !isAbsolute(inside) &&
    inside !== '..' &&
    !inside.startsWith(`..${sep}`),
  'Artifacts must stay below target/',
)
assert.ok(!existsSync(output), 'Choose a fresh evidence directory')
mkdirSync(output, { recursive: true })
const java = process.env.JAVA_HOME
  ? join(
      process.env.JAVA_HOME,
      'bin',
      process.platform === 'win32' ? 'java.exe' : 'java',
    )
  : 'java'
const programs = [
  {
    name: 'Spring',
    command: java,
    args: [
      '-jar',
      'examples/spring-web-lab/target/spring-web-lab-0.1.0-SNAPSHOT-exec.jar',
      '--server.port=0',
    ],
    marker: 'JAVA_API_PORT',
  },
  {
    name: 'Nest',
    command: process.execPath,
    args: ['examples/nestjs-reference/dist/api/main.js'],
    marker: 'NEST_API_PORT',
  },
]
const reports = []
for (const program of programs) {
  const child = spawn(program.command, program.args, {
    cwd: root,
    env: {
      ...process.env,
      PORT: '0',
      LAB_STORAGE: 'memory',
      LAB_LABEL: 'load-fixture',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let log = ''
  child.stderr.on('data', (chunk) => {
    log = (log + chunk).slice(-20000)
  })
  try {
    const port = await new Promise((resolvePort, reject) => {
      const timer = setTimeout(
        () => reject(new Error(`${program.name} startup timeout`)),
        45000,
      )
      child.once('error', (error) => {
        clearTimeout(timer)
        reject(error)
      })
      child.once('exit', (code) => {
        clearTimeout(timer)
        reject(new Error(`Server exited ${code}`))
      })
      child.stdout.on('data', (chunk) => {
        log = (log + chunk).slice(-20000)
        const match = log.match(new RegExp(`${program.marker}=([0-9]+)`))
        if (match) {
          clearTimeout(timer)
          resolvePort(Number(match[1]))
        }
      })
    })
    const url = `http://127.0.0.1:${port}/tasks`
    for (let warm = 0; warm < 20; warm++) {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) })
      assert.equal(response.status, 200)
      assert.deepEqual(await response.json(), [])
    }
    const latencies = []
    let errors = 0
    const started = performance.now(),
      deadline = started + 3000
    await Promise.all(
      Array.from({ length: 4 }, async () => {
        while (performance.now() < deadline) {
          const before = performance.now()
          try {
            const response = await fetch(url, {
              signal: AbortSignal.timeout(2000),
            })
            assert.equal(response.status, 200)
            assert.deepEqual(await response.json(), [])
            latencies.push(performance.now() - before)
          } catch {
            errors++
          }
        }
      }),
    )
    const measuredMs = performance.now() - started
    assert.equal(errors, 0, 'Load includes failed requests')
    assert.ok(latencies.length > 0, 'No actual requests measured')
    latencies.sort((a, b) => a - b)
    const percentile = (fraction) =>
      latencies[
        Math.min(
          latencies.length - 1,
          Math.ceil(latencies.length * fraction) - 1,
        )
      ]
    const report = {
      program: program.name,
      concurrency: 4,
      requests: latencies.length,
      errors,
      measuredMs,
      requestsPerSecond: (latencies.length * 1000) / measuredMs,
      p50Ms: percentile(0.5),
      p95Ms: percentile(0.95),
    }
    reports.push(report)
    console.log(JSON.stringify(report))
  } finally {
    writeFileSync(resolve(output, `${program.name.toLowerCase()}.log`), log)
    if (child.exitCode === null && child.signalCode === null) {
      const exited = once(child, 'exit')
      child.kill()
      const timeout = setTimeout(() => child.kill('SIGKILL'), 5000)
      try {
        await exited
      } finally {
        clearTimeout(timeout)
      }
    }
  }
}
writeFileSync(
  resolve(output, 'report.json'),
  JSON.stringify(
    {
      node: process.version,
      note: 'Local teaching observations; no cross-runtime performance ranking.',
      reports,
    },
    null,
    2,
  ) + '\n',
)
