import { PrismaMariaDb } from '@prisma/adapter-mariadb'
import { Prisma, PrismaClient } from './generated/prisma/client.js'
import { labConnection } from './connection.js'

const adapter = new PrismaMariaDb({
  ...labConnection(process.env.TX_DATABASE_URL),
  connectionLimit: 4,
  acquireTimeout: 5000,
  connectTimeout: 5000,
})
export const client = new PrismaClient({
  adapter,
  transactionOptions: {
    isolationLevel: Prisma.TransactionIsolationLevel.ReadCommitted,
    maxWait: 5000,
    timeout: 10000,
  },
})
