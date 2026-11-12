import 'reflect-metadata'
import { InputBoundary } from './input-boundary.js'
import {
  LAB_CONFIGURATION,
  LifecycleProbe,
  readConfiguration,
} from './configuration.js'
import {
  ArgumentsHost,
  BadRequestException,
  Body,
  Catch,
  Controller,
  Delete,
  DynamicModule,
  ExceptionFilter,
  Get,
  HttpCode,
  HttpException,
  Injectable,
  Module,
  NotFoundException,
  Param,
  Post,
  Put,
  Query,
  Req,
  Res,
  ValidationPipe,
  Type as NestType,
} from '@nestjs/common'
import { APP_GUARD, APP_INTERCEPTOR, NestFactory } from '@nestjs/core'
import {
  RequestTrace,
  TraceGuard,
  TraceInterceptor,
  TraceRequest,
  TraceResponse,
} from './trace.js'
import { ApiProperty, DocumentBuilder, SwaggerModule } from '@nestjs/swagger'
import { Type } from 'class-transformer'
import {
  IsInt,
  IsObject,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator'

// Explicit absolute end assertion; no characters may follow the title.
const titlePattern = /^[A-Za-z][A-Za-z0-9 _-]{0,79}(?![\s\S])/
export class TaskDetails {
  @ApiProperty({ minimum: 1, maximum: 5 })
  @IsInt({ always: true })
  @Min(1, { always: true })
  @Max(5, { always: true })
  priority!: number
}
export class TaskWrite {
  @ApiProperty({ pattern: titlePattern.source })
  @IsString({ always: true })
  @Matches(titlePattern, { always: true })
  title!: string

  @ApiProperty({ type: TaskDetails })
  @IsObject({ always: true })
  @ValidateNested({ always: true })
  @Type(() => TaskDetails)
  details!: TaskDetails
}
export class ValidationProbe {
  @IsString({ always: true })
  @MaxLength(80, { always: true })
  @Matches(titlePattern, { groups: ['publish'] })
  title!: string
}
export interface Task {
  id: number
  title: string
  details: { priority: number }
}
function positive(raw: string, maximum = 2147483647): number {
  if (!/^[1-9][0-9]*$/.test(raw)) throw new BadRequestException()
  const value = Number(raw)
  if (!Number.isSafeInteger(value) || value > maximum)
    throw new BadRequestException()
  return value
}
@Injectable()
export abstract class TaskStorage {
  abstract list(limit: number): Task[] | Promise<Task[]>
  abstract find(id: number): Task | Promise<Task>
  abstract create(input: TaskWrite): Task | Promise<Task>
  abstract replace(id: number, input: TaskWrite): Task | Promise<Task>
  abstract delete(id: number): void | Promise<void>
}
@Injectable()
export class TaskRepository extends TaskStorage {
  private readonly items = new Map<number, Task>()
  private nextId = 0
  list(limit: number): Task[] {
    return [...this.items.values()].sort((a, b) => a.id - b.id).slice(0, limit)
  }
  find(id: number): Task {
    const item = this.items.get(id)
    if (!item) throw new NotFoundException()
    return item
  }
  create(input: TaskWrite): Task {
    if (this.nextId === 2147483647) throw new Error('ID space exhausted')
    const task = {
      id: ++this.nextId,
      title: input.title,
      details: { priority: input.details.priority },
    }
    this.items.set(task.id, task)
    return task
  }
  replace(id: number, input: TaskWrite): Task {
    this.find(id)
    const task = {
      id,
      title: input.title,
      details: { priority: input.details.priority },
    }
    this.items.set(id, task)
    return task
  }
  delete(id: number): void {
    this.find(id)
    this.items.delete(id)
  }
}
@Injectable()
export class TaskService {
  constructor(private readonly repository: TaskStorage) {}
  async list(limit: string): Promise<Task[]> {
    return this.repository.list(positive(limit, 100))
  }
  async find(id: string): Promise<Task> {
    return this.repository.find(positive(id))
  }
  async create(input: TaskWrite): Promise<Task> {
    return this.repository.create(input)
  }
  async replace(id: string, input: TaskWrite): Promise<Task> {
    return this.repository.replace(positive(id), input)
  }
  async delete(id: string): Promise<void> {
    await this.repository.delete(positive(id))
  }
}
interface LocationResponse {
  setHeader(name: string, value: string): void
}
@Controller('tasks')
class TasksController {
  constructor(
    private readonly service: TaskService,
    private readonly trace: RequestTrace,
  ) {}
  @Get()
  list(
    @Query('limit') limit = '20',
    @Req() request: TraceRequest,
  ): Promise<Task[]> {
    this.trace.add(request, 'controller')
    return this.service.list(limit)
  }
  @Get(':id')
  find(@Param('id') id: string): Promise<Task> {
    return this.service.find(id)
  }
  @Post()
  async create(
    @Body() input: TaskWrite,
    @Res({ passthrough: true }) response: LocationResponse,
  ): Promise<Task> {
    const task = await this.service.create(input)
    response.setHeader('Location', `/tasks/${task.id}`)
    return task
  }
  @Put(':id')
  replace(@Param('id') id: string, @Body() input: TaskWrite): Promise<Task> {
    return this.service.replace(id, input)
  }
  @Delete(':id')
  @HttpCode(204)
  delete(@Param('id') id: string): Promise<void> {
    return this.service.delete(id)
  }
}
@Controller()
class LabController {
  @Get('health')
  health(): { status: string } {
    return { status: 'ok' }
  }
  @Post('validation/draft')
  @HttpCode(204)
  draft(
    @Body(
      new ValidationPipe({
        groups: ['draft'],
        whitelist: true,
        forbidNonWhitelisted: true,
      }),
    )
    _input: ValidationProbe,
  ): void {}
  @Post('validation/publish')
  @HttpCode(204)
  publish(
    @Body(
      new ValidationPipe({
        groups: ['publish'],
        whitelist: true,
        forbidNonWhitelisted: true,
      }),
    )
    _input: ValidationProbe,
  ): void {}
}
@Catch()
class ApiErrors implements ExceptionFilter {
  catch(error: unknown, host: ArgumentsHost): void {
    const status = error instanceof HttpException ? error.getStatus() : 500
    const code =
      (
        {
          400: 'invalid_request',
          401: 'unauthorized',
          403: 'forbidden',
          404: 'not_found',
          413: 'payload_too_large',
          415: 'unsupported_media_type',
        } as Record<number, string>
      )[status] ?? 'internal_error'
    host
      .switchToHttp()
      .getResponse<{ status(value: number): { json(body: unknown): void } }>()
      .status(status)
      .json({ code })
  }
}
@Module({
  controllers: [TasksController, LabController],
  providers: [
    {
      provide: LAB_CONFIGURATION,
      useFactory: () => readConfiguration(process.env),
    },
    LifecycleProbe,
    TaskRepository,
    { provide: TaskStorage, useExisting: TaskRepository },
    TaskService,
    RequestTrace,
    { provide: APP_GUARD, useClass: TraceGuard },
    { provide: APP_GUARD, useClass: InputBoundary },
    { provide: APP_INTERCEPTOR, useClass: TraceInterceptor },
  ],
})
class ApiModule {}
export async function createApi(
  port = 0,
  storage?: TaskStorage,
  feature?: NestType<unknown> | DynamicModule,
  setup?: (
    app: import('@nestjs/common').INestApplication,
  ) => void | Promise<void>,
) {
  const module = {
    module: ApiModule,
    imports: feature ? [feature] : [],
    providers: storage ? [{ provide: TaskStorage, useValue: storage }] : [],
  }
  const app = await NestFactory.create(module, {
    logger: false,
    rawBody: true,
  })
  if (setup) await setup(app)
  const trace = app.get(RequestTrace)
  app.use(
    (request: TraceRequest, response: TraceResponse, next: () => void) => {
      trace.begin(request, response)
      next()
    },
  )
  app.useGlobalPipes(
    new ValidationPipe({
      transform: true,
      whitelist: true,
      forbidNonWhitelisted: true,
      transformOptions: { enableImplicitConversion: false },
      groups: ['shared'],
    }),
  )
  app.useGlobalFilters(new ApiErrors())
  const document = SwaggerModule.createDocument(
    app,
    new DocumentBuilder().setTitle('Task teaching API').setVersion('1').build(),
  )
  SwaggerModule.setup('api-docs', app, document, {
    jsonDocumentUrl: '/openapi.json',
  })
  await app.listen(port, '127.0.0.1')
  return app
}
