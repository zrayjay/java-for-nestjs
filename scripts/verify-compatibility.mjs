import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { loadMap } from './lib/chapter-map.mjs'

export async function verifyCompatibility(root) {
  const { baseline: b } = await loadMap(root)
  const read = (path) => readFile(resolve(root, path), 'utf8')
  const pkg = JSON.parse(await read('package.json'))
  const reference = JSON.parse(
    await read('examples/prisma-reference/package.json'),
  )
  const transactionPom = await read('examples/transaction-lab/pom.xml')
  const dataPom = await read('examples/data-access-lab/pom.xml')
  const webPom = await read('examples/spring-web-lab/pom.xml')
  const securityPom = await read('examples/security-lab/pom.xml')
  const security = JSON.parse(
    await read('examples/nestjs-security-reference/package.json'),
  ).dependencies
  if (securityPom.match(/<flyway.version>([^<]+)</)?.[1] !== b.flyway)
    throw new Error('安全实验Flyway版本漂移')
  if (
    security['@nestjs/common'] !== b.nest ||
    security['@nestjs/core'] !== b.nest ||
    security['class-validator'] !== b.classValidator
  )
    throw new Error('安全参照Nest/校验基线漂移')
  if (dataPom.match(/<flyway.version>([^<]+)</)?.[1] !== b.flyway)
    throw new Error('数据实验Flyway版本漂移')
  const nest = JSON.parse(
    await read('examples/nestjs-reference/package.json'),
  ).dependencies
  if (
    nest['@nestjs/platform-express'] !== nest['@nestjs/core'] ||
    nest['@nestjs/common'] !== nest['@nestjs/core']
  )
    throw new Error('Nest平台/core/common版本漂移')
  const pom = await read('pom.xml')
  const properties = await read('.mvn/wrapper/maven-wrapper.properties')
  const matrix = await read('docs/00-introduction/compatibility-matrix.md')
  const workflows = await Promise.all(
    [
      'docs',
      'foundation',
      'transactions',
      'data-access',
      'security',
      'cache',
    ].map((n) => read(`.github/workflows/${n}.yml`)),
  )
  const actual = {
    ioredis: security.ioredis,
    redis: workflows[5].match(/image:\s+redis:([\d.]+)/)?.[1],
    jose: security.jose,
    bcryptjs: security.bcryptjs,
    mybatisPlus: dataPom.match(/<mybatis-plus.version>([^<]+)</)?.[1],
    springdoc: webPom.match(
      /<artifactId>springdoc-openapi-starter-webmvc-api<\/artifactId><version>([^<]+)</,
    )?.[1],
    nest: nest['@nestjs/core'],
    nestSwagger: nest['@nestjs/swagger'],
    classValidator: nest['class-validator'],
    classTransformer: nest['class-transformer'],
    java: pom.match(/<java.version>([^<]+)</)?.[1],
    springBoot: pom.match(/<parent>[\s\S]*?<version>([^<]+)</)?.[1],
    node: workflows[0].match(/node-version: ([\d.]+)/)?.[1],
    pnpm: pkg.packageManager.split('@')[1],
    vitepress: pkg.devDependencies.vitepress,
    vite: pkg.pnpm.overrides.vite,
    lodashEs: pkg.pnpm.overrides['lodash-es'],
    maven: properties.match(/apache-maven-([\d.]+)-bin/)?.[1],
    gradle: workflows[1].match(/gradle-version:\s*([\d.]+)/)?.[1],
    prisma: reference.devDependencies.prisma,
    prismaClient: reference.dependencies['@prisma/client'],
    prismaAdapter: reference.dependencies['@prisma/adapter-mariadb'],
    mariadb: reference.dependencies.mariadb,
    flyway: transactionPom.match(/<flyway.version>([^<]+)</)?.[1],
    mysql: workflows[2].match(/image:\s+mysql:([\d.]+)/)?.[1],
  }
  if (workflows[3].match(/image:\s+mysql:([\d.]+)/)?.[1] !== b.mysql)
    throw new Error('数据实验MySQL版本漂移')
  if (workflows[4].match(/image:\s+mysql:([\d.]+)/)?.[1] !== b.mysql)
    throw new Error('安全实验MySQL版本漂移')
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
