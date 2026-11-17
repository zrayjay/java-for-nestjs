import { test } from 'node:test'
import assert from 'node:assert/strict'
import { fork } from 'node:child_process'
import { connect } from 'node:net'
import { once } from 'node:events'
import { setTimeout as delay } from 'node:timers/promises'

test(
  'real SIGTERM drains a partially received HTTP request before process exit',
  { timeout: 15000 },
  async () => {
    const child = fork(new URL('./server.mjs', import.meta.url), {
      stdio: ['ignore', 'pipe', 'pipe', 'ipc'],
    })
    const messages = []
    child.on('message', (message) => messages.push(message))
    const exited = once(child, 'exit')
    let socket
    async function message(type) {
      const deadline = Date.now() + 5000
      while (Date.now() < deadline) {
        const found = messages.find((value) => value.type === type)
        if (found) return found
        await delay(10)
      }
      throw new Error(`No child message ${type}`)
    }
    try {
      const { port } = await message('listening')
      socket = connect(port, '127.0.0.1')
      let response = ''
      socket.on('data', (chunk) => {
        response += chunk
      })
      const ended = once(socket, 'end')
      await once(socket, 'connect')
      socket.write(
        'POST /work HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\nConnection: close\r\n\r\nab',
      )
      await message('admitted')
      child.kill('SIGTERM')
      await message('draining')
      socket.write('cd')
      await ended
      assert.match(response, /^HTTP\/1.1 200/)
      assert.ok(response.includes('{"bytes":4}'))
      const [code, signal] = await exited
      assert.equal(code, 0)
      assert.equal(signal, null)
    } finally {
      socket?.destroy()
      if (child.exitCode === null && child.signalCode === null) {
        child.kill('SIGKILL')
        await exited
      }
    }
  },
)
