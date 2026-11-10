import { Worker } from 'node:worker_threads'

export function mix(value: number): number {
  return (Math.imul(value, 31) + 7) | 0
}

export function checksum(count: number): number {
  let sum = 0
  for (let i = 0; i < count; i++) sum = (sum + mix(i)) | 0
  return sum
}

export async function eventLoopOrder(): Promise<string[]> {
  const events = ['sync-start']
  const timer = new Promise<void>((resolve) => {
    setTimeout(() => {
      events.push('timer')
      resolve()
    }, 0)
  })
  checksum(100_000)
  events.push('cpu-finished')
  await timer
  return events
}

export function workerChecksum(count: number): Promise<number> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(
      `const { parentPort, workerData } = require('node:worker_threads');
       let sum = 0;
       for (let i = 0; i < workerData; i++) sum = (sum + Math.imul(i, 31) + 7) | 0;
       parentPort.postMessage(sum);`,
      { eval: true, workerData: count },
    )
    let received = false
    worker.once('message', (value: number) => {
      received = true
      resolve(value)
    })
    worker.once('error', reject)
    worker.once('exit', (code) => {
      if (code !== 0 || !received) reject(new Error(`Worker exited: ${code}`))
    })
  })
}

if (require.main === module) {
  Promise.all([eventLoopOrder(), workerChecksum(100_000)]).then(
    ([events, worker]) =>
      console.log(
        JSON.stringify({ checksum: checksum(100_000), events, worker }),
      ),
    (error: unknown) => {
      console.error(error)
      process.exitCode = 1
    },
  )
}
