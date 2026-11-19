import {
  type ArgumentsHost,
  Body,
  Catch,
  Controller,
  type ExceptionFilter,
  Get,
  Headers,
  HttpCode,
  HttpException,
  Module,
  type OnModuleDestroy,
  Param,
  Patch,
  Post,
  Query,
  UseFilters,
} from '@nestjs/common'
import { DatabaseSync } from 'node:sqlite'
import { createApi } from './api.js'
import type { TraceRequest } from './trace.js'

type Status = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'CANCELLED'
type Action = 'EDIT' | 'SUBMIT' | 'APPROVE' | 'CANCEL'
type Permission = Action | 'READ' | 'CREATE'
type Scope = 'OWN' | 'DEPARTMENT' | 'ALL'
export type OrderFixtureActor = {
  id: string
  department: string
  grants: Partial<Record<Permission, Scope>>
}

type Row = {
  id: number
  customerName: string
  totalAmountCents: number
  ownerId: string
  departmentId: string
  status: Status
  version: number
  createdAt: string
  updatedAt: string
}

const target = {
  EDIT: 'DRAFT',
  SUBMIT: 'SUBMITTED',
  APPROVE: 'APPROVED',
  CANCEL: 'CANCELLED',
} as const
const actions = Object.keys(target) as Action[]

class Failure extends HttpException {
  constructor(
    readonly code: string,
    status: number,
  ) {
    super(code, status)
  }
}

@Catch(Failure)
class ReferenceErrors implements ExceptionFilter {
  catch(error: Failure, context: ArgumentsHost) {
    const http = context.switchToHttp()
    const request = http.getRequest<TraceRequest>()
    http
      .getResponse<{ status(code: number): { json(body: object): void } }>()
      .status(error.getStatus())
      .json({
        code: error.code,
        message: error.message,
        requestId: request.labTrace?.id,
      })
  }
}

class OrderModel implements OnModuleDestroy {
  private readonly db = new DatabaseSync(':memory:')

  constructor(
    private readonly tokens: ReadonlyMap<string, OrderFixtureActor>,
    private readonly beforeHistory: () => void,
  ) {
    this.db.exec(`
      CREATE TABLE orders (
        id INTEGER PRIMARY KEY, customerName TEXT NOT NULL,
        totalAmountCents INTEGER NOT NULL, ownerId TEXT NOT NULL,
        departmentId TEXT NOT NULL, status TEXT NOT NULL,
        version INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL
      );
      CREATE TABLE history (
        orderId INTEGER NOT NULL, actorId TEXT NOT NULL, action TEXT NOT NULL,
        fromStatus TEXT, toStatus TEXT NOT NULL, version INTEGER NOT NULL,
        occurredAt TEXT NOT NULL, PRIMARY KEY (orderId, version)
      );
    `)
  }

  onModuleDestroy() {
    this.db.close()
  }

  actor(authorization?: string) {
    const match = /^Bearer (.+)$/i.exec(authorization ?? '')
    const actor = match && this.tokens.get(match[1])
    if (!actor) throw new Failure('UNAUTHENTICATED', 401)
    return actor
  }

  list(
    actor: OrderFixtureActor,
    state?: string,
    pageText = '1',
    sizeText = '20',
  ) {
    const scope = requireScope(actor, 'READ')
    const page = positive(pageText)
    const size = positive(sizeText)
    if (
      size > 100 ||
      (state !== undefined && !Object.values(target).includes(state as Status))
    )
      throw new Failure('VALIDATION_ERROR', 400)
    const rows = this.db
      .prepare('SELECT * FROM orders ORDER BY id DESC')
      .all() as unknown as Row[]
    const visible = rows.filter(
      (row) => inScope(actor, scope, row) && (!state || row.status === state),
    )
    const offset = (page - 1) * size
    return {
      items: visible
        .slice(offset, offset + size)
        .map((row) => view(actor, row)),
      page,
      size,
      total: visible.length,
    }
  }

  detail(actor: OrderFixtureActor, id: number) {
    return view(actor, this.visible(actor, 'READ', id))
  }

