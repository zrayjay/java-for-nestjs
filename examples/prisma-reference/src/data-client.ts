import { PrismaMariaDb } from '@prisma/adapter-mariadb'
import { PrismaClient } from './generated/prisma/client.js'

export function dataConnection(value: string | undefined) {
  if (!value) throw new Error('DATA_DATABASE_URL is required')
  const url = new URL(value)
  const port = Number(url.port || '3306')
  if (
    url.protocol !== 'mysql:' ||
    !['127.0.0.1', 'localhost'].includes(url.hostname) ||
    url.pathname !== '/data_lab' ||
    url.search ||
    url.hash ||
    !url.username ||
    !url.password ||
    !Number.isInteger(port) ||
    port < 1 ||
    port > 65535
  ) {
    throw new Error(
      'Only the dedicated local data_lab URL without query parameters is allowed',
    )
  }
  return {
    host: url.hostname,
    port,
    user: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
    database: 'data_lab',
    connectionLimit: 2,
  }
}
export function createDataClient(value: string | undefined) {
  return new PrismaClient({
    adapter: new PrismaMariaDb(dataConnection(value)),
    log: [{ emit: 'event', level: 'query' }],
  })
}
