import { readFile } from 'node:fs/promises'
import { CompactSign, compactVerify, importPKCS8, importSPKI } from 'jose'

export interface SigningKeys {
  publicKey: CryptoKey
  privateKey: CryptoKey
}
export async function validateKeys(keys: SigningKeys): Promise<SigningKeys> {
  if ((keys.publicKey.algorithm as RsaHashedKeyAlgorithm).modulusLength < 2048)
    throw new Error('RSA2048 or stronger is required')
  const signed = await new CompactSign(
    new TextEncoder().encode('teaching-key-pair-check'),
  )
    .setProtectedHeader({ alg: 'RS256' })
    .sign(keys.privateKey)
  await compactVerify(signed, keys.publicKey, { algorithms: ['RS256'] })
  return keys
}
export async function loadKeys(
  privatePath: string | undefined,
  publicPath: string | undefined,
): Promise<SigningKeys> {
  if (!privatePath || !publicPath)
    throw new Error('Explicit JWT PEM paths are required')
  return validateKeys({
    privateKey: await importPKCS8(await readFile(privatePath, 'utf8'), 'RS256'),
    publicKey: await importSPKI(await readFile(publicPath, 'utf8'), 'RS256'),
  })
}
