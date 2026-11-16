import { randomUUID } from 'node:crypto'
import { Readable } from 'node:stream'
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
