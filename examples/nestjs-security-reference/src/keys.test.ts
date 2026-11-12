import assert from 'node:assert/strict'
import { test } from 'node:test'
import { generateKeyPair } from 'jose'
import { loadKeys, validateKeys } from './keys.js'

test('PEM paths are explicit with no ephemeral fallback', async () => {
  await assert.rejects(loadKeys(undefined, undefined), /Explicit JWT PEM paths/)
})
test('RSA signing and verification validate the actual key pair', async () => {
  const keys = await generateKeyPair('RS256', { modulusLength: 2048 })
  assert.equal((await validateKeys(keys)).publicKey, keys.publicKey)
})
test('mismatched RSA keys fail verification', async () => {
  const first = await generateKeyPair('RS256', { modulusLength: 2048 })
  const second = await generateKeyPair('RS256', { modulusLength: 2048 })
  await assert.rejects(
    validateKeys({ privateKey: first.privateKey, publicKey: second.publicKey }),
  )
})
