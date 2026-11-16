import { Readable } from 'node:stream'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { PNG } from 'pngjs'
import { PutObjectCommand, S3Client } from '@aws-sdk/client-s3'
import { PrivateFiles } from './files.js'

let client: S3Client
beforeEach(() => {
  client = new S3Client({
    region: 'us-east-1',
    credentials: { accessKeyId: 'unit-only', secretAccessKey: 'unit-only' },
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  client.destroy()
})
function png() {
  const image = new PNG({ width: 1, height: 1 })
  image.data.set([255, 0, 0, 255])
  return PNG.sync.write(image)
}
test('scanner receives canonical pixels and audit receives only structured identifiers', async () => {
  const send = vi
    .spyOn(client, 'send')
    .mockImplementation(async () => ({ $metadata: {} }))
  const input = png()
  const scanner = vi.fn(async (canonical: Buffer) => {
    expect(canonical).not.toBe(input)
    expect(PNG.sync.read(canonical).data).toEqual(Buffer.from([255, 0, 0, 255]))
    return 'not_configured' as const
  })
  const audit = vi.fn(async () => {})
  const files = await PrivateFiles.create(client, 'unit-files', scanner, audit)
  const receipt = await files.upload(
    1,
    'private-name.png',
    'image/png',
    Readable.from([input]),
  )
  expect(receipt.scanStatus).toBe('not_configured')
  expect(scanner).toHaveBeenCalledTimes(1)
  expect(audit).toHaveBeenCalledExactlyOnceWith({
    actor: 1,
    action: 'file.created',
    key: receipt.key,
  })
  expect(send).toHaveBeenCalledTimes(2)
  expect(send.mock.calls[1][0]).toBeInstanceOf(PutObjectCommand)
})
test('scanner exception does not call storage write or audit', async () => {
  const send = vi
    .spyOn(client, 'send')
    .mockImplementation(async () => ({ $metadata: {} }))
  const scanner = vi.fn(async () => {
    throw new Error('Scanner down')
  })
  const audit = vi.fn(async () => {})
  const files = await PrivateFiles.create(client, 'unit-files', scanner, audit)
  await expect(
    files.upload(1, 'a.png', 'image/png', Readable.from([png()])),
  ).rejects.toThrow('Scanner down')
  expect(scanner).toHaveBeenCalledTimes(1)
  expect(send).toHaveBeenCalledTimes(1) // Constructor bucket-policy check only.
  expect(audit).not.toHaveBeenCalled()
})
