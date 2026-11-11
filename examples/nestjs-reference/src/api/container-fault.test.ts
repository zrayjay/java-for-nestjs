import 'reflect-metadata'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Test } from '@nestjs/testing'

test('actual Nest container rejects a missing dependency', async () => {
  await assert.rejects(
    Test.createTestingModule({
      providers: [
        {
          provide: 'consumer',
          useFactory: (port: unknown) => ({ port }),
          inject: ['missing-port'],
        },
      ],
    }).compile(),
    /can't resolve dependencies/i,
  )
  const repaired = await Test.createTestingModule({
    providers: [
      { provide: 'missing-port', useValue: {} },
      {
        provide: 'consumer',
        useFactory: (port: unknown) => ({ port }),
        inject: ['missing-port'],
      },
    ],
  }).compile()
  try {
    assert.equal(repaired.get('consumer').port, repaired.get('missing-port'))
  } finally {
    await repaired.close()
  }
})

test('actual Nest container rejects a circular pair of factory providers', async () => {
  await assert.rejects(
    Test.createTestingModule({
      providers: [
        {
          provide: 'first',
          useFactory: (second: unknown) => ({ second }),
          inject: ['second'],
        },
        {
          provide: 'second',
          useFactory: (first: unknown) => ({ first }),
          inject: ['first'],
        },
      ],
    }).compile(),
    /circular dependency/i,
  )
  const repaired = await Test.createTestingModule({
    providers: [
      { provide: 'shared-port', useValue: {} },
      {
        provide: 'first',
        useFactory: (port: unknown) => ({ port }),
        inject: ['shared-port'],
      },
      {
        provide: 'second',
        useFactory: (port: unknown) => ({ port }),
        inject: ['shared-port'],
      },
    ],
  }).compile()
  try {
    assert.equal(repaired.get('first').port, repaired.get('second').port)
  } finally {
    await repaired.close()
  }
})
