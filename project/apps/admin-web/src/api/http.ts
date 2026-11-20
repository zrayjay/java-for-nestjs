export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId?: string

  constructor(
    status: number,
    code: string,
    message: string,
    requestId?: string,
  ) {
    super(message)
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

export type Result<T> = { data?: T; error?: unknown; response?: Response }
type Operation<T> = () => Promise<Result<T>>

const messages: Readonly<Record<number, string>> = {
  400: '请检查填写的内容。',
  401: '登录凭据无效或会话已过期。',
  403: '当前账号没有执行此操作的权限。',
  404: '找不到该记录。',
  409: '记录已经变化，请刷新后重试。',
  500: '服务暂时无法处理此请求。',
}

export async function request<T>(
  operation: Operation<T>,
): Promise<T | undefined> {
  const result = await operation().catch(() => {
    throw new ApiError(0, 'NETWORK_ERROR', '无法连接订单服务，请稍后重试。')
  })
  const response = result.response
  if (!response)
    throw new ApiError(0, 'NETWORK_ERROR', '无法连接订单服务，请稍后重试。')
  if (response.ok) return result.data

  const error = result.error
  const code =
    error &&
    typeof error === 'object' &&
    'code' in error &&
    typeof error.code === 'string'
      ? error.code
      : 'REQUEST_FAILED'
  const requestId = response.headers.get('X-Request-Id') ?? undefined
  throw new ApiError(
    response.status,
    code,
    messages[response.status] ?? '请求未完成，请重试。',
    requestId,
  )
}

export function errorMessage(failure: unknown) {
  return failure instanceof ApiError ? failure.message : '请求未完成，请重试。'
}

export async function expectData<T>(operation: Operation<T>): Promise<T> {
  const data = await request(operation)
  if (data === undefined)
    throw new ApiError(0, 'EMPTY_RESPONSE', '服务返回的结果不完整。')
  return data
}

export function bearer(token: string) {
  return { Authorization: `Bearer ${token}` }
}
