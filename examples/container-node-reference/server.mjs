import { createServer } from 'node:http'

// Native HTTP lifecycle comparison only; not the P4 authenticated/SQL API.
let draining = false
const server = createServer(async (request, response) => {
  if (request.url === '/health/live') {
    response.end('UP')
    return
  }
  if (request.url === '/health/ready') {
    response.statusCode = draining ? 503 : 200
    response.end(draining ? 'DRAINING' : 'READY')
    return
  }
  if (request.method !== 'POST' || request.url !== '/work') {
    response.statusCode = 404
    response.end()
    return
  }
  let bytes = 0
  process.send?.({ type: 'admitted' })
  try {
    for await (const chunk of request) {
      bytes += chunk.length
      if (bytes > 1024) {
        response.statusCode = 413
        response.end()
        return
      }
    }
    response.setHeader('content-type', 'application/json')
    response.end(JSON.stringify({ bytes }))
  } catch {
    response.destroy()
  }
})
server.requestTimeout = 10000
server.headersTimeout = 10000
server.listen(0, '127.0.0.1', () => {
  process.send?.({ type: 'listening', port: server.address().port })
})
function stop() {
  if (draining) return
  draining = true
  process.send?.({ type: 'draining' })
  const deadline = setTimeout(() => {
    process.exitCode = 1
    server.closeAllConnections()
  }, 5000)
  server.close(() => {
    clearTimeout(deadline)
    console.log('NODE_DRAINED')
    process.disconnect?.()
  })
}
process.on('SIGTERM', stop)
process.on('SIGINT', stop)
