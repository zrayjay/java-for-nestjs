import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { Readable } from 'node:stream'
import { deflateSync } from 'node:zlib'
import { after, before, test } from 'node:test'
import { PNG } from 'pngjs'
import {
  CreateBucketCommand,
  DeleteBucketCommand,
  DeleteObjectCommand,
  ListObjectsV2Command,
  PutBucketPolicyCommand,
  S3Client,
} from '@aws-sdk/client-s3'
import {
  MAX_PNG_BYTES,
  PrivateFiles,
  sanitizePng,
  type FileAudit,
} from './files.js'

const endpoint = process.env.FILES_ENDPOINT
if (!endpoint || !/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(endpoint))
  throw new Error(
    'FILES_ENDPOINT must name a disposable loopback object service',
  )
const accessKeyId = process.env.FILES_ACCESS_KEY,
  secretAccessKey = process.env.FILES_SECRET_KEY
if (!accessKeyId || !secretAccessKey)
  throw new Error('Explicit disposable object service credentials required')
const client = new S3Client({
  endpoint,
  forcePathStyle: true,
  region: 'us-east-1',
  credentials: { accessKeyId, secretAccessKey },
})
const bucket = `node-files-${randomUUID()}`
before(async () => {
  await client.send(new CreateBucketCommand({ Bucket: bucket }))
})
after(async () => {
  const objects = await client.send(
    new ListObjectsV2Command({ Bucket: bucket }),
  )
  for (const object of objects.Contents ?? [])
    await client.send(
      new DeleteObjectCommand({ Bucket: bucket, Key: object.Key }),
    )
  await client.send(new DeleteBucketCommand({ Bucket: bucket }))
  client.destroy()
})
function png() {
  const image = new PNG({ width: 2, height: 2 })
  image.data.fill(0)
  image.data.set([255, 0, 0, 255], 0)
  return PNG.sync.write(image)
}
const stream = (bytes: Buffer) => Readable.from([bytes])
function chunk(type: string, data: Buffer) {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  let crc = 0xffffffff
  for (const byte of body) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++)
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  const result = Buffer.alloc(data.length + 12)
  result.writeUInt32BE(data.length, 0)
  body.copy(result, 4)
  result.writeUInt32BE((crc ^ 0xffffffff) >>> 0, result.length - 4)
  return result
}
test('metadata is removed while duplicate headers interlace and excessive inflation are rejected', async () => {
  const valid = png()
  const metadata = Buffer.concat([
    valid.subarray(0, 33),
    chunk('tEXt', Buffer.from('Comment\0UNTRUSTED_SCRIPT_MARKER')),
    valid.subarray(33),
  ])
  const sanitized = await sanitizePng('a.png', 'image/png', stream(metadata))
  assert.deepEqual(
    PNG.sync.read(sanitized).data.subarray(0, 4),
    Buffer.from([255, 0, 0, 255]),
  )
  assert.ok(!sanitized.includes(Buffer.from('UNTRUSTED_SCRIPT_MARKER')))
  const duplicate = Buffer.concat([valid.subarray(0, 33), valid.subarray(8)])
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(duplicate)),
    /Duplicate or misplaced PNG header/,
  )
  const header = Buffer.from(valid.subarray(16, 29))
  header[12] = 1
  const interlaced = Buffer.concat([
    valid.subarray(0, 8),
    chunk('IHDR', header),
    valid.subarray(33),
  ])
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(interlaced)),
    /Only noninterlaced/,
  )
  header[12] = 0
  const expansion = Buffer.concat([
    valid.subarray(0, 8),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.alloc(8 * 1024 * 1024))),
    chunk('IEND', Buffer.alloc(0)),
  ])
  await assert.rejects(sanitizePng('a.png', 'image/png', stream(expansion)), {
    code: 'ERR_BUFFER_TOO_LARGE',
  })
})
test('valid PNG is reencoded with preserved pixels', async () => {
  const result = await sanitizePng('avatar.png', 'image/png', stream(png()))
  const decoded = PNG.sync.read(result)
  assert.equal(decoded.width, 2)
  assert.deepEqual(decoded.data.subarray(0, 4), Buffer.from([255, 0, 0, 255]))
  assert.ok(result.length < MAX_PNG_BYTES)
})
test('path traversal MIME spoof wrong signature byte and pixel bombs are rejected', async () => {
  for (const name of [
    '../secret.png',
    '..\\secret.png',
    '/tmp/a.png',
    'a.png.exe',
    'a\r\nb.png',
  ])
    await assert.rejects(
      sanitizePng(name, 'image/png', stream(png())),
      /Invalid PNG upload/,
    )
  await assert.rejects(
    sanitizePng('a.png', 'text/html', stream(png())),
    /Invalid PNG upload/,
  )
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(Buffer.from('<script>'))),
    /Invalid PNG header/,
  )
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(Buffer.alloc(MAX_PNG_BYTES + 1))),
    /size budget/,
  )
  const bomb = png()
  bomb.writeUInt32BE(2147483647, 16)
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(bomb)),
    /pixel budget/,
  )
  await assert.rejects(
    sanitizePng('a.png', 'image/png', stream(png().subarray(0, 34))),
  )
})
test('private objects require owner and audits contain no filename', async () => {
  const events: FileAudit[] = []
  const files = await PrivateFiles.create(
    client,
    bucket,
    async () => 'not_configured',
    async (event) => {
      events.push(event)
    },
  )
  const receipt = await files.upload(
    1,
    'private-name.png',
    'image/png',
    stream(png()),
  )
  assert.equal(receipt.scanStatus, 'not_configured')
  assert.ok(!receipt.key.includes('private-name'))
  assert.deepEqual(
    await files.download(1, receipt.key),
    await sanitizePng('a.png', 'image/png', stream(png())),
  )
  await assert.rejects(files.download(2, receipt.key), /Forbidden/)
  await assert.rejects(
    files.download(0, receipt.key),
    /Authentication required/,
  )
  await assert.rejects(
    files.download(1, `../${receipt.key}`),
    /Invalid object key/,
  )
  assert.equal(
    (await fetch(`${endpoint}/${bucket}/${receipt.key}`)).status,
    403,
  )
  assert.deepEqual(
    events.map((e) => e.action),
    ['file.created', 'file.read'],
  )
  assert.ok(!JSON.stringify(events).includes('private-name'))
})
test('infected verdict scanner failure and audit failure do not leave objects', async () => {
  const count = async () =>
    (await client.send(new ListObjectsV2Command({ Bucket: bucket }))).KeyCount
  const before = await count()
  const infected = await PrivateFiles.create(
    client,
    bucket,
    async () => 'infected',
    async () => {},
  )
  await assert.rejects(
    infected.upload(1, 'a.png', 'image/png', stream(png())),
    /Upload rejected/,
  )
  const brokenScanner = await PrivateFiles.create(
    client,
    bucket,
    async () => {
      throw new Error('Scanner down')
    },
    async () => {},
  )
  await assert.rejects(
    brokenScanner.upload(1, 'a.png', 'image/png', stream(png())),
    /Scanner down/,
  )
  const brokenAudit = await PrivateFiles.create(
    client,
    bucket,
    async () => 'not_configured',
    async () => {
      throw new Error('Audit down')
    },
  )
  await assert.rejects(
    brokenAudit.upload(1, 'a.png', 'image/png', stream(png())),
    /Audit down/,
  )
  assert.equal(await count(), before)
})
test('public bucket policy is rejected at service construction', async () => {
  const policyBucket = `policy-${randomUUID()}`
  await client.send(new CreateBucketCommand({ Bucket: policyBucket }))
  try {
    await client.send(
      new PutBucketPolicyCommand({
        Bucket: policyBucket,
        Policy: JSON.stringify({
          Version: '2012-10-17',
          Statement: [
            {
              Effect: 'Allow',
              Principal: { AWS: ['*'] },
              Action: ['s3:GetObject'],
              Resource: [`arn:aws:s3:::${policyBucket}/*`],
            },
          ],
        }),
      }),
    )
    await assert.rejects(
      PrivateFiles.create(
        client,
        policyBucket,
        async () => 'not_configured',
        async () => {},
      ),
      /Bucket policy must be absent/,
    )
  } finally {
    await client.send(new DeleteBucketCommand({ Bucket: policyBucket }))
  }
})
