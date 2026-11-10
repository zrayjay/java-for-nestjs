import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const java = process.env.JAVA_HOME
  ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  : 'java'
const executables = [
  { name: 'Java CLI', command: java, prefix: ['-jar', 'examples/java-basics/greeting-cli/target/greeting-cli-0.1.0-SNAPSHOT.jar'] },
  { name: 'Spring context', command: java, prefix: ['-jar', 'examples/spring-boot-examples/target/spring-boot-examples-0.1.0-SNAPSHOT.jar'] },
  { name: 'Nest context', command: process.execPath, prefix: ['examples/nestjs-reference/dist/main.js'] },
]
const cases = [
  { args: [], output: 'Hello, NestJS!' },
  { args: ['NestJS'], output: 'Hello, NestJS!' },
  { args: ['reader_21'], output: 'Hello, reader_21!' },
  { args: [''], output: null },
  { args: ['1reader'], output: null },
  { args: ['bad name'], output: null },
  { args: ['reader\n'], output: null },
  { args: ['读者'], output: null },
  { args: ['abcdefghijklmnopqrstuvwxyzABCDEFG'], output: null },
]

for (const executable of executables) {
  if (!existsSync(join(root, executable.prefix.at(-1)))) {
    throw new Error(`${executable.name}未构建，请先运行Maven verify和pnpm examples:build。`)
  }
  for (const fixture of cases) {
    const result = spawnSync(executable.command, [...executable.prefix, ...fixture.args], {
      cwd: root, encoding: 'utf8', timeout: 30000,
    })
    if (result.error || result.signal || result.status === null) {
      throw new Error(`${executable.name}进程异常：${result.error?.message || result.signal}`)
    }
    const passed = fixture.output === null
      ? result.status !== 0 && result.stderr.includes('Name must be')
      : result.status === 0 && result.stdout.trim() === fixture.output
    if (!passed) {
      throw new Error(`${executable.name}输入${JSON.stringify(fixture.args)}不符合契约：exit=${result.status}\n${result.stdout}\n${result.stderr}`)
    }
  }
}
console.log(`通过：${executables.length}个独立运行物 × ${cases.length}项共同契约。`)
