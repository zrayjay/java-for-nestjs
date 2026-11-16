import amqp, { type Channel, type ChannelModel, type GetMessage } from 'amqplib'
import mariadb, { type Pool } from 'mariadb'
import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'

export class PermanentMessageFailure extends Error {}
export interface TaskEvent {
  eventId: string
  kind: 'task.created'
  taskKey: string
}
export function decode(bytes: Buffer): TaskEvent {
  if (bytes.length > 1024)
    throw new PermanentMessageFailure('Event exceeds 1KiB')
  let value: unknown
  try {
    value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
  } catch {
    throw new PermanentMessageFailure('Invalid event JSON')
  }
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new PermanentMessageFailure('Invalid event fields')
  const fields = Object.keys(value)
  if (
    fields.length !== 3 ||
    fields.some((key) => !['eventId', 'kind', 'taskKey'].includes(key))
  )
    throw new PermanentMessageFailure('Invalid event fields')
  if (
    !('eventId' in value) ||
    typeof value.eventId !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
      value.eventId,
    ) ||
    !('kind' in value) ||
    value.kind !== 'task.created' ||
    !('taskKey' in value) ||
    typeof value.taskKey !== 'string' ||
    !/^[a-z][a-z0-9-]{0,39}$/.test(value.taskKey)
  )
    throw new PermanentMessageFailure('Invalid event')
  return { eventId: value.eventId, kind: value.kind, taskKey: value.taskKey }
}
export function localUrl(
  value: string | undefined,
  scheme: 'mysql:' | 'amqp:',
): URL {
  if (!value) throw new Error('Explicit local message_lab URL required')
  const url = new URL(value)
  if (
    url.protocol !== scheme ||
    !['127.0.0.1', 'localhost'].includes(url.hostname) ||
    url.pathname !== '/message_lab' ||
    url.hash ||
    !url.username ||
    !url.password ||
    (url.search !== '' &&
      !(scheme === 'amqp:' && url.search === '?heartbeat=5'))
  )
    throw new Error('Only the disposable local message_lab target is allowed')
  return url
}
export class MessageStore {
  private readonly pool: Pool
  constructor(value: string | undefined) {
    const url = localUrl(value, 'mysql:')
    this.pool = mariadb.createPool({
      host: url.hostname,
      port: Number(url.port || 3306),
      user: decodeURIComponent(url.username),
      password: decodeURIComponent(url.password),
      database: 'message_lab',
      connectionLimit: 4,
      connectTimeout: 3000,
      acquireTimeout: 3000,
    })
  }
  async apply(
    event: TaskEvent,
    beforeCommit: () => void | Promise<void>,
  ): Promise<'APPLIED' | 'DUPLICATE'> {
    const connection = await this.pool.getConnection()
    try {
      await connection.beginTransaction()
      const inserted = await connection.query(
        "INSERT IGNORE INTO processed_events(consumer_name,event_id,task_key) VALUES ('projection-v1',?,?)",
        [event.eventId, event.taskKey],
      )
      if (inserted.affectedRows === 0) {
        const rows = await connection.query(
          "SELECT task_key FROM processed_events WHERE consumer_name='projection-v1' AND event_id=? FOR UPDATE",
          [event.eventId],
        )
        if (rows[0]?.task_key !== event.taskKey)
          throw new PermanentMessageFailure(
            'Event identity conflicts with stored payload',
          )
        await connection.commit()
        return 'DUPLICATE'
      }
      await connection.query(
        'INSERT INTO projection_effects(task_key,applied_count) VALUES (?,1) ON DUPLICATE KEY UPDATE applied_count=applied_count+1',
        [event.taskKey],
      )
      await beforeCommit()
      await connection.commit()
      return 'APPLIED'
    } catch (error) {
      await connection.rollback()
      throw error
    } finally {
      connection.release()
    }
  }
  async effects(key: string): Promise<number> {
    const rows = await this.pool.query(
      'SELECT COALESCE(SUM(applied_count),0) AS count FROM projection_effects WHERE task_key=?',
      [key],
    )
    return Number(rows[0].count)
  }
  async markers(): Promise<number> {
    const rows = await this.pool.query(
      'SELECT COUNT(*) AS count FROM processed_events',
    )
    return Number(rows[0].count)
  }
  async resetFixture(): Promise<void> {
    await this.pool.query('DELETE FROM processed_events')
    await this.pool.query('DELETE FROM projection_effects')
  }
  close(): Promise<void> {
    return this.pool.end()
  }
}
export interface DeliveryResult {
  outcome: 'APPLIED' | 'DUPLICATE' | 'RETRY' | 'DEAD' | 'COMMITTED_UNACKED'
  attempt: number
  redelivered: boolean
  eventId?: string
}
export class BrokerLab {
  private readonly prefix = `teaching.${randomUUID()}`
  private readonly work = `${this.prefix}.work`
  private readonly dead = `${this.prefix}.dead`
  private readonly dlx = `${this.prefix}.dlx`
  private constructor(private readonly connection: ChannelModel) {}
  static async open(
    value: string | undefined,
    connect: (url: string) => Promise<ChannelModel> = (url) =>
      amqp.connect(url, { timeout: 3000 }),
  ): Promise<BrokerLab> {
    const url = localUrl(value, 'amqp:')
    url.searchParams.set('heartbeat', '5')
    const connection = await connect(url.toString())
    // Prevent an unhandled EventEmitter error; operations still reject and tests fail.
    connection.on('error', () => {})
    const lab = new BrokerLab(connection)
    let channel: Channel | undefined
    try {
      channel = await connection.createChannel()
      await channel.assertExchange(lab.dlx, 'direct', { durable: true })
      await channel.assertQueue(lab.dead, { durable: true })
      await channel.bindQueue(lab.dead, lab.dlx, 'failed')
      await channel.assertQueue(lab.work, {
        durable: true,
        arguments: {
          'x-dead-letter-exchange': lab.dlx,
          'x-dead-letter-routing-key': 'failed',
        },
      })
    } catch (error) {
      await connection.close()
      throw error
    } finally {
      await channel?.close().catch(() => {})
    }
    return lab
  }
  async consumer(): Promise<Channel> {
    const channel = await this.connection.createChannel()
    channel.on('error', () => {})
    return channel
  }
  private async publish(
    bytes: Buffer,
    attempt: unknown,
    routing: string,
  ): Promise<{ confirmed: boolean; returned: boolean }> {
    const channel = await this.connection.createConfirmChannel()
    channel.on('error', () => {})
    let returned = false
    channel.on('return', () => {
      returned = true
    })
    try {
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(
          () => reject(new Error('Publisher confirm deadline exceeded')),
          5000,
        )
        try {
          channel.publish(
            '',
            routing,
            bytes,
            {
              mandatory: true,
              persistent: true,
              contentType: 'application/json',
              contentEncoding: 'utf-8',
              messageId: randomUUID(),
              headers: { attempt },
            },
            (error: Error | null) => {
              clearTimeout(timer)
              if (error) reject(error)
              else resolve()
            },
          )
        } catch (error) {
          clearTimeout(timer)
          reject(error)
        }
      })
      return { confirmed: true, returned }
    } finally {
      await channel.close()
    }
  }
  async send(event: TaskEvent): Promise<void> {
    await this.sendRaw(Buffer.from(JSON.stringify(event)), 0)
  }
  async sendRaw(bytes: Buffer, attempt: unknown): Promise<void> {
    const result = await this.publish(bytes, attempt, this.work)
    if (result.returned) throw new Error('Message confirmed but unroutable')
  }
  probeUnroutable(
    event: TaskEvent,
  ): Promise<{ confirmed: boolean; returned: boolean }> {
    return this.publish(
      Buffer.from(JSON.stringify(event)),
      0,
      `${this.prefix}.missing`,
    )
  }
  private async get(
    channel: Channel,
    queue: string,
    noAck: boolean,
  ): Promise<GetMessage> {
    const deadline = Date.now() + 5000
    while (Date.now() < deadline) {
      const message = await channel.get(queue, { noAck })
      if (message) return message
      await delay(20)
    }
    throw new Error('No broker delivery within deadline')
  }
  async consume(
    channel: Channel,
    store: MessageStore,
    beforeCommit: () => void | Promise<void>,
    closeAfterCommit = false,
  ): Promise<DeliveryResult> {
    const message = await this.get(channel, this.work, false)
    const observed = (
      outcome: DeliveryResult['outcome'],
      attempt: number,
      eventId?: string,
    ): DeliveryResult => {
      const result = {
        outcome,
        attempt,
        redelivered: message.fields.redelivered,
        eventId,
      }
      console.log('MESSAGE_RESULT', JSON.stringify(result))
      return result
    }
    let attempt: number
    let event: TaskEvent
    try {
      const header: unknown = message.properties.headers?.attempt ?? 0
      if (
        typeof header !== 'number' ||
        !Number.isInteger(header) ||
        header < 0 ||
        header > 2
      )
        throw new PermanentMessageFailure('Invalid retry attempt header')
      attempt = header
      event = decode(message.content)
    } catch (error) {
      if (!(error instanceof PermanentMessageFailure)) throw error
      channel.reject(message, false)
      return observed('DEAD', 0)
    }
    let outcome: 'APPLIED' | 'DUPLICATE'
    try {
      outcome = await store.apply(event, beforeCommit)
    } catch (error) {
      if (error instanceof PermanentMessageFailure || attempt >= 2) {
        channel.reject(message, false)
        return observed('DEAD', attempt, event.eventId)
      }
      await this.sendRaw(message.content, attempt + 1)
      channel.ack(message)
      return observed('RETRY', attempt, event.eventId)
    }
    if (closeAfterCommit) {
      await channel.close()
      return observed('COMMITTED_UNACKED', attempt, event.eventId)
    }
    channel.ack(message)
    return observed(outcome, attempt, event.eventId)
  }
  async readDead(): Promise<GetMessage> {
    const channel = await this.consumer()
    try {
      return await this.get(channel, this.dead, true)
    } finally {
      await channel.close()
    }
  }
  async close(): Promise<void> {
    try {
      const channel = await this.consumer()
      await channel.deleteQueue(this.work)
      await channel.deleteQueue(this.dead)
      await channel.deleteExchange(this.dlx)
    } finally {
      await this.connection.close()
    }
  }
}
