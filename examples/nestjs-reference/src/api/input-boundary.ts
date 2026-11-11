import {
  BadRequestException,
  CanActivate,
  ExecutionContext,
  Injectable,
} from '@nestjs/common'

// Exact decimal check for this contract's small integers, before binary64 rounding.
export function isPriorityToken(source: string): boolean {
  const match = /^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(source)
  if (!match) return false
  const fraction = match[2] ?? ''
  const digits = (match[1] + fraction).replace(/^0+/, '')
  const significant = digits.replace(/0+$/, '')
  const trailingZeros = digits.length - significant.length
  const exponent = Number(match[3] ?? '0')
  return (
    /^[1-5]$/.test(significant) &&
    Number.isSafeInteger(exponent) &&
    exponent + trailingZeros - fraction.length === 0
  )
}
function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function keys(value: Record<string, unknown>, allowed: string[]): void {
  if (Object.keys(value).some((key) => !allowed.includes(key)))
    throw new BadRequestException()
}
// Node22.20 supplies the original numeric token as reviver context.source.
// The pinned TS lib predates that runtime overload.
const parseWithSource = JSON.parse as (
  text: string,
  reviver: (
    key: string,
    value: unknown,
    context?: { source?: string },
  ) => unknown,
) => unknown

@Injectable()
export class InputBoundary implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{
      method: string
      path: string
      route?: { path?: string }
      rawBody?: Buffer
    }>()
    const tasks =
      ['POST', 'PUT'].includes(request.method) &&
      ['/tasks', '/tasks/:id'].includes(request.route?.path ?? '')
    const validation =
      request.method === 'POST' &&
      ['/validation/draft', '/validation/publish'].includes(
        request.route?.path ?? '',
      )
    if (!tasks && !validation) return true
    if (!request.rawBody) throw new BadRequestException()
    const input = parseWithSource(
      request.rawBody.toString('utf8'),
      (key, value, source) => {
        if (
          tasks &&
          key === 'priority' &&
          typeof value === 'number' &&
          (!source?.source || !isPriorityToken(source.source))
        )
          throw new BadRequestException()
        return value
      },
    )
    if (!object(input)) throw new BadRequestException()
    keys(input, tasks ? ['title', 'details'] : ['title'])
    if (tasks && object(input.details)) keys(input.details, ['priority'])
    return true
  }
}
