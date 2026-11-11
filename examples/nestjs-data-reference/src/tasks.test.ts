import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createApi } from '@java-for-nestjs/nestjs-reference/api'
import {
  createDataClient,
  dataConnection,
} from '@java-for-nestjs/prisma-reference/data-client'
import { PrismaTasks } from './tasks.js'

test('data URL rejects overrides, production databases and remote hosts before connecting', () => {
  assert.equal(
    dataConnection('mysql://lab:pass@127.0.0.1:3306/data_lab').database,
    'data_lab',
  )
  for (const url of [
    undefined,
    'mysql://lab:pass@remote:3306/data_lab',
    'mysql://lab:pass@127.0.0.1:3306/production',
    'mysql://lab:pass@127.0.0.1:3306/data_lab?dbname=production',
    'mysql://lab:pass@127.0.0.1:3306/data_lab#other',
  ])
    assert.throws(() => dataConnection(url))
})

test('Nest service and Prisma persist the same task HTTP contract in real MySQL', async () => {
  const client = createDataClient(process.env.DATA_DATABASE_URL)
  let app: Awaited<ReturnType<typeof createApi>> | undefined
  try {
    await client.dataTask.deleteMany()
    app = await createApi(0, new PrismaTasks(client))
    const base = await app.getUrl()
    const send = (path: string, method: string, body?: string) =>
      fetch(base + path, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body,
      })
    const response = await send(
      '/tasks',
      'POST',
      '{"title":"Learn Java","details":{"priority":2}}',
    )
    assert.equal(response.status, 201)
    const task = (await response.json()) as { id: number }
    const path = `/tasks/${task.id}`
    assert.equal(response.headers.get('location'), path)
    assert.equal(
      (await client.dataTask.findUniqueOrThrow({ where: { id: task.id } }))
        .title,
      'Learn Java',
    )
    const updated = await send(
      path,
      'PUT',
      '{"title":"Learn SQL","details":{"priority":5}}',
    )
    assert.equal(updated.status, 200)
    assert.equal(
      (await client.dataTask.findUniqueOrThrow({ where: { id: task.id } }))
        .priority,
      5,
    )
    assert.equal((await send(path, 'DELETE')).status, 204)
    assert.equal(await client.dataTask.count({ where: { id: task.id } }), 0)
    const missing = await fetch(base + path)
    assert.equal(missing.status, 404)
    assert.deepEqual(await missing.json(), { code: 'not_found' })
  } finally {
    await app?.close()
    await client.$disconnect()
  }
})

test('Prisma bound filters, offset pagination and batching avoid sequential N+1 reads', async () => {
  const client = createDataClient(process.env.DATA_DATABASE_URL)
  try {
    await client.dataTask.deleteMany()
    await client.dataTask.createMany({
      data: [
        { title: 'Alpha', priority: 1, ownerId: 1 },
        { title: 'Beta', priority: 2, ownerId: 2 },
        { title: 'Gamma', priority: 3, ownerId: 3 },
      ],
    })
    assert.equal(
      (await client.dataTask.findMany({ where: { title: "x' OR 1=1 --" } }))
        .length,
      0,
    )
    assert.equal(
      (
        await client.dataTask.findMany({
          orderBy: { id: 'asc' },
          skip: 2,
          take: 2,
        })
      )[0].title,
      'Gamma',
    )
    const statements: string[] = []
    client.$on('query', (event) => {
      if (/^SELECT\b/i.test(event.query.trim())) statements.push(event.query)
    })
    const tasks = await client.dataTask.findMany({ orderBy: { id: 'asc' } })
    const names: string[] = []
    for (const task of tasks)
      names.push(
        (
          await client.dataOwner.findUniqueOrThrow({
            where: { id: task.ownerId },
          })
        ).name,
      )
    assert.deepEqual(names, ['Alice', 'Bob', 'Cara'])
    assert.equal(statements.length, 4)
    statements.length = 0
    const batchedTasks = await client.dataTask.findMany({
      orderBy: { id: 'asc' },
    })
    const owners = await client.dataOwner.findMany({
      where: { id: { in: batchedTasks.map((task) => task.ownerId) } },
    })
    assert.deepEqual(
      batchedTasks.map(
        (task) => owners.find((owner) => owner.id === task.ownerId)!.name,
      ),
      names,
    )
    assert.equal(statements.length, 2)
  } finally {
    await client.$disconnect()
  }
})
