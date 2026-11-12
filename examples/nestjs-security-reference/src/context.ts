import { AsyncLocalStorage } from 'node:async_hooks'
import { ForbiddenException, UnauthorizedException } from '@nestjs/common'

export interface Principal {
  id: number
  username: string
  departmentId: number
  permissions: ReadonlySet<string>
  sessionId: string
}
export interface RequestState {
  requestId: string
  user?: Principal
}
export class SecurityContext {
  private readonly local = new AsyncLocalStorage<RequestState>()
  run(state: RequestState, work: () => void): void {
    this.local.run(state, work)
  }
  state(): RequestState | undefined {
    return this.local.getStore()
  }
  current(): Principal {
    const user = this.state()?.user
    if (!user) throw new ForbiddenException()
    return user
  }
  require(...permissions: string[]): void {
    const user = this.current()
    if (!permissions.some((permission) => user.permissions.has(permission)))
      throw new ForbiddenException()
  }
  onModuleDestroy(): void {
    this.local.disable()
  }
  authorize(method: string, rawPath: string): void {
    const path = rawPath.toLowerCase().replace(/\/+$/, '') || '/'
    if (
      [
        '/auth/login',
        '/auth/refresh',
        '/health',
        '/actuator/health',
        '/openapi.json',
      ].includes(path)
    )
      return
    if (!this.state()?.user) throw new UnauthorizedException()
    if (
      (method === 'GET' && path === '/me') ||
      (method === 'POST' && path === '/auth/logout')
    )
      return
    if (path.startsWith('/admin/')) {
      this.require('role:manage')
      return
    }
    const item = /^\/tasks\/[^/]+$/.test(path)
    if (method === 'GET' && (path === '/tasks' || item)) {
      this.require('task:read:department', 'task:read:any')
      return
    }
    if (
      (method === 'POST' && path === '/tasks') ||
      (['PUT', 'DELETE'].includes(method) && item)
    ) {
      this.require('task:write:own', 'task:write:any')
      return
    }
    throw new ForbiddenException()
  }
}
