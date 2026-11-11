import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createApi, TaskRepository } from './api.js'
import { RequestTrace } from './trace.js'
import { isPriorityToken } from './input-boundary.js'
import { LifecycleProbe, readConfiguration } from './configuration.js'

test('real HTTP CRUD, strict nested validation, groups and OpenAPI', async () => {
  const app = await createApi()
  const lifecycle = app.get(LifecycleProbe)
  assert.deepEqual(lifecycle.events, ['construct', 'init', 'ready'])
  try {
    const base = await app.getUrl()
    const traced = await fetch(base + '/tasks')
    assert.deepEqual(
      await app.get(RequestTrace).take(traced.headers.get('x-lab-request-id')!),
      [
        'middleware.before',
        'guard',
        'interceptor.before',
        'controller',
        'interceptor.finalize',
        'middleware.finish',
      ],
    )
    const send = (path: string, body: string, method = 'POST') =>
      fetch(base + path, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body,
      })
    const created = await send(
      '/tasks',
      '{"title":"Learn Java","details":{"priority":1.0}}',
    )
    assert.equal(created.status, 201)
    const task = (await created.json()) as { id: number }
    const path = `/tasks/${task.id}`
    assert.equal(created.headers.get('location'), path)
    assert.equal((await fetch(base + path)).status, 200)
    const updated = await send(
      path,
      '{"title":"Learn Spring","details":{"priority":3}}',
      'PUT',
    )
    assert.equal(updated.status, 200)
    assert.equal(
      ((await updated.json()) as { title: string }).title,
      'Learn Spring',
    )
    assert.equal((await fetch(base + path, { method: 'DELETE' })).status, 204)
    const missing = await fetch(base + path)
    assert.equal(missing.status, 404)
    assert.deepEqual(await missing.json(), { code: 'not_found' })
    for (const body of [
      '{}',
      '{"title":"Learn","details":{"priority":1.0000000000000001}}',
      '{"title":"Learn","details":{"priority":0.99999999999999999}}',
      '{"title":"Learn","details":{"priority":1},"constructor":{}}',
      '{"title":"Learn","details":{"priority":1},"__proto__":{}}',
      '{"title":"Learn\\n","details":{"priority":1}}',
      '{',
      '{"title":17,"details":{"priority":1}}',
      '{"title":"Learn","details":null}',
      '{"title":"Learn","details":{"priority":"1"}}',
      '{"title":"Learn","details":{"priority":1.5}}',
      '{"title":"Learn","details":{"priority":0}}',
      '{"title":"Learn","details":{"priority":1,"integral":true}}',
      '{"title":"Learn","details":{"priority":1},"extra":true}',
    ]) {
      const result = await send('/tasks', body)
      assert.equal(result.status, 400, body)
      assert.deepEqual(await result.json(), { code: 'invalid_request' })
    }
    for (const invalid of [
      '/tasks?limit=',
      '/tasks/0',
      '/tasks/01',
      '/tasks/2147483648',
      '/tasks/abc',
      '/tasks?limit=0',
      '/tasks?limit=101',
      '/tasks?limit=1e1',
      '/tasks?limit=abc',
    ]) {
      assert.equal((await fetch(base + invalid)).status, 400, invalid)
    }
    assert.equal((await send('/validation/draft', '{"title":""}')).status, 204)
    assert.equal(
      (await send('/validation/publish', '{"title":""}')).status,
      400,
    )
    assert.equal(
      (await send('/validation/publish', '{"title":"Learn"}')).status,
      204,
    )
    const schema = (await (await fetch(base + '/openapi.json')).json()) as {
      paths: Record<string, unknown>
    }
    assert.ok(schema.paths['/tasks'])
  } finally {
    await app.close()
  }
  assert.deepEqual(lifecycle.events, ['construct', 'init', 'ready', 'destroy'])
})

test('typed configuration rejects blank values before serving requests', () => {
  assert.equal(readConfiguration({}).label, 'lesson')
  assert.equal(
    readConfiguration({ LAB_LABEL: 'test-profile' }).label,
    'test-profile',
  )
  assert.throws(() => readConfiguration({ LAB_LABEL: ' ' }), /nonblank/)
})

test('unexpected repository failure returns sanitized 500 over real HTTP', async () => {
  const app = await createApi()
  try {
    const repository = app.get(TaskRepository)
    repository.create = () => {
      throw new Error('private-database-detail')
    }
    const response = await fetch((await app.getUrl()) + '/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"title":"Crash","details":{"priority":1}}',
    })
    assert.equal(response.status, 500)
    assert.deepEqual(await response.json(), { code: 'internal_error' })
  } finally {
    await app.close()
  }
})

test('original JSON numeric tokens retain exact decimal integer semantics', () => {
  for (const value of [
    '1',
    '1.0',
    '1e0',
    '10e-1',
    '0.100e1',
    '5.000',
    '500e-2',
  ])
    assert.equal(isPriorityToken(value), true, value)
  for (const value of [
    '0',
    '-1',
    '6',
    '1.0000000000000001',
    '0.99999999999999999',
    '1e999999',
    '1e-999999',
    '1.5',
    'NaN',
    'Infinity',
  ])
    assert.equal(isPriorityToken(value), false, value)
})
