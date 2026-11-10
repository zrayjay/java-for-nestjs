import { test } from 'node:test'
import assert from 'node:assert/strict'
import { labConnection } from './connection.js'

test('the destructive fixture is restricted to the named loopback database', () => {
  for (const value of [
    undefined,
    'invalid',
    'postgresql://lab@localhost/transaction_lab',
    'mysql://lab@example.com/transaction_lab',
    'mysql://lab@localhost/production',
    'mysql://lab@localhost:99999/transaction_lab',
  ]) {
    assert.throws(() => labConnection(value))
  }
})
test('valid fixture URLs decode credentials without changing the database scope', () => {
  assert.deepEqual(
    labConnection('mysql://lab:p%40ss@127.0.0.1:13306/transaction_lab'),
    {
      host: '127.0.0.1',
      port: 13306,
      user: 'lab',
      password: 'p@ss',
      database: 'transaction_lab',
    },
  )
})
