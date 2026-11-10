import { readdir } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

async function files(directory) {
  const result = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) result.push(...(await files(path)))
    else if (entry.name.endsWith('.mjs')) result.push(path)
  }
  return result
}

const scripts = await files(fileURLToPath(new URL('./', import.meta.url)))
for (const path of scripts) {
  const result = spawnSync(process.execPath, ['--check', path], {
    encoding: 'utf8',
  })
  if (result.status !== 0) {
    console.error(result.stderr || result.error?.message)
    process.exitCode = 1
  }
}
if (!process.exitCode)
  console.log(`通过：${scripts.length}个Node脚本语法检查。`)
