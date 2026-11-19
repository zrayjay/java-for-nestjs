import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = resolve(root, 'target/delivery-evidence')
const namespace = 'teaching'
const image = 'java-for-nestjs-teaching'
const clusterName = process.env.KIND_CLUSTER_NAME || 'p5-delivery'
const kind = process.env.KIND_BIN || 'kind'
const source = process.env.GITHUB_SHA || 'local'
const markers = { v1: `drill-v1-${source}`, v2: `drill-v2-${source}` }

function run(command, args, { input, timeout = 60_000, label = args[0] } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: root, stdio: 'pipe' })
    let stdout = ''
    let stderr = ''
    const timer = setTimeout(() => {
      child.kill()
      reject(new Error(`${command} ${label} exceeded ${timeout} ms`))
    }, timeout)
    child.stdout.setEncoding('utf8').on('data', (data) => {
      stdout += data
    })
    child.stderr.setEncoding('utf8').on('data', (data) => {
      stderr += data
    })
    child.stdin.on('error', reject)
    child.on('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
    child.on('close', (code) => {
      clearTimeout(timer)
      if (code === 0) resolve(stdout)
      else
        reject(
          new Error(
            `${command} ${label} exited ${code}\n${stderr.slice(-4000)}`,
          ),
        )
    })
    child.stdin.end(input)
  })
}

const kubectl = (args, options) =>
  run('kubectl', ['--context', `kind-${clusterName}`, ...args], {
    label: args.slice(0, 3).join(' '),
    ...options,
  })
const ns = (args, options) =>
  kubectl(['-n', namespace, ...args], {
    label: args.slice(0, 3).join(' '),
    ...options,
  })
const get = async (...resource) =>
  JSON.parse(await ns(['get', ...resource, '-o', 'json']))
const client = (input, options) =>
  ns(['exec', '-i', 'drill-client', '--', 'sh', '-eu'], { input, ...options })
const rollout = (resource) =>
  ns(['rollout', 'status', `deployment/${resource}`, '--timeout=300s'], {
    timeout: 310_000,
  })

