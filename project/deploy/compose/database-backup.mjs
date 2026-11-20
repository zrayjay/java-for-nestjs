import { execFile, spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream, createWriteStream } from 'node:fs'
import {
  mkdir,
  mkdtemp,
  readFile,
  rmdir,
  unlink,
  writeFile,
} from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const [operation, project, envFile, archive, ...extra] = process.argv.slice(2)
if (
  !['backup', 'restore'].includes(operation) ||
  !/^[a-z0-9][a-z0-9_-]*$/.test(project ?? '') ||
  !envFile ||
  !archive ||
  extra.length
) {
  console.error(
    'Usage: node database-backup.mjs <backup|restore> <compose-project> <env-file> <archive-directory>',
  )
  process.exit(2)
}

const compose = [
  'compose',
  '-p',
  project,
  '--env-file',
  resolve(envFile),
  '-f',
  fileURLToPath(new URL('./capstone.yaml', import.meta.url)),
]
const snapshot = join(archive, 'capstone.sql')
const checksum = join(archive, 'capstone.sha256')
const run = promisify(execFile)

function mysql(tool, stream) {
  const command =
    tool === 'dump'
      ? 'mysqldump -ucapstone --single-transaction --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table capstone'
      : 'mysql -ucapstone --binary-mode capstone'
  const child = spawn(
    'docker',
    [
      ...compose,
      'exec',
      '-T',
      'mysql',
      'sh',
      '-ec',
      'export MYSQL_PWD="$MYSQL_PASSWORD"; exec ' + command,
    ],
    { stdio: stream },
  )
  const done = new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('close', (code) => {
      if (code === 0) resolve()
      else
        reject(
          new Error(
            tool === 'restore'
              ? 'MySQL restore failed. Verify that the target schema is empty and the restore lock is free.'
              : 'MySQL dump exited unsuccessfully',
          ),
        )
    })
  })
  return { child, done }
}

async function digest(path) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(path)) hash.update(chunk)
  return hash.digest('hex')
}

async function backup() {
  await mkdir(archive, { mode: 0o700 })
  const { child, done } = mysql('dump', ['ignore', 'pipe', 'inherit'])
  await Promise.all([
    pipeline(
      child.stdout,
      createWriteStream(snapshot, { flags: 'wx', mode: 0o600 }),
    ),
    done,
  ])
  await writeFile(checksum, (await digest(snapshot)) + '\n', {
    flag: 'wx',
    mode: 0o600,
  })
}

async function* restoreInput(staged) {
  // Keep the advisory lock and empty-schema check in the import connection.
  // PREPARE rejects the guard statement in the non-empty or locked case.
  yield `
SET @capstone_restore_ok = GET_LOCK('capstone-logical-restore', 0) = 1 AND
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()) = 0;
SET @capstone_restore_guard = IF(@capstone_restore_ok, 'DO 0', 'RESTORE_TARGET_MUST_BE_EMPTY_AND_UNLOCKED');
PREPARE capstone_restore_guard FROM @capstone_restore_guard;
EXECUTE capstone_restore_guard;
DEALLOCATE PREPARE capstone_restore_guard;
`
  yield* createReadStream(staged)
  yield `
START TRANSACTION;
DELETE FROM sys_refresh_session;
DELETE FROM sys_session_family;
COMMIT;
`
}

async function restore() {
  const expected = (await readFile(checksum, 'utf8')).trim()
  if (!/^[a-f0-9]{64}$/.test(expected))
    throw new Error('Invalid backup checksum file')
  const scratch = await mkdtemp(join(tmpdir(), 'capstone-restore-'))
  const staged = join(scratch, 'capstone.sql')
  try {
    await pipeline(
      createReadStream(snapshot),
      createWriteStream(staged, { flags: 'wx', mode: 0o600 }),
    )
    if ((await digest(staged)) !== expected)
      throw new Error('Backup checksum mismatch')
    const status = await run('docker', [
      ...compose,
      'ps',
      '--status',
      'running',
      '--services',
      'app',
    ])
    if (status.stdout.trim()) throw new Error('Stop the app before restoring')
    // MySQL errors can quote values from the archive.
    const { child, done } = mysql('restore', ['pipe', 'ignore', 'ignore'])
    const results = await Promise.allSettled([
      done,
      pipeline(Readable.from(restoreInput(staged)), child.stdin),
    ])
    for (const result of results)
      if (result.status === 'rejected') throw result.reason
  } finally {
    await unlink(staged).catch((error) => {
      if (error.code !== 'ENOENT') throw error
    })
    await rmdir(scratch)
  }
}

try {
  if (operation === 'backup') await backup()
  else await restore()
  console.log('Capstone database ' + operation + ' completed')
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
