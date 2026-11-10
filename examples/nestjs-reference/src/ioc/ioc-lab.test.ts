import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ContextIdFactory } from '@nestjs/core'
import { Test } from '@nestjs/testing'
import {
  Greeter,
  Holder,
  IocGreetingModule,
  LifecycleProbe,
  MissingExportModule,
  RequestToken,
  ScopeModule,
  TransientToken,
} from './ioc-lab'

test('exported provider is injected and the default consumer is shared', async (t) => {
  const module = await Test.createTestingModule({
    imports: [IocGreetingModule],
  }).compile()
  t.after(() => module.close())
  assert.equal(module.get(Greeter).greet(), 'Hello @ 09:00')
  assert.equal(module.get(Greeter), module.get(Greeter))
})

test('importing a module does not expose a provider that it did not export', async () => {
  await assert.rejects(
    Test.createTestingModule({ imports: [MissingExportModule] }).compile(),
    /can't resolve dependencies/,
  )
})

test('scoped resolution uses explicit context IDs and singleton injection is retained', async (t) => {
  const module = await Test.createTestingModule({
    imports: [ScopeModule],
  }).compile()
  t.after(() => module.close())
  const holder = module.get(Holder)
  assert.equal(holder.token, module.get(Holder).token)
  assert.notEqual(
    await module.resolve(TransientToken),
    await module.resolve(TransientToken),
  )
  assert.throws(() => module.get(RequestToken), /scoped provider/)
  const context = ContextIdFactory.create()
  assert.equal(
    await module.resolve(RequestToken, context),
    await module.resolve(RequestToken, context),
  )
  assert.notEqual(
    await module.resolve(RequestToken, context),
    await module.resolve(RequestToken, ContextIdFactory.create()),
  )
})

test('request objects do not receive application destruction hooks', async () => {
  const module = await Test.createTestingModule({
    imports: [ScopeModule],
  }).compile()
  const token = await module.resolve(RequestToken, ContextIdFactory.create())
  try {
    await module.init()
  } finally {
    await module.close()
  }
  assert.equal(token.closed, false)
})

test('singleton hooks follow initialization and explicit close lifecycle', async () => {
  const module = await Test.createTestingModule({
    providers: [LifecycleProbe],
  }).compile()
  const probe = module.get(LifecycleProbe)
  try {
    assert.deepEqual(probe.events, ['constructed'])
    await module.init()
    assert.deepEqual(probe.events, ['constructed', 'module-init', 'bootstrap'])
  } finally {
    await module.close()
  }
  assert.deepEqual(probe.events, [
    'constructed',
    'module-init',
    'bootstrap',
    'module-destroy',
    'before-shutdown',
    'shutdown',
  ])
})
