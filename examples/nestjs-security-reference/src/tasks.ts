import { NotFoundException } from '@nestjs/common'
import {
  TaskStorage,
  type Task,
  type TaskWrite,
} from '@java-for-nestjs/nestjs-reference/api'
import { AuthService } from './auth.js'
import { SecurityContext } from './context.js'

const view = (value: {
  id: number
  title: string
  priority: number
}): Task => ({
  id: value.id,
  title: value.title,
  details: { priority: value.priority },
})
export class PolicyTasks extends TaskStorage {
  constructor(
    private readonly auth: AuthService,
    private readonly context: SecurityContext,
  ) {
    super()
  }
  private readable() {
    this.context.require('task:read:department', 'task:read:any')
    const user = this.context.current()
    return user.permissions.has('task:read:any')
      ? {}
      : { owner: { identity: { departmentId: user.departmentId } } }
  }
  private writable(id: number) {
    this.context.require('task:write:own', 'task:write:any')
    const user = this.context.current()
    return user.permissions.has('task:write:any')
      ? { id }
      : { id, ownerId: user.id }
  }
  async list(limit: number): Promise<Task[]> {
    return (
      await this.auth.client.dataTask.findMany({
        where: this.readable(),
        orderBy: { id: 'asc' },
        take: limit,
      })
    ).map(view)
  }
  async find(id: number): Promise<Task> {
    const row = await this.auth.client.dataTask.findFirst({
      where: { id, ...this.readable() },
    })
    if (!row) throw new NotFoundException()
    return view(row)
  }
  async create(input: TaskWrite): Promise<Task> {
    this.context.require('task:write:own', 'task:write:any')
    const user = this.context.current()
    return this.auth.client.$transaction(async (tx) => {
      const row = await tx.dataTask.create({
        data: {
          title: input.title,
          priority: input.details.priority,
          ownerId: user.id,
        },
      })
      await this.auth.audit(tx, user.id, 'task.created', String(row.id))
      return view(row)
    })
  }
  async replace(id: number, input: TaskWrite): Promise<Task> {
    const where = this.writable(id)
    return this.auth.client.$transaction(async (tx) => {
      const result = await tx.dataTask.updateMany({
        where,
        data: { title: input.title, priority: input.details.priority },
      })
      if (!result.count) throw new NotFoundException()
      await this.auth.audit(
        tx,
        this.context.current().id,
        'task.updated',
        String(id),
      )
      return {
        id,
        title: input.title,
        details: { priority: input.details.priority },
      }
    })
  }
  async delete(id: number): Promise<void> {
    const where = this.writable(id)
    await this.auth.client.$transaction(async (tx) => {
      if (!(await tx.dataTask.deleteMany({ where })).count)
        throw new NotFoundException()
      await this.auth.audit(
        tx,
        this.context.current().id,
        'task.deleted',
        String(id),
      )
    })
  }
}
