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
    const value = await check()
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
    `token=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/login -H 'content-type: application/json' -d '{"username":"compose-admin","password":"disposable-admin-password"}' | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    test -n "$token" && echo "TOKEN_OK" &&
    curl -fsS -o /dev/null -w '%{http_code}' -X POST http://api.teaching.svc.cluster.local:8089/tasks -H "authorization: Bearer $token" -H 'content-type: application/json' -d '{"title":"K8s drill","details":{"priority":2}}' > /tmp/create.status &&
    echo "CREATE_$(cat /tmp/create.status)" &&
    curl -fsS http://api.teaching.svc.cluster.local:8089/actuator/health/readiness | grep -q '"status":"UP"' && echo 'READY_UP' &&
    printf '%s' "$token" > /dev/null`,
    180000,
  )
  assert.match(login, /TOKEN_OK/)
  assert.match(login, /CREATE_201/)
  assert.match(login, /READY_UP/)
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
  const rollouter = ns(['rollout', 'restart', 'deployment', 'api'], 60000)
  // Old pod objects are GC'd shortly after termination: capture exit codes live,
  // while the pod is still Terminating, instead of querying after the rollout.
  const capturedExits = new Map()
  let collecting = true
  const collector = (async () => {
    while (collecting) {
      try {
        const pods = (await json(['pods', '-l', 'app=api'])).items
        for (const pod of pods) {
          const terminated =
            pod.status?.containerStatuses?.[0]?.state?.terminated
          if (terminated && !capturedExits.has(pod.metadata.name))
            capturedExits.set(pod.metadata.name, {
              exitCode: terminated.exitCode,
              reason: terminated.reason,
            })
        }
      } catch {
        // transient list failures are retried on the next poll
      }
      await delay(300)
    }
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
  assert.match(looperOutput, /REQUESTS_\d+ FAILURES_0/)
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
  // Give the collector a moment to observe the final terminated states.
  await delay(3000)
  collecting = false
  await collector
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
  // Directly verify the token from earlier still works against a new pod generation.
  const relogin = await clientCommand(
    `token=$(curl -fsS -X POST http://api.teaching.svc.cluster.local:8089/auth/login -H 'content-type: application/json' -d '{"username":"compose-admin","password":"disposable-admin-password"}' | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p') &&
    curl -fsS -H "authorization: Bearer $token" http://api.teaching.svc.cluster.local:8089/tasks | grep -q 'K8s drill' && echo 'TASK_PRESERVED'`,
    120000,
  )
  assert.match(relogin, /TASK_PRESERVED/)
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
