import { NotFoundException } from '@nestjs/common'
import {
  TaskStorage,
  type Task,
  type TaskWrite,
} from '@java-for-nestjs/nestjs-reference/api'
import { createDataClient } from '@java-for-nestjs/prisma-reference/data-client'

type Client = ReturnType<typeof createDataClient>
function view(row: { id: number; title: string; priority: number }): Task {
  return { id: row.id, title: row.title, details: { priority: row.priority } }
}
export class PrismaTasks extends TaskStorage {
  constructor(private readonly client: Client) {
    super()
  }
  async list(limit: number): Promise<Task[]> {
    return (
      await this.client.dataTask.findMany({
        orderBy: { id: 'asc' },
        take: limit,
      })
    ).map(view)
  }
  async find(id: number): Promise<Task> {
    const task = await this.client.dataTask.findUnique({ where: { id } })
    if (!task) throw new NotFoundException()
    return view(task)
  }
  async create(input: TaskWrite): Promise<Task> {
    return view(
      await this.client.dataTask.create({
        data: {
          title: input.title,
          priority: input.details.priority,
          ownerId: 1,
        },
      }),
    )
  }
  async replace(id: number, input: TaskWrite): Promise<Task> {
    const result = await this.client.dataTask.updateMany({
      where: { id },
      data: { title: input.title, priority: input.details.priority },
    })
    if (result.count === 0) throw new NotFoundException()
    return this.find(id)
  }
  async delete(id: number): Promise<void> {
    const result = await this.client.dataTask.deleteMany({ where: { id } })
    if (result.count === 0) throw new NotFoundException()
  }
  async onModuleDestroy(): Promise<void> {
    await this.client.$disconnect()
  }
}
