import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  createOrderReference,
  type OrderFixtureActor,
} from './order-reference.js'

const actors: ReadonlyMap<string, OrderFixtureActor> = new Map([
  [
    'clerk',
    {
      id: '10',
      department: '2',
      grants: {
        READ: 'OWN',
        CREATE: 'OWN',
        EDIT: 'OWN',
        SUBMIT: 'OWN',
        APPROVE: 'ALL',
        CANCEL: 'OWN',
      },
    },
  ],
  [
    'reviewer',
    {
      id: '11',
      department: '2',
      grants: {
        READ: 'DEPARTMENT',
        APPROVE: 'DEPARTMENT',
        CANCEL: 'DEPARTMENT',
      },
    },
  ],
  [
    'outside',
    {
      id: '12',
      department: '3',
      grants: { READ: 'DEPARTMENT', APPROVE: 'DEPARTMENT' },
    },
  ],
  [
    'reader',
    {
      id: '13',
      department: '2',
      grants: { READ: 'DEPARTMENT', CANCEL: 'OWN' },
    },
  ],
  ['none', { id: '14', department: '2', grants: {} }],
])

test('Nest HTTP order contract checks action scope, transition precedence and history', async () => {
  const app = await createOrderReference(actors)
  try {
    const send = orderRequests(await app.getUrl())
    await expectError(await send('', 'GET'), 401, 'UNAUTHENTICATED')
    await expectError(await send('', 'GET', 'none'), 403, 'FORBIDDEN')
    const customerName = '📦'.repeat(100)
    await expectError(
      await send('', 'POST', 'clerk', {
        customerName: customerName + '📦',
        totalAmountCents: 950,
      }),
      400,
      'VALIDATION_ERROR',
    )
    const created = await send('', 'POST', 'clerk', {
      customerName: '\u202f' + customerName + '\u202f',
      totalAmountCents: 950,
    })
    assert.equal(created.status, 201)
    const draft = await created.json()
    const route = '/' + draft.id
    assert.equal(draft.status, 'DRAFT')
    assert.equal(draft.version, 1)
    assert.equal(draft.ownerId, '10')
    assert.equal(draft.customerName, customerName)
    assert.deepEqual(draft.allowedActions, ['EDIT', 'SUBMIT', 'CANCEL'])
    await expectError(await send(route, 'GET', 'outside'), 404, 'NOT_FOUND')
    await expectError(
      await send(route + '/cancel', 'POST', 'reviewer', { version: 9 }),
      404,
      'NOT_FOUND',
    )
    const submitted = await send(route + '/submit', 'POST', 'clerk', {
      version: 1,
    })
    assert.equal(submitted.status, 200)
    assert.equal((await submitted.json()).status, 'SUBMITTED')
    const visible = await send(route, 'GET', 'reader')
    assert.equal(visible.status, 200)
    assert.deepEqual((await visible.json()).allowedActions, [])
    await expectError(
      await send(route + '/cancel', 'POST', 'reader', { version: 2 }),
      404,
      'NOT_FOUND',
    )
    await expectError(
      await send(route + '/approve', 'POST', 'clerk', { version: 2 }),
      403,
      'SELF_APPROVAL_FORBIDDEN',
    )
    await expectError(
      await send(route + '/submit', 'POST', 'clerk', { version: 1 }),
      409,
      'VERSION_CONFLICT',
    )
    const approved = await send(route + '/approve', 'POST', 'reviewer', {
      version: 2,
    })
    assert.equal(approved.status, 200)
    const result = await approved.json()
    assert.equal(result.status, 'APPROVED')
    assert.equal(result.version, 3)
    await expectError(
      await send(route + '/approve', 'POST', 'reviewer', { version: 3 }),
      409,
      'INVALID_TRANSITION',
    )
    const history = await (
      await send(route + '/history', 'GET', 'reviewer')
    ).json()
    assert.deepEqual(
      history.map((entry: { action: string }) => entry.action),
      ['CREATE', 'SUBMIT', 'APPROVE'],
    )
    assert.equal(
      (await (await send('?status=APPROVED', 'GET', 'clerk')).json()).total,
      1,
    )
    await expectError(
      await send(route + '/history', 'GET', 'outside'),
      404,
      'NOT_FOUND',
    )
  } finally {
    await app.close()
  }
})

test('Nest HTTP rolls back a SQLite order update when its history append fails', async () => {
  let injectFailure = false
  const app = await createOrderReference(actors, () => {
    if (injectFailure) throw new Error('fixture aborts history append')
  })
  try {
    const send = orderRequests(await app.getUrl())
    const opened = await send('', 'POST', 'clerk', {
      customerName: 'Rollback',
      totalAmountCents: 700,
    })
    assert.equal(opened.status, 201)
    const { id } = await opened.json()
    injectFailure = true
    await expectError(
      await send('/' + id + '/submit', 'POST', 'clerk', { version: 1 }),
      500,
      'INTERNAL_ERROR',
    )
    const draft = await (await send('/' + id, 'GET', 'clerk')).json()
    assert.equal(draft.status, 'DRAFT')
    assert.equal(draft.version, 1)
    const history = await (
      await send('/' + id + '/history', 'GET', 'clerk')
    ).json()
    assert.equal(history.length, 1)
    injectFailure = false
    assert.equal(
      (await send('/' + id + '/submit', 'POST', 'clerk', { version: 1 }))
        .status,
      200,
    )
  } finally {
    await app.close()
  }
})

function orderRequests(base: string) {
  return (route: string, method: string, bearer?: string, body?: object) => {
    const headers = new Headers()
    if (bearer) headers.set('Authorization', 'Bearer ' + bearer)
    if (body) headers.set('Content-Type', 'application/json')
    return fetch(base + '/api/v1/orders' + route, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  }
}

async function expectError(response: Response, status: number, code: string) {
  assert.equal(response.status, status)
  const result = await response.json()
  assert.equal(result.code, code)
  assert.ok(result.requestId)
  assert.equal(result.requestId, response.headers.get('X-Lab-Request-Id'))
}
