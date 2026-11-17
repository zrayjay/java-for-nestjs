import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

// P5-05 delivery drill: build two marker-distinct images, deploy v1, roll to
// v2 under live traffic, then rollout undo and verify version, business data
// and the original session all come back. Requires a kind cluster whose
// context kubectl reaches; images are built locally and kind-loaded.
const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, 'target/delivery-evidence')
const namespace = 'teaching'
const image = 'java-for-nestjs-teaching'
const clusterName = process.env.KIND_CLUSTER_NAME || 'p5-teaching'
await mkdir(output, { recursive: true })

function start(command, args, timeout = 60000) {
  const child = spawn(command, args, {
    cwd: root,
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
      reject(new Error('command exceeded bounded deadline'))
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
            `${command} exited ${code}: ${state.stdout.slice(-12000)}\n${state.stderr.slice(-4000)}`,
          ),
        )
    })
  })
  state.done.catch(() => {})
  return state
}
async function run(command, args, timeout) {
  const state = start(command, args, timeout)
  state.child.stdin.end()
  return state.done
}
const kubectl = (args, timeout) => run('kubectl', args, timeout)
const ns = (args, timeout) =>
  kubectl(['-n', namespace, ...args], undefined, timeout)
async function json(args, timeout) {
  return JSON.parse(await kubectl(args, timeout))
}
async function nsJson(args, timeout) {
  return JSON.parse(await ns(args, timeout))
}
async function until(check, label, milliseconds = 15000) {
  const deadline = Date.now() + milliseconds
  while (Date.now() < deadline) {
    let value
    try {
      value = await check()
    } catch {
      value = undefined
    }
    if (value) return value
    await delay(250)
  }
  throw new Error(`Timed out waiting for ${label}`)
}
const clientTemplate = (command) => `apiVersion: v1
kind: Pod
metadata:
  name: drill-client
  namespace: ${namespace}
spec:
  restartPolicy: Never
  activeDeadlineSeconds: 900
  containers:
    - name: client
      image: ${image}:v1
      imagePullPolicy: IfNotPresent
      command: ['sh', '-ec']
      args:
        - echo ${Buffer.from(command, 'utf8').toString('base64')} | base64 -d | sh -s
`
async function clientCommand(command, timeout = 120000) {
  await kubectl([
    'delete',
    'pod',
    'drill-client',
    '-n',
    namespace,
    '--ignore-not-found=true',
    '--wait=true',
    '--timeout=30s',
  ])
  await kubectl(['apply', '-f', '-'], clientTemplate(command))
  await until(
    async () =>
      (await nsJson(['pod', 'drill-client'])).status.phase === 'Succeeded',
    'drill-client completion',
    timeout,
  )
  return ns(['logs', 'drill-client'], 30000)
}

