import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, 'target/compose-evidence')
const project = `p5-teaching-${randomUUID().slice(0, 8)}`
const base = [
  'compose',
  '-p',
  project,
  '-f',
  'project/deploy/compose/teaching.yaml',
]
const env = {
  ...process.env,
  TEACHING_IMAGE_TAG: process.env.TEACHING_IMAGE_TAG || 'local',
}
const origin = `http://127.0.0.1:${env.TEACHING_HTTP_PORT || '18089'}`
const sqlArgs = [
  ...base,
  'exec',
  '-T',
  'mysql',
  'sh',
  '-ec',
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --batch --skip-column-names --unbuffered -uroot data_lab',
]
await mkdir(output, { recursive: true })
function start(args, timeout = 60000) {
  const child = spawn('docker', args, {
    cwd: root,
    env,
    stdio: ['pipe', 'pipe', 'pipe'],
  })
  const state = { child, stdout: '', stderr: '' }
  child.stdout.on('data', (data) => {
    state.stdout += data
  })
  child.stderr.on('data', (data) => {
    state.stderr += data
  })
  state.done = new Promise((accept, reject) => {
    const timer = setTimeout(() => {
      child.kill()
      reject(new Error('Docker command exceeded bounded deadline'))
    }, timeout)
    child.on('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
    child.on('close', (code) => {
      clearTimeout(timer)
      if (code === 0) accept(state.stdout)
      else
        reject(
          new Error(
            `Docker command exited ${code}: ${state.stdout.slice(-15000)}\n${state.stderr.slice(-5000)}`,
          ),
        )
    })
  })
  // Attach immediately, including the deliberately long-lived SQL lock process.
  state.done.catch(() => {})
  return state
}
async function docker(args, input, timeout) {
  const state = start(args, timeout)
  state.child.stdin.end(input)
  return state.done
}
const compose = (args, timeout) =>
  docker([...base, ...args], undefined, timeout)
const sql = (statement) => docker(sqlArgs, statement + '\n')
async function until(check, label, milliseconds = 15000) {
  const deadline = Date.now() + milliseconds
  while (Date.now() < deadline) {
    if (await check()) return
    await delay(150)
  }
  throw new Error(`Timed out waiting for ${label}`)
}
async function request(path, method = 'GET', body, token, timeout = 20000) {
  const response = await fetch(origin + path, {
    method,
    headers: {
      ...(body ? { 'content-type': 'application/json' } : {}),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(timeout),
  })
  const text = await response.text()
  return { status: response.status, json: text ? JSON.parse(text) : undefined }
}
const results = {
  project,
  source: process.env.GITHUB_SHA || 'local',
  checks: [],
}
let lock
let primaryError
try {
  await compose(['config', '--quiet'])
  console.log('COMPOSE_PHASE build-and-start')
  const starting = start(
    [
      ...base,
      '--progress',
      'plain',
      'up',
      '-d',
      '--build',
      '--wait',
      '--wait-timeout',
      '240',
    ],
    900000,
  )
  starting.child.stdin.end()
  const launched = await starting.done.then(
    () => ({}),
    (error) => ({ error }),
  )
  await writeFile(
    resolve(output, 'build.log'),
    starting.stdout + '\n' + starting.stderr,
  )
  if (launched.error) throw launched.error
  const apiId = (await compose(['ps', '-q', 'api'])).trim()
  assert.match(apiId, /^[a-f0-9]+$/)
  const runtime = JSON.parse(
    await docker(['inspect', '--format', '{{json .Config.User}}', apiId]),
  )
  assert.equal(runtime, '10001:10001')
  results.runtimeUser = runtime
  results.composeVersion = (
    await docker(['compose', 'version', '--short'])
  ).trim()
  results.dockerVersion = (
    await docker(['version', '--format', '{{.Server.Version}}'])
  ).trim()
  results.image = (
    await docker(['inspect', '--format', '{{.Image}}', apiId])
  ).trim()
  results.mysqlDigest = JSON.parse(
    await docker([
      'image',
      'inspect',
      'mysql:8.4.7',
      '--format',
      '{{json .RepoDigests}}',
    ]),
  )
  for (const path of [
    '/actuator/health/liveness',
    '/actuator/health/readiness',
  ]) {
    const result = await request(path)
    assert.equal(result.status, 200)
    assert.equal(result.json.status, 'UP')
  }
  assert.equal((await request('/tasks')).status, 401)
  const login = await request('/auth/login', 'POST', {
    username: 'compose-admin',
    password: env.TEACHING_ADMIN_PASSWORD || 'disposable-admin-password',
  })
  assert.equal(login.status, 200)
  const token = login.json.accessToken
  assert.equal(typeof token, 'string')
  const created = await request(
    '/tasks',
    'POST',
    { title: 'Compose start', details: { priority: 2 } },
    token,
  )
  assert.equal(created.status, 201)
  const id = created.json.id
  assert.ok(Number.isSafeInteger(id) && id > 0)
  results.checks.push('empty-volume-migration-login-authorized-create')
  console.log('COMPOSE_PHASE database-readiness-failure')
  await compose(['stop', 'mysql'])
  await until(
    async () => (await request('/actuator/health/readiness')).status === 503,
    'readiness DOWN',
    20000,
  )
  assert.equal((await request('/actuator/health/liveness')).status, 200)
  await compose(['start', '--wait', '--wait-timeout', '180', 'mysql'], 240000)
  await until(
    async () => (await request('/actuator/health/readiness')).status === 200,
    'database recovery',
    30000,
  )
  results.checks.push('database-down-readiness503-liveness200-recovery')
  console.log('COMPOSE_PHASE lock-real-inflight-update')
  lock = start(sqlArgs, 60000)
  lock.child.stdin.write(
    `START TRANSACTION; SELECT id FROM data_tasks WHERE id=${id} FOR UPDATE; SELECT 'P5_LOCKED';\n`,
  )
  await until(() => lock.stdout.includes('P5_LOCKED'), 'SQL lock acquired')
  const update = request(
    `/tasks/${id}`,
    'PUT',
    { title: 'Compose drained', details: { priority: 3 } },
    token,
    35000,
  ).then(
    (value) => ({ value }),
    (error) => ({ error }),
  )
  await until(
    async () =>
      Number(
        (
          await sql('SELECT COUNT(*) FROM performance_schema.data_lock_waits;')
        ).trim(),
      ) > 0,
    'actual MySQL lock wait',
  )
  results.checks.push('actual-inflight-sql-lock-wait-observed')
  console.log('COMPOSE_PHASE signal-term-and-release-lock')
  const stopping = compose(['stop', 'api'], 45000)
  stopping.catch(() => {})
  await until(
    async () =>
      (await compose(['logs', '--no-color', 'api'])).includes(
        'Commencing graceful shutdown',
      ),
    'graceful shutdown admission stop',
    10000,
  )
  lock.child.stdin.end('COMMIT;\n')
  await lock.done
  lock = undefined
  const updated = await update
  if (updated.error) throw updated.error
  assert.equal(updated.value.status, 200)
  assert.equal(updated.value.json.title, 'Compose drained')
  await stopping
  const stopped = JSON.parse(
    await docker(['inspect', '--format', '{{json .State}}', apiId]),
  )
  assert.ok(
    [0, 143].includes(stopped.ExitCode),
    `Unexpected process exit ${stopped.ExitCode}`,
  )
  assert.equal(stopped.OOMKilled, false)
  results.exitCode = stopped.ExitCode
  assert.equal(
    (await sql(`SELECT title FROM data_tasks WHERE id=${id};`)).trim(),
    'Compose drained',
  )
  assert.equal(
    (
      await sql(
        `SELECT COUNT(*) FROM sec_audit WHERE event_code='task.updated' AND resource_id='${id}';`,
      )
    ).trim(),
    '1',
  )
  results.checks.push(
    'sigterm-inflight-http200-sql-and-audit-commit-no-sigkill',
  )
  await compose(['up', '-d', '--wait', '--wait-timeout', '180', 'api'], 240000)
  // The key volume and DB session persist; original access token remains valid.
  const persisted = await request(`/tasks/${id}`, 'GET', undefined, token)
  assert.equal(persisted.status, 200)
  assert.equal(persisted.json.title, 'Compose drained')
  results.checks.push('restart-preserves-task-session-and-rsa-key')
  results.status = 'passed'
  console.log('COMPOSE_RESULT', JSON.stringify(results))
} catch (error) {
  primaryError = error
  results.status = 'failed'
  results.error = error.message
  throw error
} finally {
  const cleanupErrors = []
  try {
    if (lock) {
      try {
        lock.child.stdin.end('ROLLBACK;\n')
        await lock.done
      } catch (error) {
        cleanupErrors.push(error)
      }
    }
    try {
      const logs = await compose(['logs', '--no-color'])
      await writeFile(resolve(output, 'services.log'), logs)
    } catch (error) {
      cleanupErrors.push(error)
    }
  } finally {
    // Always attempt cleanup, even when lock release or artifact writing failed.
    // project is generated here; never consume a caller's project name.
    try {
      await compose(['down', '--volumes', '--remove-orphans'], 120000)
    } catch (error) {
      cleanupErrors.push(error)
    }
    try {
      await writeFile(
        resolve(output, 'summary.json'),
        JSON.stringify(results, null, 2) + '\n',
      )
    } catch (error) {
      cleanupErrors.push(error)
    }
  }
  if (cleanupErrors.length && !primaryError)
    throw new AggregateError(
      cleanupErrors,
      'Compose cleanup or evidence failed',
    )
  if (cleanupErrors.length)
    console.error(
      'COMPOSE_CLEANUP_ERRORS',
      cleanupErrors.map((error) => error.message),
    )
}
