import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
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
for (const program of programs) {
  const child = spawn(program.command, program.args, {
    cwd: root,
    env: { ...process.env, PORT: '0' },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let output = ''
  child.stderr.on('data', (chunk) => {
    output = (output + chunk).slice(-20000)
  })
  try {
    const port = await new Promise((resolve, reject) => {
      const timeout = setTimeout(
        () => reject(new Error(`${program.name} startup timed out\n${output}`)),
        45000,
      )
      const clean = () => clearTimeout(timeout)
      child.once('error', (error) => {
        clean()
        reject(error)
      })
      child.once('exit', (code) => {
        clean()
        reject(new Error(`${program.name} exited ${code}\n${output}`))
      })
      child.stdout.on('data', (chunk) => {
        output = (output + chunk).slice(-20000)
        const match = output.match(new RegExp(`${program.marker}=([0-9]+)`))
        if (match) {
          clean()
          resolve(Number(match[1]))
        }
      })
    })
    assert.ok(port > 0 && port <= 65535)
    const base = `http://127.0.0.1:${port}`
    const request = (path, method = 'GET', body) =>
      fetch(base + path, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body,
        signal: AbortSignal.timeout(10000),
      })
    const created = await request(
      '/tasks',
      'POST',
      '{"title":"Learn Java","details":{"priority":2}}',
    )
    assert.equal(created.status, 201)
    const task = await created.json()
    assert.ok(Number.isInteger(task.id) && task.id > 0)
    assert.deepEqual(task, {
      id: task.id,
      title: 'Learn Java',
      details: { priority: 2 },
    })
    const path = `/tasks/${task.id}`
    assert.equal(created.headers.get('location'), path)
    assert.deepEqual(await (await request(path)).json(), task)
    assert.deepEqual(await (await request('/tasks')).json(), [task])
    const updated = await request(
      path,
      'PUT',
      '{"title":"Learn Spring","details":{"priority":5}}',
    )
    assert.equal(updated.status, 200)
    assert.deepEqual(await updated.json(), {
      id: task.id,
      title: 'Learn Spring',
      details: { priority: 5 },
    })
    for (const body of [
      '{}',
      '{"title":"Learn\\n","details":{"priority":1}}',
      '{',
      '{"title":"Learn","details":{"priority":"1"}}',
      '{"title":"Learn","details":{"priority":1.5}}',
      '{"title":"Learn","details":{"priority":6}}',
      '{"title":"Learn","details":{"priority":1},"extra":true}',
    ]) {
      const invalid = await request('/tasks', 'POST', body)
      assert.equal(invalid.status, 400)
      assert.deepEqual(await invalid.json(), { code: 'invalid_request' })
    }
    for (const path of [
      '/tasks/01',
      '/tasks/2147483648',
      '/tasks?limit=101',
      '/tasks?limit=1e1',
    ]) {
      assert.equal((await request(path)).status, 400)
    }
    assert.equal(
      (await request('/validation/draft', 'POST', '{"title":""}')).status,
      204,
    )
    assert.equal(
      (await request('/validation/publish', 'POST', '{"title":""}')).status,
      400,
    )
    const schema = await (await request('/openapi.json')).json()
    assert.ok(schema.paths['/tasks'].post.responses['201'])
    const removed = await request(path, 'DELETE')
    assert.equal(removed.status, 204)
    assert.equal(await removed.text(), '')
    const missing = await request(path)
    assert.equal(missing.status, 404)
    assert.deepEqual(await missing.json(), { code: 'not_found' })
    console.log(`${program.name}: packaged process HTTP contract passed`)
  } finally {
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
