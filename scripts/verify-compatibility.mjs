import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { loadMap } from './lib/chapter-map.mjs'

export async function verifyCompatibility(root) {
  const { baseline: b } = await loadMap(root)
  const read = (path) => readFile(resolve(root, path), 'utf8')
  const pkg = JSON.parse(await read('package.json'))
  const pom = await read('pom.xml')
  const properties = await read('.mvn/wrapper/maven-wrapper.properties')
  const matrix = await read('docs/00-introduction/compatibility-matrix.md')
  const workflows = await Promise.all(
    ['docs', 'foundation'].map((n) => read(`.github/workflows/${n}.yml`)),
  )
  const actual = {
    java: pom.match(/<java.version>([^<]+)</)?.[1],
    springBoot: pom.match(/<parent>[\s\S]*?<version>([^<]+)</)?.[1],
    node: workflows[0].match(/node-version: ([\d.]+)/)?.[1],
    pnpm: pkg.packageManager.split('@')[1],
    vitepress: pkg.devDependencies.vitepress,
    vite: pkg.pnpm.overrides.vite,
    lodashEs: pkg.pnpm.overrides['lodash-es'],
    maven: properties.match(/apache-maven-([\d.]+)-bin/)?.[1],
  }
  for (const [name, value] of Object.entries(actual)) {
    if (!value || b?.[name] !== value)
      throw new Error(`版本漂移 ${name}: map=${b?.[name]}, source=${value}`)
    if (!matrix.includes(value))
      throw new Error(`兼容矩阵未记录 ${name} ${value}`)
  }
  if (
    workflows.some(
      (s) =>
        !s.includes(`node-version: ${b.node}`) || !s.includes(`pnpm@${b.pnpm}`),
    )
  )
    throw new Error('CI Node/pnpm版本漂移')
  return actual
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
)
  console.log(
    '版本映射一致',
    await verifyCompatibility(fileURLToPath(new URL('../', import.meta.url))),
  )
