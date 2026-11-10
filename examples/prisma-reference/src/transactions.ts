import { client } from './client.js'
import { Prisma } from './generated/prisma/client.js'

export class Failure extends Error {}
type Tx = Prisma.TransactionClient
const insert = (tx: Tx, label: string) => tx.entry.create({ data: { label } })

export const commit = () => client.$transaction((tx) => insert(tx, 'commit'))

export const callbackRollback = () =>
  client.$transaction(async (tx) => {
    await insert(tx, 'runtime')
    throw new Failure('runtime-failure')
  })

// Deliberate negative example: the root client is not the transaction client.
export const wrongClient = () =>
  client.$transaction(async () => {
    await client.entry.create({ data: { label: 'outside' } })
    throw new Failure('outer-failure')
  })

async function helperFailure(tx: Tx): Promise<void> {
  await insert(tx, 'inner-required')
  throw new Failure('inner-failure')
}

export const catchApplicationError = () =>
  client.$transaction(async (tx) => {
    try {
      await helperFailure(tx)
    } catch (error) {
      if (!(error instanceof Failure)) throw error
    }
    await insert(tx, 'outer-required')
  })

// A separately opened transaction, not a Prisma propagation/nested-transaction feature.
export const independentCommit = () =>
  client.$transaction(async (outer) => {
    await insert(outer, 'outer-independent')
    await client.$transaction((inner) => insert(inner, 'inner-independent'))
    throw new Failure('outer-failure')
  })

export const readTwice = (isolationLevel: 'ReadCommitted' | 'RepeatableRead') =>
  client.$transaction(
    async (reader) => {
      const first = await reader.balance.findUniqueOrThrow({ where: { id: 1 } })
      const readerIds = await reader.$queryRaw<
        { connection_id: string }[]
      >`SELECT CAST(CONNECTION_ID() AS CHAR) AS connection_id`
      const writerId = await client.$transaction(async (writer) => {
        await writer.balance.update({ where: { id: 1 }, data: { amount: 200 } })
        const ids = await writer.$queryRaw<
          { connection_id: string }[]
        >`SELECT CAST(CONNECTION_ID() AS CHAR) AS connection_id`
        return ids[0].connection_id
      })
      if (readerIds[0].connection_id === writerId)
        throw new Error('Isolation experiment requires distinct connections')
      const second = await reader.balance.findUniqueOrThrow({
        where: { id: 1 },
      })
      return [first.amount, second.amount]
    },
    { isolationLevel },
  )
