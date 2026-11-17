import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

// Real kind-cluster drill for the P5-04 K8s teaching runtime.
// Requires: a reachable kubectl context, a running Docker engine, and the
// teaching image loaded into the cluster nodes (kind load docker-image).
const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, 'target/k8s-evidence')
const namespace = 'teaching'
const image = 'java-for-nestjs-teaching:local'
const manifest = 'project/deploy/k8s/teaching.yaml'
const clusterName = process.env.KIND_CLUSTER_NAME || 'p5-teaching'
await mkdir(output, { recursive: true })

function start(args, timeout = 60000) {
  const child = spawn('kubectl', args, {
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
      reject(new Error('kubectl command exceeded bounded deadline'))
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
            `kubectl exited ${code}: ${state.stdout.slice(-15000)}\n${state.stderr.slice(-5000)}`,
          ),
        )
    })
  })
  state.done.catch(() => {})
  return state
}
async function kubectl(args, input, timeout) {
  const state = start(args, timeout)
  state.child.stdin.end(input)
  return state.done
}
const k = (args, timeout) => kubectl(args, undefined, timeout)
const ns = (args, timeout) =>
  kubectl(['-n', namespace, ...args], undefined, timeout)
async function json(path, timeout) {
  return JSON.parse(await ns(['get', ...path, '-o', 'json'], timeout))
}
async function until(check, label, milliseconds = 15000) {
  const deadline = Date.now() + milliseconds
  while (Date.now() < deadline) {
    let value
    try {
      value = await check()
    } catch {
      // Transient kubectl/await failures are retried until the deadline.
      value = undefined
    }
    if (value) return value
    await delay(200)
  }
  throw new Error(`Timed out waiting for ${label}`)
}
async function shellIn(pod, container, command, timeout = 30000) {
  return ns(['exec', pod, '-c', container, '--', 'sh', '-ec', command], timeout)
}

// HTTP client from inside the cluster, run as a one-shot pod against the Service.
// The command is base64-encoded to keep arbitrary shell (including newlines) out of YAML.
const clientJob = 'drill-client'
const clientTemplate = (command) => `apiVersion: v1
kind: Pod
metadata:
  name: ${clientJob}
  namespace: ${namespace}
spec:
  restartPolicy: Never
  activeDeadlineSeconds: 900
  containers:
    - name: client
      image: java-for-nestjs-teaching:local
      imagePullPolicy: IfNotPresent
      command: ['sh', '-ec']
      args:
        - echo ${Buffer.from(command, 'utf8').toString('base64')} | base64 -d | sh -s
`
async function clientCommand(command, timeout = 120000) {
  await kubectl([
    'delete',
    'pod',
    clientJob,
    '-n',
    namespace,
    '--ignore-not-found=true',
    '--wait=true',
    '--timeout=30s',
  ])
  await kubectl(['apply', '-f', '-'], clientTemplate(command))
  await until(
    async () => (await json(['pod', clientJob])).status.phase === 'Succeeded',
    `${clientJob} completion`,
    timeout,
  )
  return ns(['logs', clientJob], 30000)
}