  history(actor: OrderFixtureActor, id: number) {
    this.visible(actor, 'READ', id)
    return this.db
      .prepare(
        'SELECT actorId, action, fromStatus, toStatus, version, occurredAt FROM history WHERE orderId = ? ORDER BY version',
      )
      .all(id)
  }

  create(actor: OrderFixtureActor, input: unknown) {
    requireScope(actor, 'CREATE')
    const { customer, amount } = draft(
      fields(input, ['customerName', 'totalAmountCents']),
    )
    return this.transaction(() => {
      const now = new Date().toISOString()
      const id = Number(
        this.db
          .prepare(
            `
        INSERT INTO orders (customerName, totalAmountCents, ownerId, departmentId, status, version, createdAt, updatedAt)
        VALUES (?, ?, ?, ?, 'DRAFT', 1, ?, ?)
      `,
          )
          .run(customer, amount, actor.id, actor.department, now, now)
          .lastInsertRowid,
      )
      this.append(id, actor.id, 'CREATE', null, 'DRAFT', 1, now)
      return view(actor, this.row(id)!)
    })
  }

  change(actor: OrderFixtureActor, id: number, action: Action, input: unknown) {
    return this.transaction(() => {
      const before = this.visible(actor, action, id)
      const isOwner = owned(actor, before)
      if (action === 'CANCEL' && before.status === 'DRAFT' && !isOwner)
        throw new Failure('NOT_FOUND', 404)
      if (action === 'APPROVE' && actor.id === before.ownerId)
        throw new Failure('SELF_APPROVAL_FORBIDDEN', 403)
      const payload = fields(
        input,
        action === 'EDIT'
          ? ['customerName', 'totalAmountCents', 'version']
          : ['version'],
      )
      if (
        typeof payload.version !== 'number' ||
        !Number.isInteger(payload.version) ||
        payload.version < 1 ||
        payload.version > 2_147_483_647
      )
        throw new Failure('VALIDATION_ERROR', 400)
      if (payload.version !== before.version)
        throw new Failure('VERSION_CONFLICT', 409)
      if (!eligible(action, before.status, isOwner))
        throw new Failure('INVALID_TRANSITION', 409)
      const next =
        action === 'EDIT'
          ? draft(payload)
          : {
              customer: before.customerName,
              amount: before.totalAmountCents,
            }
      const now = new Date().toISOString()
      const version = before.version + 1
      this.db
        .prepare(
          `
        UPDATE orders SET customerName = ?, totalAmountCents = ?, status = ?, version = ?, updatedAt = ?
        WHERE id = ?
      `,
        )
        .run(next.customer, next.amount, target[action], version, now, id)
      this.append(
        id,
        actor.id,
        action,
        before.status,
        target[action],
        version,
        now,
      )
      return view(actor, this.row(id)!)
    })
  }

  private append(
    id: number,
    actor: string,
    action: string,
    from: Status | null,
    to: Status,
    version: number,
    now: string,
  ) {
    this.beforeHistory()
    this.db
      .prepare('INSERT INTO history VALUES (?, ?, ?, ?, ?, ?, ?)')
      .run(id, actor, action, from, to, version, now)
  }

  private transaction<T>(operation: () => T) {
    this.db.exec('BEGIN IMMEDIATE')
    try {
      const result = operation()
      this.db.exec('COMMIT')
      return result
    } catch (error) {
      this.db.exec('ROLLBACK')
      if (error instanceof Failure) throw error
      throw new Failure('INTERNAL_ERROR', 500)
    }
  }

  private visible(
    actor: OrderFixtureActor,
    permission: Permission,
    id: number,
  ) {
    const scope = requireScope(actor, permission)
    const row = this.row(id)
    if (!row || !inScope(actor, scope, row)) throw new Failure('NOT_FOUND', 404)
    return row
  }

  private row(id: number) {
    return this.db.prepare('SELECT * FROM orders WHERE id = ?').get(id) as
      Row | undefined
  }
}

function requireScope(actor: OrderFixtureActor, action: Permission) {
  const scope = actor.grants[action]
  if (!scope) throw new Failure('FORBIDDEN', 403)
  return scope
}

function owned(actor: OrderFixtureActor, row: Row) {
  return actor.id === row.ownerId && actor.department === row.departmentId
}

