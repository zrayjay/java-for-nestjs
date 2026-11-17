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
  const filePom = await read('examples/file-lab/pom.xml')
  const securityPackage = JSON.parse(
    await read('examples/nestjs-security-reference/package.json'),
  )
  const security = securityPackage.dependencies
  const messagePackage = JSON.parse(
    await read('examples/nestjs-message-reference/package.json'),
  )
  const messageWorkflow = await read('.github/workflows/messaging.yml')
  const messagePom = await read('examples/messaging-lab/pom.xml')
  const containerFile = await read('project/deploy/compose/Dockerfile')
  const containerCompose = await read('project/deploy/compose/teaching.yaml')
  const containerWorkflow = await read('.github/workflows/containers.yml')
  const k8sManifest = await read('project/deploy/k8s/teaching.yaml')
  const k8sWorkflow = await read('.github/workflows/k8s.yml')
  const temurinImage = containerFile.match(
    /FROM eclipse-temurin:([\d._]+)-jdk-noble/,
  )?.[1]
  if (
    !containerFile.includes(`FROM eclipse-temurin:${temurinImage}-jre-noble`) ||
    containerCompose.match(/image:\s+mysql:([\d.]+)/)?.[1] !== b.mysql ||
    !containerWorkflow.includes(`node-version: ${b.node}`)
  )
    throw new Error('容器JDK/JRE/MySQL/CI Node版本漂移')
  if (
    k8sManifest.match(/image:\s+mysql:([\d.]+)/)?.[1] !== b.mysql ||
    !k8sManifest.includes(
      'jdbc:mysql://mysql:3306/data_lab?allowPublicKeyRetrieval=true&useSSL=false',
    )
  )
    throw new Error('K8s MySQL/守卫URL版本漂移')
  if (
    k8sWorkflow.match(/download\/v([\d.]+)\/kind-linux-amd64/)?.[1] !==
      b.kind ||
    k8sWorkflow.match(/kindest\/node:v([\d.]+)/)?.[1] !== b.kindNode ||
    k8sWorkflow.match(/node-version: ([\d.]+)/)?.[1] !== b.node
  )
    throw new Error('K8s kind/节点/CI Node版本漂移')
  if (
    messagePom.match(/<flyway.version>([^<]+)</)?.[1] !== b.flyway ||
    messagePackage.dependencies.mariadb !== b.mariadb ||
    messageWorkflow.match(/image:\s+mysql:([\d.]+)/)?.[1] !== b.mysql
  )
    throw new Error('消息实验数据库版本漂移')
  if (securityPom.match(/<flyway.version>([^<]+)</)?.[1] !== b.flyway)
    throw new Error('安全实验Flyway版本漂移')
  if (security['@opentelemetry/context-async-hooks'] !== b.otelCore)
    throw new Error('Node追踪上下文版本漂移')
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
      'files',
      'unit-tests',
      'messaging',
    ].map((n) => read(`.github/workflows/${n}.yml`)),
  )
  const actual = {
    temurinImage,
    rabbitmq: messageWorkflow.match(
      /image:\s+rabbitmq:([\d.]+)-management/,
    )?.[1],
    amqplib: messagePackage.dependencies.amqplib,
    amqplibTypes: messagePackage.devDependencies['@types/amqplib'],
    otelApi: security['@opentelemetry/api'],
    otelNodeSdk: security['@opentelemetry/sdk-trace'],
    otelCore: security['@opentelemetry/core'],
    pino: security.pino,
    vitest: securityPackage.devDependencies.vitest,
    minio: filePom.match(
      /<artifactId>minio<\/artifactId><version>([^<]+)</,
    )?.[1],
    okhttpJvm: filePom.match(
      /<artifactId>okhttp-jvm<\/artifactId><version>([^<]+)</,
    )?.[1],
    s3Sdk: security['@aws-sdk/client-s3'],
    pngjs: security.pngjs,
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
