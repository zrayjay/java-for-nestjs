export function labConnection(value: string | undefined) {
  if (!value)
    throw new Error('TX_DATABASE_URL is required for real database tests')
  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new Error('TX_DATABASE_URL must be a valid local test URL')
  }
  const port = Number(url.port || 3306)
  if (
    url.protocol !== 'mysql:' ||
    !['127.0.0.1', 'localhost'].includes(url.hostname) ||
    url.pathname !== '/transaction_lab' ||
    !Number.isInteger(port) ||
    port < 1 ||
    port > 65535
  ) {
    throw new Error('Only the local transaction_lab test database is allowed')
  }
  return {
    host: url.hostname,
    port,
    user: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
    database: 'transaction_lab',
  }
}
