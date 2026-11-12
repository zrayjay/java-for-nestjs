import 'reflect-metadata'
import { randomUUID } from 'node:crypto'
import {
  BadRequestException,
  Body,
  CanActivate,
  Controller,
  ExecutionContext,
  Get,
  Header,
  HttpCode,
  HttpException,
  Injectable,
  Module,
  Param,
  Post,
  Put,
} from '@nestjs/common'
import { APP_GUARD } from '@nestjs/core'
import {
  ArrayMaxSize,
  IsArray,
  IsInt,
  IsNotEmpty,
  IsString,
  Matches,
  MaxLength,
  Min,
} from 'class-validator'
import { createApi } from '@java-for-nestjs/nestjs-reference/api'
import {
  AuthService,
  type Client,
  type SecurityClock,
  systemClock,
  type Tokens,
} from './auth.js'
import { SecurityContext } from './context.js'
import { validateKeys, type SigningKeys } from './keys.js'
import { PolicyTasks } from './tasks.js'

export class LoginInput {
  @IsString({ always: true })
  @Matches(/^[a-z][a-z0-9_-]{0,39}$/, { always: true })
  username!: string
  @IsString({ always: true })
  @IsNotEmpty({ always: true })
  @MaxLength(72, { always: true })
  password!: string
}
export class RefreshInput {
  @IsString({ always: true })
  @Matches(/^[A-Za-z0-9_-]{43}$/, { always: true })
  refreshToken!: string
}
export class RolesInput {
  @IsArray({ always: true })
  @ArrayMaxSize(10, { always: true })
  @IsInt({ each: true, always: true })
  @Min(1, { each: true, always: true })
  roles!: number[]
}
@Controller()
class SecurityController {
  constructor(
    private readonly auth: AuthService,
    private readonly context: SecurityContext,
  ) {}
  @Post('auth/login')
  @HttpCode(200)
  @Header('Cache-Control', 'no-store')
  login(@Body() input: LoginInput): Promise<Tokens> {
    return this.auth.login(input.username, input.password)
  }
  @Post('auth/refresh')
  @HttpCode(200)
  @Header('Cache-Control', 'no-store')
  refresh(@Body() input: RefreshInput): Promise<Tokens> {
    return this.auth.rotate(input.refreshToken)
  }
  @Post('auth/logout')
  @HttpCode(204)
  @Header('Cache-Control', 'no-store')
  logout(): Promise<void> {
    return this.auth.logout()
  }
  @Get('me')
  @Header('Cache-Control', 'no-store')
  me() {
    const user = this.context.current()
    return {
      id: user.id,
      username: user.username,
      departmentId: user.departmentId,
      permissions: [...user.permissions].sort(),
    }
  }
  @Put('admin/users/:id/roles')
  @HttpCode(204)
  roles(@Param('id') raw: string, @Body() input: RolesInput): Promise<void> {
    if (!/^[1-9][0-9]*$/.test(raw) || Number(raw) > 2147483647)
      throw new BadRequestException()
    return this.auth.assignRoles(Number(raw), input.roles)
  }
}
interface Request {
  method: string
  path: string
  headers: { authorization?: string }
  route?: { path?: string }
  rawBody?: Buffer
}
interface Response {
  setHeader(name: string, value: string): void
  status(code: number): { json(body: unknown): void }
}
const parseSource = JSON.parse as (
  text: string,
  reviver: (
    key: string,
    value: unknown,
    source?: { source?: string },
  ) => unknown,
) => unknown
@Injectable()
class SecurityGuard implements CanActivate {
  constructor(private readonly context: SecurityContext) {}
  canActivate(execution: ExecutionContext): boolean {
    const request = execution.switchToHttp().getRequest<Request>()
    const path = request.route?.path ?? request.path
    this.context.authorize(request.method, path)
    const fields =
      path === '/auth/login'
        ? ['username', 'password']
        : path === '/auth/refresh'
          ? ['refreshToken']
          : path === '/admin/users/:id/roles'
            ? ['roles']
            : undefined
    if (fields && ['POST', 'PUT'].includes(request.method)) {
      if (!request.rawBody) throw new BadRequestException()
      const body = parseSource(
        request.rawBody.toString('utf8'),
        (_, value, source) => {
          if (
            path === '/admin/users/:id/roles' &&
            typeof value === 'number' &&
            (!source?.source ||
              !/^[1-9][0-9]*$/.test(source.source) ||
              value > 2147483647)
          )
            throw new BadRequestException()
          return value
        },
      )
      if (
        !body ||
        typeof body !== 'object' ||
        Array.isArray(body) ||
        Object.keys(body).some((key) => !fields.includes(key))
      )
        throw new BadRequestException()
    }
    return true
  }
}
@Module({ controllers: [SecurityController] })
class SecurityModule {}
export async function createSecurityApi(
  client: Client,
  keys: SigningKeys,
  clock: SecurityClock = systemClock,
  port = 0,
) {
  await validateKeys(keys)
  const context = new SecurityContext()
  const auth = new AuthService(client, keys, clock, context)
  const feature = {
    module: SecurityModule,
    providers: [
      { provide: AuthService, useValue: auth },
      { provide: SecurityContext, useValue: context },
      { provide: APP_GUARD, useClass: SecurityGuard },
    ],
  }
  return createApi(port, new PolicyTasks(auth, context), feature, (app) => {
    app.use((request: Request, response: Response, next: () => void) => {
      context.run({ requestId: randomUUID() }, () => {
        response.setHeader('X-Request-Id', context.state()!.requestId)
        response.setHeader('Cache-Control', 'no-store')
        // Authentication/authorization precede body parsing and DTO validation.
        void auth
          .authenticate(request.headers.authorization)
          .then((user) => {
            context.state()!.user = user
            context.authorize(request.method, request.path)
            next()
          })
          .catch((error: unknown) => {
            const status =
              error instanceof HttpException ? error.getStatus() : 500
            const code =
              status === 401
                ? 'unauthorized'
                : status === 403
                  ? 'forbidden'
                  : 'internal_error'
            response.status(status).json({ code })
          })
      })
    })
  })
}