const results = {
  source: process.env.GITHUB_SHA || 'local',
  cluster: (await json(['version', '-o', 'json'])).serverVersion.gitVersion,
  checks: [],
}
let primaryError
try {
  console.log('DELIVERY_PHASE build-two-markers')
  await run(
    'docker',
    [
      'build',
      '--build-arg',
      `BUILD_MARKER=drill-v1-${process.env.GITHUB_SHA || 'local'}`,
      '-f',
      'project/deploy/compose/Dockerfile',
      '-t',
      `${image}:v1`,
      '.',
    ],
    900000,
  )
  await run(
    'docker',
    [
      'build',
      '--build-arg',
      'BUILD_MARKER=drill-v2-rollback-target',
      '-f',
      'project/deploy/compose/Dockerfile',
      '-t',
      `${image}:v2`,
      '.',
    ],
    900000,
  )
  const kindBin = process.env.KIND_BIN || 'kind'
  // The manifest references :local (api image + wait-keys init container).
  // Alias v1 as :local and load it too, so a fresh cluster never tries to
  // pull java-for-nestjs-teaching:local from a remote registry.
  await run('docker', ['tag', `${image}:v1`, `${image}:local`])
  for (const tag of ['v1', 'v2', 'local']) {
    await run(
      kindBin,
      ['load', 'docker-image', `${image}:${tag}`, '--name', clusterName],
      300000,
    )
  }
  // --entrypoint avoids launching the app: the data-layer guard correctly
  // rejects a container without its compose environment.
  const localMarker = (
    await run('docker', [
      'run',
      '--rm',
      '--entrypoint',
      'printenv',
      `${image}:v1`,
      'BUILD_MARKER',
    ])
  ).trim()
  assert.match(localMarker, /^drill-v1-/)
  const v2Marker = (
    await run('docker', [
      'run',
      '--rm',
      '--entrypoint',
      'printenv',
      `${image}:v2`,
      'BUILD_MARKER',
    ])
  ).trim()
  assert.equal(v2Marker, 'drill-v2-rollback-target')
  results.markers = { v1: localMarker, v2: v2Marker }
  results.checks.push('marker-distinct-images-built')

  console.log('DELIVERY_PHASE deploy-v1')
  await kubectl(
    [
      'delete',
      'namespace',
      namespace,
      '--ignore-not-found=true',
      '--wait=true',
      '--timeout=60s',
    ],
    90000,
  )
  // Apply the base manifest and wait for the :local first boot (mysql init,
  // migrations, key job) BEFORE switching the image: this keeps the v1
  // rollout from stacking on a cold first boot.
  await kubectl(['apply', '-f', 'project/deploy/k8s/teaching.yaml'], 90000)
  await until(
    async () =>
      (await nsJson(['deployment', 'api'])).status.readyReplicas === 1,
    'base :local deployment ready',
    900000,
  )
  await ns(['set', 'image', 'deployment/api', `api=${image}:v1`], 60000)
  await until(
    async () => {
      const deployment = await nsJson(['deployment', 'api'])
      return (
        deployment.status.readyReplicas === 1 &&
        deployment.spec.template.spec.containers[0].image === `${image}:v1`
      )
    },
    'v1 deployment ready',
    600000,
  )
  await until(
    async () => (await nsJson(['job', 'keys-init'])).status.succeeded === 1,
    'keys job success',
    240000,
  )
  const login = await clientCommand(
    `login=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/login -H 'content-type: application/json' -d '{"username":"compose-admin","password":"disposable-admin-password"}') &&
    token=$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    refresh=$(printf '%s' "$login" | sed -n 's/.*"refreshToken":"\\([^"]*\\)".*/\\1/p') &&
    test -n "$token" && test -n "$refresh" && echo "TOKEN_OK" &&
    curl -fsS -o /dev/null -w '%{http_code}' -X POST http://api.teaching.svc.cluster.local:8089/tasks -H "authorization: Bearer $token" -H 'content-type: application/json' -d '{"title":"Delivery drill","details":{"priority":1}}' > /tmp/c.status &&
    echo "CREATE_$(cat /tmp/c.status)" &&
    echo "REFRESH_PREFIX_$refresh"`,
    180000,
  )
  assert.match(login, /TOKEN_OK/)
  assert.match(login, /CREATE_201/)
  const originalRefresh = login.match(/REFRESH_PREFIX_([^\s"]+)/)?.[1]
  assert.ok(originalRefresh, 'original refresh token captured')
  results.checks.push('v1-deployed-login-and-task-created')

  console.log('DELIVERY_PHASE roll-to-v2')
  const beforeImage = (await nsJson(['deployment', 'api'])).spec.template.spec
    .containers[0].image
  await ns(['set', 'image', 'deployment/api', `api=${image}:v2`], 60000)
  const looper = clientCommand(
    `failures=0; requests=0;
    deadline=$(( $(date +%s) + 180 ));
    while [ "$(date +%s)" -lt "$deadline" ]; do
      code=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://api.teaching.svc.cluster.local:8089/actuator/health/readiness || echo curl_error);
      requests=$((requests+1));
      case "$code" in
        200) : ;;
        *) failures=$((failures+1)); echo "FAILED_REQUEST $code" ;;
      esac;
      sleep 0.3;
    done;
    echo "REQUESTS_$requests FAILURES_$failures"`,
    300000,
  )
  await until(
    async () => {
      const status = await ns(
        ['rollout', 'status', 'deployment', 'api', '--timeout=5s'],
        20000,
      ).catch((error) => String(error))
      return String(status).trim().endsWith('successfully rolled out')
    },
    'v2 rollout completion',
    300000,
  )
  const looperOutput = await looper
  const requestCount = Number(looperOutput.match(/REQUESTS_(\d+)/)?.[1])
  assert.ok(
    Number.isSafeInteger(requestCount) && requestCount >= 1,
    `suspicious looper request count ${requestCount}`,
  )
  assert.match(looperOutput, /FAILURES_0/)
  assert.doesNotMatch(looperOutput, /FAILED_REQUEST/)
  const podV2 = (await nsJson(['pods', '-l', 'app=api'])).items.find(
    (p) => !p.metadata.deletionTimestamp,
  )
  const podMarkerV2 = (
    await ns(['exec', podV2.metadata.name, '--', 'printenv', 'BUILD_MARKER'])
  ).trim()
  assert.equal(podMarkerV2, 'drill-v2-rollback-target')
  const afterImage = (await nsJson(['deployment', 'api'])).spec.template.spec
    .containers[0].image
  assert.equal(afterImage, `${image}:v2`)
  assert.notEqual(afterImage, beforeImage)
  results.rollForward = {
    requests: requestCount,
    failures: 0,
    marker: podMarkerV2,
  }
  results.checks.push('v2-rollout-zero-failure-marker-v2')

  console.log('DELIVERY_PHASE rollback-to-v1')
  await ns(['rollout', 'undo', 'deployment', 'api'], 60000)
  const looper2 = clientCommand(
    `failures=0; requests=0;
    deadline=$(( $(date +%s) + 180 ));
    while [ "$(date +%s)" -lt "$deadline" ]; do
      code=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://api.teaching.svc.cluster.local:8089/actuator/health/readiness || echo curl_error);
      requests=$((requests+1));
      case "$code" in
        200) : ;;
        *) failures=$((failures+1)); echo "FAILED_REQUEST $code" ;;
      esac;
      sleep 0.3;
    done;
    echo "REQUESTS_$requests FAILURES_$failures"`,
    300000,
  )
  await until(
    async () => {
      const status = await ns(
        ['rollout', 'status', 'deployment', 'api', '--timeout=5s'],
        20000,
      ).catch((error) => String(error))
      return String(status).trim().endsWith('successfully rolled out')
    },
    'rollback completion',
    300000,
  )
  const looperOutput2 = await looper2
  const requestCount2 = Number(looperOutput2.match(/REQUESTS_(\d+)/)?.[1])
  assert.ok(
    Number.isSafeInteger(requestCount2) && requestCount2 >= 1,
    `suspicious looper request count ${requestCount2}`,
  )
  assert.match(looperOutput2, /FAILURES_0/)
  assert.doesNotMatch(looperOutput2, /FAILED_REQUEST/)
  const rolledBackImage = (await nsJson(['deployment', 'api'])).spec.template
    .spec.containers[0].image
  assert.equal(rolledBackImage, `${image}:v1`)
  const podV1 = (await nsJson(['pods', '-l', 'app=api'])).items.find(
    (p) => !p.metadata.deletionTimestamp,
  )
  const podMarkerV1 = (
    await ns(['exec', podV1.metadata.name, '--', 'printenv', 'BUILD_MARKER'])
  ).trim()
  assert.match(podMarkerV1, /^drill-v1-/)
  const history = await ns(['rollout', 'history', 'deployment', 'api'], 60000)
  results.rollback = {
    requests: requestCount2,
    failures: 0,
    marker: podMarkerV1,
    image: rolledBackImage,
  }
  await writeFile(resolve(output, 'rollout-history.log'), history)
  results.checks.push('undo-returns-v1-image-and-marker')

  console.log('DELIVERY_PHASE business-continuity-after-rollback')
  const persisted = await clientCommand(
    `token=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/refresh -H 'content-type: application/json' -d '{"refreshToken":"${originalRefresh}"}' | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    test -n "$token" && echo "REFRESH_OK" &&
    curl -fsS -H "authorization: Bearer $token" http://api.teaching.svc.cluster.local:8089/tasks | grep -q 'Delivery drill' && echo 'TASK_PRESERVED'`,
    120000,
  )
  assert.match(persisted, /REFRESH_OK/)
  assert.match(persisted, /TASK_PRESERVED/)
  results.checks.push('rollback-preserves-task-and-original-session')

  results.status = 'passed'
  console.log('DELIVERY_RESULT', JSON.stringify(results))
} catch (error) {
  primaryError = error
  results.status = 'failed'
  results.error = error.message
  throw error
} finally {
  const cleanupErrors = []
  try {
    const describe = await ns(['describe', 'deployment', 'api'], 60000).catch(
      (error) => `describe failed: ${error.message}`,
    )
    await writeFile(resolve(output, 'api-describe.log'), describe)
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
  if (cleanupErrors.length && !primaryError)
    throw new AggregateError(cleanupErrors, 'Delivery evidence failed')
  if (cleanupErrors.length)
    console.error(
      'DELIVERY_CLEANUP_ERRORS',
      cleanupErrors.map((error) => error.message),
    )
}
