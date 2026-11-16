import { randomUUID } from 'node:crypto'
import { Readable } from 'node:stream'
import { inflateSync } from 'node:zlib'
import { PNG } from 'pngjs'
import {
  DeleteObjectCommand,
  GetBucketPolicyCommand,
  GetObjectCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client,
  S3ServiceException,
} from '@aws-sdk/client-s3'

export const MAX_PNG_BYTES = 1024 * 1024
const crcTable = Uint32Array.from({ length: 256 }, (_, value) => {
  let crc = value
  for (let bit = 0; bit < 8; bit++)
    crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  return crc >>> 0
})
function crc32(bytes: Buffer) {
  let crc = 0xffffffff
  for (const byte of bytes) crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 255]
  return (crc ^ 0xffffffff) >>> 0
}
function validateChunks(input: Buffer, width: number, height: number) {
  if (
    input[24] !== 8 ||
    ![2, 6].includes(input[25]) ||
    input[26] !== 0 ||
    input[27] !== 0 ||
    input[28] !== 0
  )
    throw new Error('Only noninterlaced 8-bit RGB/RGBA PNG is supported')
  let offset = 8,
    headers = 0,
    ended = false,
    dataEnded = false,
    sawData = false
  const compressed: Buffer[] = []
  while (offset < input.length) {
    if (input.length - offset < 12)
      throw new Error('Invalid PNG chunk structure')
    const length = input.readUInt32BE(offset)
    if (length > input.length - offset - 12)
      throw new Error('Invalid PNG chunk length')
    const type = input.toString('ascii', offset + 4, offset + 8)
    const end = offset + 12 + length
    if (
      crc32(input.subarray(offset + 4, end - 4)) !== input.readUInt32BE(end - 4)
    )
      throw new Error('Invalid PNG CRC')
    if (type === 'IHDR') {
      if (++headers !== 1 || offset !== 8 || length !== 13)
        throw new Error('Duplicate or misplaced PNG header')
    } else if (type === 'IDAT') {
      if (dataEnded) throw new Error('Noncontiguous PNG image data')
      sawData = true
      compressed.push(input.subarray(offset + 8, end - 4))
    } else if (type === 'IEND') {
      if (!sawData || length !== 0 || end !== input.length)
        throw new Error('Invalid PNG ending')
      ended = true
    } else if (type === 'tEXt') {
      if (sawData) dataEnded = true
    } else throw new Error('Unsupported PNG chunk')
    offset = end
  }
  if (headers !== 1 || !ended) throw new Error('Incomplete PNG structure')
  const expected = height * (1 + width * (input[25] === 6 ? 4 : 3))
  // Bound zlib output BEFORE invoking the image decoder; reject expansion beyond scanline budget.
  const pixels = inflateSync(Buffer.concat(compressed), {
    maxOutputLength: expected,
  })
  if (pixels.length !== expected) throw new Error('Invalid PNG scanline length')
}
async function boundedBytes(source: Readable) {
  const chunks: Buffer[] = []
  let size = 0
  for await (const chunk of source) {
    const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
    size += bytes.length
    if (size > MAX_PNG_BYTES) {
      source.destroy()
      throw new Error('PNG exceeds size budget')
    }
    chunks.push(bytes)
  }
  return Buffer.concat(chunks)
}
export async function sanitizePng(
  filename: string,
  type: string,
  source: Readable,
) {
  if (
    !/^[A-Za-z0-9][A-Za-z0-9._ -]{0,99}\.png$/.test(filename) ||
    filename.includes('..') ||
    type !== 'image/png'
  )
    throw new Error('Invalid PNG upload')
  const input = await boundedBytes(source)
  if (
    input.length < 33 ||
    !input
      .subarray(0, 8)
      .equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) ||
    input.readUInt32BE(8) !== 13 ||
    input.toString('ascii', 12, 16) !== 'IHDR'
  )
    throw new Error('Invalid PNG header')
  const width = input.readUInt32BE(16),
    height = input.readUInt32BE(20)
  if (
    width < 1 ||
    height < 1 ||
    width > 1024 ||
    height > 1024 ||
    width * height > 1048576
  )
    throw new Error('PNG pixel budget exceeded')
  validateChunks(input, width, height)
  const decoded = PNG.sync.read(input, { checkCRC: true })
  const canonical = new PNG({ width, height })
  canonical.data = Buffer.from(decoded.data)
  const output = PNG.sync.write(canonical)
  if (output.length > MAX_PNG_BYTES)
    throw new Error('Canonical PNG exceeds size budget')
  return output
}
export type ScanStatus = 'clean' | 'infected' | 'not_configured'
export type FileAudit = {
  actor: number
  action: 'file.created' | 'file.read'
  key: string
}
/** Internal service. Actor comes from authenticated server context; there is no public actor JSON field. */
export class PrivateFiles {
  private constructor(
    private readonly client: S3Client,
    private readonly bucket: string,
    private readonly scanner: (png: Buffer) => Promise<ScanStatus>,
    private readonly audit: (entry: FileAudit) => Promise<void>,
  ) {}
  static async create(
    client: S3Client,
    bucket: string,
    scanner: (png: Buffer) => Promise<ScanStatus>,
    audit: (entry: FileAudit) => Promise<void>,
  ) {
    try {
      const response = await client.send(
        new GetBucketPolicyCommand({ Bucket: bucket }),
      )
      if (response.Policy?.trim())
        throw new Error('Bucket policy must be absent')
    } catch (error) {
      if (
        !(error instanceof S3ServiceException) ||
        error.name !== 'NoSuchBucketPolicy'
      )
        throw error
    }
    return new PrivateFiles(client, bucket, scanner, audit)
  }
  private actor(actor: number) {
    if (!Number.isSafeInteger(actor) || actor < 1)
      throw new Error('Authentication required')
  }
  async upload(
    trustedActor: number,
    filename: string,
    type: string,
    source: Readable,
  ) {
    this.actor(trustedActor)
    const canonical = await sanitizePng(filename, type, source)
    const status = await this.scanner(canonical)
    if (status !== 'clean' && status !== 'not_configured')
      throw new Error('Upload rejected')
    const key = `${randomUUID()}.png`
    await this.client.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: key,
        Body: canonical,
        ContentType: 'image/png',
        Metadata: { 'owner-id': String(trustedActor), 'scan-status': status },
      }),
    )
    try {
      await this.audit({ actor: trustedActor, action: 'file.created', key })
    } catch (error) {
      try {
        await this.client.send(
          new DeleteObjectCommand({ Bucket: this.bucket, Key: key }),
        )
      } catch (cleanup) {
        throw new AggregateError(
          [error, cleanup],
          'Audit and compensating delete failed',
        )
      }
      throw error
    }
    return { key, scanStatus: status }
  }
  async download(trustedActor: number, key: string) {
    this.actor(trustedActor)
    if (
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.png$/.test(
        key,
      )
    )
      throw new Error('Invalid object key')
    const metadata = await this.client.send(
      new HeadObjectCommand({ Bucket: this.bucket, Key: key }),
    )
    if (metadata.Metadata?.['owner-id'] !== String(trustedActor))
      throw new Error('Forbidden')
    const object = await this.client.send(
      new GetObjectCommand({ Bucket: this.bucket, Key: key }),
    )
    if (!(object.Body instanceof Readable))
      throw new Error('Missing object body')
    const bytes = await boundedBytes(object.Body)
    await this.audit({ actor: trustedActor, action: 'file.read', key })
    return bytes
  }
}