const results = {
  source: process.env.GITHUB_SHA || 'local',
  cluster: await k(['version', '-o', 'json'], 30000).then(
    (out) => JSON.parse(out).serverVersion,
    async () => JSON.parse(await k(['version', '-o', 'json'])).serverVersion,
  ),
  checks: [],
}
let primaryError
try {
  console.log('K8S_PHASE fresh-namespace')
  await k(
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
  await k(['apply', '-f', manifest], 90000)

  console.log('K8S_PHASE first-pod-ready')
  await until(
    async () => (await json(['deployment', 'api'])).status.readyReplicas === 1,
    'api deployment ready',
    420000,
  )
  await until(
    async () => (await json(['job', 'keys-init'])).status.succeeded === 1,
    'keys job success',
    240000,
  )
  const apiPod = (await json(['pods', '-l', 'app=api'])).items[0].metadata.name
  const apiSecurity = (await json(['pod', apiPod])).spec.securityContext
  assert.equal(apiSecurity.runAsUser, 10001)
  assert.equal(apiSecurity.runAsNonRoot, true)
  results.runtimeUser = `${apiSecurity.runAsUser}:${apiSecurity.runAsGroup}`

  // Real business flow through the cluster-internal Service DNS name.
  console.log('K8S_PHASE login-and-create')
  const login = await clientCommand(
    `login=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/login -H 'content-type: application/json' -d '{"username":"compose-admin","password":"disposable-admin-password"}') &&
    token=$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    refresh=$(printf '%s' "$login" | sed -n 's/.*"refreshToken":"\\([^"]*\\)".*/\\1/p') &&
    test -n "$token" && test -n "$refresh" && echo "TOKEN_OK" &&
    curl -fsS -o /dev/null -w '%{http_code}' -X POST http://api.teaching.svc.cluster.local:8089/tasks -H "authorization: Bearer $token" -H 'content-type: application/json' -d '{"title":"K8s drill","details":{"priority":2}}' > /tmp/create.status &&
    echo "CREATE_$(cat /tmp/create.status)" &&
    curl -fsS http://api.teaching.svc.cluster.local:8089/actuator/health/readiness | grep -q '"status":"UP"' && echo 'READY_UP' &&
    echo "REFRESH_PREFIX_$refresh"`,
    180000,
  )
  assert.match(login, /TOKEN_OK/)
  assert.match(login, /CREATE_201/)
  assert.match(login, /READY_UP/)
  // The original refresh token (7-day validity) is kept in memory only; the
  // access token itself expires after 300s, long before the rollout completes.
  const originalRefresh = login.match(/REFRESH_PREFIX_([^\s"]+)/)?.[1]
  assert.ok(originalRefresh, 'original refresh token captured')
  results.checks.push('fresh-volume-migration-login-authorized-create')

  console.log('K8S_PHASE database-readiness-failure')
  const apiPodIp = (await json(['pod', apiPod])).status.podIP
  await ns(['scale', 'deployment', 'mysql', '--replicas=0'], 60000)
  await until(
    async () =>
      (await json(['deployment', 'api'])).status.conditions?.some(
        (c) => c.type === 'Available' && c.status === 'False',
      ),
    'api unavailable after DB loss',
    30000,
  )
  // The unready pod is removed from the Service endpoints: traffic is shed there.
  await until(
    async () => {
      // Ready addresses must be gone; notReadyAddresses may legitimately remain.
      const subsets = (await json(['endpoints', 'api'])).subsets || []
      return subsets.every(
        (subset) => !subset.addresses || subset.addresses.length === 0,
      )
    },
    'service ready endpoints drained',
    30000,
  )
  // Directly against the pod IP the app itself still answers.
  const readyDuringOutage = await clientCommand(
    `code=$(curl -s -o /dev/null -w '%{http_code}' http://${apiPodIp}:8089/actuator/health/readiness); echo "READY_$code"`,
    120000,
  )
  assert.match(readyDuringOutage, /READY_503/)
  const liveDuringOutage = await clientCommand(
    `code=$(curl -s -o /dev/null -w '%{http_code}' http://${apiPodIp}:8089/actuator/health/liveness); echo "LIVE_$code"`,
    120000,
  )
  assert.match(liveDuringOutage, /LIVE_200/)
  await ns(['scale', 'deployment', 'mysql', '--replicas=1'], 60000)
  await until(
    async () =>
      (await json(['deployment', 'mysql'])).status.readyReplicas === 1,
    'mysql recovery',
    180000,
  )
  await until(
    async () => {
      const deployment = await json(['deployment', 'api'])
      return deployment.status.conditions?.some(
        (c) => c.type === 'Available' && c.status === 'True',
      )
    },
    'api available after DB recovery',
    120000,
  )
  results.checks.push('database-down-readiness503-liveness200-recovery')

  console.log('K8S_PHASE rolling-update-zero-failure')
  await ns(['scale', 'deployment', 'api', '--replicas=2'], 60000)
  await until(
    async () => (await json(['deployment', 'api'])).status.readyReplicas === 2,
    'api scaled to 2',
    300000,
  )
  const before = await json(['deployment', 'api'])
  const oldPodNames = new Set(
    (await json(['pods', '-l', 'app=api'])).items.map((p) => p.metadata.name),
  )
  const rollouter = ns(['rollout', 'restart', 'deployment', 'api'], 60_000)
  // Old pod objects are GC'd shortly after termination. A streaming watch sees
  // every final terminated state before the DELETED event, so nothing is missed.
  // kubectl --watch -o json emits a stream of pretty-printed JSON documents
  // (not newline-delimited): frame them by brace balance, outside strings.
  const capturedExits = new Map()
  const watchDebug = { docs: 0, bytes: 0, stderr: '' }
  const watcher = start(
    ['-n', namespace, 'get', 'pods', '-l', 'app=api', '--watch', '-o', 'json'],
    3_600_000,
  )
  const collector = (async () => {
    // Scan with a persistent cursor: state (depth/strings) must never be
    // re-applied to bytes already consumed, or brace counts inflate.
    let buffer = ''
    let pos = 0
    let depth = 0
    let inString = false
    let escaped = false
    let start_ = -1
    watcher.child.stdout.on('data', (data) => {
      buffer += data
      watchDebug.bytes += data.length
      for (; pos < buffer.length; pos++) {
        const c = buffer[pos]
        if (start_ < 0) {
          if (c !== '{') continue
          start_ = pos
          depth = 0
          inString = false
          escaped = false
        }
        if (escaped) {
          escaped = false
          continue
        }
        if (c === '\\' && inString) {
          escaped = true
          continue
        }
        if (c === '"') inString = !inString
        if (inString) continue
        if (c === '{') depth++
        else if (c === '}') {
          depth--
          if (depth === 0) {
            const document = buffer.slice(start_, pos + 1)
            start_ = -1
            try {
              const pod = JSON.parse(document)
              watchDebug.docs++
              const terminated =
                pod?.status?.containerStatuses?.[0]?.state?.terminated
              if (terminated && pod.kind === 'Pod')
                capturedExits.set(pod.metadata.name, {
                  exitCode: terminated.exitCode,
                  reason: terminated.reason,
                })
            } catch {
              // skip malformed frames
            }
          }
        }
      }
    })
    await new Promise((resolve) => watcher.child.on('close', resolve))
  })()
  // Continuous in-cluster client traffic during the whole rollout.
  const looper = clientCommand(
    `failures=0; requests=0;
    deadline=$(( $(date +%s) + 240 ));
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
  await rollouter
  await until(
    async () => {
      // Exit 1 while still rolling out is expected; only the final line matters.
      const status = await ns(
        ['rollout', 'status', 'deployment', 'api', '--timeout=5s'],
        20000,
      ).catch((error) => String(error))
      return String(status).trim().endsWith('successfully rolled out')
    },
    'rollout completion',
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
  const after = await json(['deployment', 'api'])
  assert.notEqual(after.metadata.generation, before.metadata.generation)
  const runningPods = (await json(['pods', '-l', 'app=api'])).items.filter(
    (p) => !p.metadata.deletionTimestamp,
  )
  const newPodNames = runningPods.map((p) => p.metadata.name)
  assert.ok(newPodNames.some((name) => !oldPodNames.has(name)))
  assert.equal(newPodNames.length, 2)
  results.rollout = {
    requests: looperOutput.match(/REQUESTS_(\d+)/)?.[1],
    failures: looperOutput.match(/FAILURES_(\d+)/)?.[1],
  }
  results.checks.push('rolling-restart-service-requests-zero-failure')

  console.log('K8S_PHASE graceful-old-pod-shutdown')
  // Final terminated states stream in before the DELETED event; give the
  // already-received events a moment, then stop the watch.
  await delay(3000)
  watcher.child.kill()
  await collector
  results.watch = { ...watchDebug }
  console.log('K8S_WATCH_DEBUG', JSON.stringify(watchDebug))
  console.log(
    'K8S_CAPTURED_EXITS',
    JSON.stringify([...capturedExits.entries()]),
  )
  results.exitCodes = {}
  for (const name of [...oldPodNames]) {
    if (newPodNames.includes(name)) continue
    const captured = capturedExits.get(name)
    // A replaced pod without a captured exit code is missing evidence, not a pass.
    assert.ok(captured, `exit code not captured for replaced pod ${name}`)
    assert.ok(
      [0, 143].includes(captured.exitCode),
      `Pod ${name} exit code ${captured.exitCode} (${captured.reason})`,
    )
    results.exitCodes[name] = captured.exitCode
  }

  console.log('K8S_PHASE restart-preserves-task-session-and-rsa-key')
  // The original session's refresh token (captured before the rollout, held
  // only in this process) must still mint a working access token against the
  // new pod generation: this proves session and RSA key persisted together.
  const persisted = await clientCommand(
    `token=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/refresh -H 'content-type: application/json' -d '{"refreshToken":"${originalRefresh}"}' | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    test -n "$token" && echo "REFRESH_OK" &&
    curl -fsS -H "authorization: Bearer $token" http://api.teaching.svc.cluster.local:8089/tasks | grep -q 'K8s drill' && echo 'TASK_PRESERVED'`,
    120000,
  )
  assert.match(persisted, /REFRESH_OK/)
  assert.match(persisted, /TASK_PRESERVED/)
  results.checks.push('rollout-preserves-task-session-and-rsa-key')

  results.status = 'passed'
  console.log('K8S_RESULT', JSON.stringify(results))
} catch (error) {
  primaryError = error
  results.status = 'failed'
  results.error = error.message
  throw error
} finally {
  const cleanupErrors = []
  try {
    const events = await k(
      ['get', 'events', '-n', namespace, '--sort-by=.lastTimestamp'],
      60000,
    )
    await writeFile(resolve(output, 'events.log'), events)
  } catch (error) {
    cleanupErrors.push(error)
  }
  try {
    const pods = await ns(['get', 'pods', '-o', 'wide'], 60000)
    await writeFile(resolve(output, 'pods.log'), pods)
  } catch (error) {
    cleanupErrors.push(error)
  }
  try {
    for (const resource of ['deployment/api', 'deployment/mysql']) {
      const describe = await ns(['describe', resource], 60000).catch(
        (error) => `describe failed: ${error.message}`,
      )
      await writeFile(
        resolve(output, `${resource.split('/')[1]}-describe.log`),
        describe,
      )
    }
  } catch (error) {
    cleanupErrors.push(error)
  }
  try {
    const logs = await ns(
      ['logs', 'deployment/api', '--all-containers', '--prefix', '--tail=2000'],
      120000,
    ).catch((error) => `logs failed: ${error.message}`)
    await writeFile(resolve(output, 'api-logs.log'), logs)
  } catch (error) {
    cleanupErrors.push(error)
  }
  try {
    await writeFile(
      resolve(output, 'summary.json'),
      JSON.stringify(results, null, 2) + '\n',
    )
  } catch (kindError) {
    cleanupErrors.push(kindError)
  }
  if (cleanupErrors.length && !primaryError)
    throw new AggregateError(cleanupErrors, 'K8s evidence collection failed')
  if (cleanupErrors.length)
    console.error(
      'K8S_CLEANUP_ERRORS',
      cleanupErrors.map((error) => error.message),
    )
  // The cluster and its PVCs are intentionally left for inspection; the
  // script never deletes another user's namespace or cluster.
}
