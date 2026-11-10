import 'reflect-metadata'
import assert from 'node:assert/strict'
import test from 'node:test'
import { Test } from '@nestjs/testing'
import { AppModule } from '../app.module.js'
import { GreetingService } from './greeting.service.js'

test('assembled Nest module follows the shared greeting contract', async () => {
  const app = await Test.createTestingModule({ imports: [AppModule] }).compile()
  try {
    const service = app.get(GreetingService)
    assert.equal(service.greet('NestJS'), 'Hello, NestJS!')
    assert.equal(service.greet('reader_21'), 'Hello, reader_21!')
    for (const invalid of [
      '',
      ' ',
      ' reader',
      'reader\n',
      '1reader',
      '读者',
      'abcdefghijklmnopqrstuvwxyzABCDEFG',
    ]) {
      assert.throws(() => service.greet(invalid))
    }
  } finally {
    await app.close()
  }
})