async function verifyVersion(tag) {
  const deployment = await get('deployment', 'api')
  const deployedImage = deployment.spec.template.spec.containers.find(
    ({ name }) => name === 'api',
  ).image
  assert.equal(deployedImage, `${image}:${tag}`)
  const { items } = await get('pods', '-l', 'app=api')
  const activePods = items.filter(({ metadata }) => !metadata.deletionTimestamp)
  assert.equal(activePods.length, 1)
  const pod = activePods[0]
  const container = pod.status.containerStatuses.find(
    ({ name }) => name === 'api',
  )
  assert.ok(container.ready)
  assert.equal(
    container.image.replace(/^docker\.io\/library\//, ''),
    deployedImage,
  )
  assert.ok(container.imageID, 'The runtime must report the deployed image ID')
  const marker = (
    await ns([
      'exec',
      pod.metadata.name,
      '-c',
      'api',
      '--',
      'printenv',
      'BUILD_MARKER',
    ])
  ).trim()
  assert.equal(marker, markers[tag])
  return {
    image: deployedImage,
    imageID: container.imageID,
    pod: pod.metadata.name,
    marker,
  }
}

const api = 'http://api.teaching.svc.cluster.local:8089'

async function underTraffic(change) {
  const requests = client(
    `
    requests=0
    failures=0
    while [ ! -f /tmp/probe-stop ]; do
      code=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' ${api}/actuator/health/readiness) || code=error
      requests=$((requests + 1))
      if [ "$code" != 200 ]; then failures=$((failures + 1)); fi
      touch /tmp/probe-started
      sleep 0.3
    done
    printf '%s %s\n' "$requests" "$failures"
  `,
    { timeout: 360_000 },
  )
  // Attach a handler immediately while the rollout is in flight.
  requests.catch(() => {})
  let rolloutError
  try {
    await client(`
      for attempt in $(seq 1 20); do
        if [ -f /tmp/probe-started ]; then exit 0; fi
        sleep 1
      done
      exit 1
    `)
    await change()
    await rollout('api')
  } catch (error) {
    rolloutError = error
    throw error
  } finally {
    await client('touch /tmp/probe-stop\n').catch((error) => {
      if (!rolloutError) throw error
      console.error(error.message)
    })
  }
  const [count, failures] = (await requests).trim().split(' ').map(Number)
  await client('rm /tmp/probe-started /tmp/probe-stop\n')
  assert.ok(Number.isSafeInteger(count) && count > 0)
  return { requests: count, failures }
}

const results = { source, checks: [] }
let namespaceCreated = false
let primaryError
await mkdir(output, { recursive: true })

try {
  const version = JSON.parse(await kubectl(['version', '-o', 'json']))
  results.cluster = version.serverVersion.gitVersion
  const existing = await kubectl([
    'get',
    'namespace',
    namespace,
    '--ignore-not-found',
    '-o',
    'name',
  ])
  assert.equal(
    existing.trim(),
    '',
    'Use a fresh, dedicated kind cluster for this drill',
  )

  console.log('DELIVERY_PHASE prepare-images')
  for (const [tag, marker] of Object.entries(markers)) {
    const ref = `${image}:${tag}`
    if (process.env.DELIVERY_USE_PREBUILT !== '1') {
      await run(
        'docker',
        [
          'build',
          '--build-arg',
          `BUILD_MARKER=${marker}`,
          '-f',
          'project/deploy/compose/Dockerfile',
          '-t',
          ref,
          '.',
        ],
        { timeout: 900_000, label: `build ${ref}` },
      )
    }
    const actual = await run('docker', [
      'run',
      '--rm',
      '--entrypoint',
      'printenv',
      ref,
      'BUILD_MARKER',
    ])
    assert.equal(actual.trim(), marker)
    await run(kind, ['load', 'docker-image', ref, '--name', clusterName], {
      timeout: 300_000,
    })
  }
  results.checks.push('marker-distinct-images-built')

  console.log('DELIVERY_PHASE deploy-v1')
  // The teaching image appears in the keys Job, the init container, and the
  // API. Set all three to v1 before applying so v1 is the initial revision.
  const manifest = await readFile(
    resolve(root, 'project/deploy/k8s/teaching.yaml'),
    'utf8',
  )
  await kubectl(['create', 'namespace', namespace])
  namespaceCreated = true
  await kubectl(['apply', '-f', '-'], {
    input: manifest.replaceAll(`${image}:local`, `${image}:v1`),
  })
  await ns(
    ['wait', '--for=condition=complete', 'job/keys-init', '--timeout=180s'],
    { timeout: 190_000 },
  )
  await rollout('api')
  results.initial = await verifyVersion('v1')

  const clientPod = {
    apiVersion: 'v1',
    kind: 'Pod',
    metadata: { name: 'drill-client' },
    spec: {
      restartPolicy: 'Never',
      activeDeadlineSeconds: 900,
      containers: [
        {
          name: 'client',
          image: `${image}:v1`,
          command: ['sleep', '900'],
        },
      ],
    },
  }
  await ns(['apply', '-f', '-'], { input: JSON.stringify(clientPod) })
  await ns(
    ['wait', '--for=condition=Ready', 'pod/drill-client', '--timeout=60s'],
    { timeout: 70_000 },
  )

  // Refresh credentials stay in the client container's private file and do
  // not appear in Pod manifests, command arguments from the host, or evidence.
  await client(`
    umask 077
    login=$(curl -fsS --max-time 15 -X POST ${api}/auth/login -H 'content-type: application/json' -d '{"username":"compose-admin","password":"disposable-admin-password"}')
    token=$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p')
    refresh=$(printf '%s' "$login" | sed -n 's/.*"refreshToken":"\\([^"]*\\)".*/\\1/p')
    test -n "$token"
    test -n "$refresh"
    printf '%s' "$refresh" > /tmp/drill-refresh
    status=$(curl -fsS --max-time 15 -o /dev/null -w '%{http_code}' -X POST ${api}/tasks -H "authorization: Bearer $token" -H 'content-type: application/json' -d '{"title":"Delivery drill","details":{"priority":1}}')
    test "$status" = 201
  `)
  results.checks.push('v1-deployed-login-and-task-created')

  console.log('DELIVERY_PHASE roll-to-v2')
  const forward = await underTraffic(() =>
    ns(['set', 'image', 'deployment/api', `api=${image}:v2`]),
  )
  results.rollForward = forward
  assert.equal(
    forward.failures,
    0,
    `Readiness request failures during upgrade out of ${forward.requests}`,
  )
  Object.assign(forward, await verifyVersion('v2'))
  assert.notEqual(forward.imageID, results.initial.imageID)
  results.checks.push('v2-rollout-zero-failure-marker-v2')

  console.log('DELIVERY_PHASE rollback-to-v1')
  const backward = await underTraffic(() =>
    ns(['rollout', 'undo', 'deployment/api']),
  )
  results.rollback = backward
  assert.equal(
    backward.failures,
    0,
    `Readiness request failures during rollback out of ${backward.requests}`,
  )
  Object.assign(backward, await verifyVersion('v1'))
  assert.equal(results.rollback.imageID, results.initial.imageID)
  await writeFile(
    resolve(output, 'rollout-history.log'),
    await ns(['rollout', 'history', 'deployment/api']),
  )
  results.checks.push('undo-returns-v1-image-and-marker')

  console.log('DELIVERY_PHASE business-continuity-after-rollback')
  await client(`
    refresh=$(cat /tmp/drill-refresh)
    rm /tmp/drill-refresh
    reply=$(curl -fsS --max-time 15 -X POST ${api}/auth/refresh -H 'content-type: application/json' -d "{\\"refreshToken\\":\\"$refresh\\"}")
    token=$(printf '%s' "$reply" | sed -n 's/.*"accessToken":"\\([^"]*\\)".*/\\1/p')
    test -n "$token"
    tasks=$(curl -fsS --max-time 15 -H "authorization: Bearer $token" ${api}/tasks)
    printf '%s' "$tasks" | grep -q 'Delivery drill'
  `)
  results.checks.push('rollback-preserves-task-and-original-session')
  results.status = 'passed'
  console.log('DELIVERY_RESULT', JSON.stringify(results))
} catch (error) {
  primaryError = error
  results.status = 'failed'
  results.error = error.message
  throw error
} finally {
  const writeErrors = []
  if (namespaceCreated) {
    const diagnostics = [
      ['pods.log', ['get', 'pods', '-o', 'wide']],
      ['events.log', ['get', 'events', '--sort-by=.lastTimestamp']],
      ['api-describe.log', ['describe', 'deployment', 'api']],
    ]
    for (const [filename, args] of diagnostics) {
      const log = await ns(args).catch((error) => error.message)
      await writeFile(resolve(output, filename), log).catch((error) =>
        writeErrors.push(error),
      )
    }
  }
  await writeFile(
    resolve(output, 'summary.json'),
    JSON.stringify(results, null, 2) + '\n',
  ).catch((error) => writeErrors.push(error))
  if (writeErrors.length && !primaryError)
    throw new AggregateError(writeErrors, 'Delivery evidence write failed')
  for (const error of writeErrors) console.error(error.message)
}