function inScope(actor: OrderFixtureActor, scope: Scope, row: Row) {
  if (scope === 'ALL') return true
  if (scope === 'DEPARTMENT') return actor.department === row.departmentId
  return owned(actor, row)
}

function eligible(action: Action, status: Status, owner: boolean) {
  if (action === 'APPROVE') return status === 'SUBMITTED'
  if (action === 'CANCEL')
    return status === 'SUBMITTED' || (owner && status === 'DRAFT')
  return status === 'DRAFT' && owner
}

function view(actor: OrderFixtureActor, row: Row) {
  return {
    ...row,
    id: String(row.id),
    allowedActions: actions.filter((action) => {
      const scope = actor.grants[action]
      return (
        scope &&
        inScope(actor, scope, row) &&
        eligible(action, row.status, owned(actor, row)) &&
        (action !== 'APPROVE' || actor.id !== row.ownerId)
      )
    }),
  }
}

function positive(source: string) {
  const value = Number(source)
  if (!/^[1-9]\d*$/.test(source) || !Number.isSafeInteger(value))
    throw new Failure('VALIDATION_ERROR', 400)
  return value
}

function fields(input: unknown, names: string[]) {
  if (
    !input ||
    typeof input !== 'object' ||
    Array.isArray(input) ||
    Object.keys(input).some((key) => !names.includes(key))
  )
    throw new Failure('VALIDATION_ERROR', 400)
  return input as Record<string, unknown>
}

function draft(input: Record<string, unknown>) {
  const customer =
    typeof input.customerName === 'string'
      ? input.customerName.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '')
      : ''
  const amount = input.totalAmountCents
  if (
    !customer ||
    Array.from(customer).length > 100 ||
    typeof amount !== 'number' ||
    !Number.isInteger(amount) ||
    amount < 1 ||
    amount > 999_999_999
  )
    throw new Failure('VALIDATION_ERROR', 400)
  return { customer, amount }
}

@Controller('api/v1/orders')
@UseFilters(ReferenceErrors)
class ReferenceOrderController {
  constructor(private readonly orders: OrderModel) {}

  @Get()
  list(
    @Headers('authorization') authorization?: string,
    @Query('status') state?: string,
    @Query('page') page?: string,
    @Query('size') size?: string,
  ) {
    return this.orders.list(this.orders.actor(authorization), state, page, size)
  }

  @Get(':id')
  detail(
    @Headers('authorization') authorization: string,
    @Param('id') id: string,
  ) {
    return this.orders.detail(this.orders.actor(authorization), positive(id))
  }

  @Get(':id/history')
  history(
    @Headers('authorization') authorization: string,
    @Param('id') id: string,
  ) {
    return this.orders.history(this.orders.actor(authorization), positive(id))
  }

  @Post()
  create(
    @Headers('authorization') authorization: string,
    @Body() input: unknown,
  ) {
    return this.orders.create(this.orders.actor(authorization), input)
  }

  @Patch(':id')
  edit(
    @Headers('authorization') authorization: string,
    @Param('id') id: string,
    @Body() input: unknown,
  ) {
    return this.orders.change(
      this.orders.actor(authorization),
      positive(id),
      'EDIT',
      input,
    )
  }

  @Post(':id/:operation')
  @HttpCode(200)
  transition(
    @Headers('authorization') authorization: string,
    @Param('id') id: string,
    @Param('operation') operation: string,
    @Body() input: unknown,
  ) {
    const actor = this.orders.actor(authorization)
    if (
      operation !== 'submit' &&
      operation !== 'approve' &&
      operation !== 'cancel'
    )
      throw new Failure('NOT_FOUND', 404)
    return this.orders.change(
      actor,
      positive(id),
      operation.toUpperCase() as Action,
      input,
    )
  }
}

@Module({ controllers: [ReferenceOrderController] })
class OrderReferenceModule {}

export function createOrderReference(
  tokens: ReadonlyMap<string, OrderFixtureActor>,
  beforeHistory: () => void = () => {},
) {
  return createApi(0, undefined, {
    module: OrderReferenceModule,
    providers: [
      { provide: OrderModel, useValue: new OrderModel(tokens, beforeHistory) },
    ],
  })
}
