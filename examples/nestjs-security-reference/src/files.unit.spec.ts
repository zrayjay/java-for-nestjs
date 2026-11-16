import assert from 'node:assert/strict'
import { Readable } from 'node:stream'
import { afterEach, mock, test } from 'node:test'
import { PNG } from 'pngjs'
import {
  DeleteObjectCommand,
  GetBucketPolicyCommand,
  GetObjectCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3'
import { PrivateFiles } from './files.js'

const key = '00000000-0000-0000-0000-000000000001.png'
const clients: S3Client[] = []
function client() {
  const value = new S3Client({
    region: 'us-east-1',
    credentials: { accessKeyId: 'unit-only', secretAccessKey: 'unit-only' },
  })
  clients.push(value)
  return value
}
function png() {
  const image = new PNG({ width: 1, height: 1 })
  image.data.set([255, 0, 0, 255])
  return Readable.from([PNG.sync.write(image)])
}
afterEach(() => {
  mock.restoreAll()
  for (const value of clients.splice(0)) value.destroy()
})
test('anonymous and infected uploads never reach storage', async () => {
  const storage = client()
  const calls: unknown[] = []
  mock.method(storage, 'send', async (command: unknown) => {
    calls.push(command)
    if (command instanceof GetBucketPolicyCommand) return { $metadata: {} }
    throw new Error('Unexpected storage call')
  })
  const files = await PrivateFiles.create(
    storage,
    'unit-files',
    async () => 'infected',
    async () => {},
  )
  await assert.rejects(
    files.upload(0, 'a.png', 'image/png', png()),
    /Authentication required/,
  )
  await assert.rejects(
    files.upload(1, 'a.png', 'image/png', png()),
    /Upload rejected/,
  )
  assert.equal(calls.filter((c) => c instanceof PutObjectCommand).length, 0)
})
test('audit and cleanup failures retain both causes and the same random key', async () => {
  const storage = client()
  const auditFailure = new Error('Audit down'),
    cleanupFailure = new Error('Delete down')
  let putKey: string | undefined, deleteKey: string | undefined
  mock.method(storage, 'send', async (command: unknown) => {
    if (command instanceof GetBucketPolicyCommand) return { $metadata: {} }
    if (command instanceof PutObjectCommand) {
      putKey = command.input.Key
      return { $metadata: {} }
    }
    if (command instanceof DeleteObjectCommand) {
      deleteKey = command.input.Key
      throw cleanupFailure
    }
    throw new Error('Unexpected storage call')
  })
  const files = await PrivateFiles.create(
    storage,
    'unit-files',
    async () => 'not_configured',
    async () => {
      throw auditFailure
    },
  )
  await assert.rejects(
    files.upload(1, 'a.png', 'image/png', png()),
    (error) => {
      assert.ok(error instanceof AggregateError)
      assert.deepEqual(error.errors, [auditFailure, cleanupFailure])
      return true
    },
  )
  assert.match(putKey!, /^[0-9a-f-]{36}\.png$/)
  assert.equal(deleteKey, putKey)
})
test('foreign owner never reads the object body', async () => {
  const storage = client()
  let reads = 0
  mock.method(storage, 'send', async (command: unknown) => {
    if (command instanceof GetBucketPolicyCommand) return { $metadata: {} }
    if (command instanceof HeadObjectCommand)
      return { $metadata: {}, Metadata: { 'owner-id': '2' } }
    if (command instanceof GetObjectCommand) reads++
    throw new Error('Unexpected storage call')
  })
  const files = await PrivateFiles.create(
    storage,
    'unit-files',
    async () => 'not_configured',
    async () => {},
  )
  await assert.rejects(files.download(1, key), /Forbidden/)
  assert.equal(reads, 0)
})
test('download audit failure prevents returning bytes', async () => {
  const storage = client()
  const body = png()
  mock.method(storage, 'send', async (command: unknown) => {
    if (command instanceof GetBucketPolicyCommand) return { $metadata: {} }
    if (command instanceof HeadObjectCommand)
      return { $metadata: {}, Metadata: { 'owner-id': '1' } }
    if (command instanceof GetObjectCommand)
      return { $metadata: {}, Body: body }
    throw new Error('Unexpected storage call')
  })
  const files = await PrivateFiles.create(
    storage,
    'unit-files',
    async () => 'not_configured',
    async () => {
      throw new Error('Audit down')
    },
  )
  await assert.rejects(files.download(1, key), /Audit down/)
  assert.equal(body.destroyed, true)
})
