export interface Named {
  name(): string
}

export function nameOf(value: Named): string {
  return value.name()
}

export type Payment =
  { kind: 'card'; lastFour: string } | { kind: 'cash'; units: number }

export function describe(payment: Payment): string {
  switch (payment.kind) {
    case 'card':
      return `card:${payment.lastFour}`
    case 'cash':
      return `cash:${payment.units}`
    default: {
      const exhaustive: never = payment
      return exhaustive
    }
  }
}

export type Account = Readonly<{ id: string; roles: readonly string[] }>

export function account(id: string, roles: readonly string[]): Account {
  if (!id.trim()) throw new Error('id required')
  return Object.freeze({ id, roles: Object.freeze([...roles]) })
}

export function total(amounts: readonly number[]): number {
  return amounts
    .filter((value) => value >= 10)
    .map((value) => value * 2)
    .reduce((sum, value) => sum + value, 0)
}

export function find(
  accounts: readonly Account[],
  id: string,
): string | undefined {
  return accounts.find((value) => value.id === id)?.id
}

export function closeTrace(bodyFails: boolean, closeFails: boolean): string[] {
  const trace: string[] = []
  try {
    trace.push('body')
    if (bodyFails) throw new Error('body-failure')
  } finally {
    trace.push('close')
    if (closeFails) throw new Error('close-failure')
  }
  return trace
}

const tags = new WeakMap<Function, string>()
function tag(value: string): ClassDecorator {
  return (target) => {
    tags.set(target, value)
  }
}

@tag('risk')
export class TaggedRule {}
export function readTag(target: Function): string | undefined {
  return tags.get(target)
}

export async function promiseTasks(): Promise<{
  values: number[]
  events: string[]
}> {
  const events: string[] = []
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const task = async (id: number, value: number): Promise<number> => {
    events.push(`start:${id}`)
    await gate
    events.push(`finish:${id}`)
    return value
  }
  const first = task(1, 10)
  const second = task(2, 20)
  if (events.join(',') !== 'start:1,start:2')
    throw new Error('unexpected admission')
  release()
  const values = await Promise.all([first, second])
  return { values, events }
}

// Never called: the compiler must reject this assignment under strictNullChecks.
function nullableCompileFixture(): void {
  // @ts-expect-error null is not a number under this project's strict configuration.
  const value: number = null
  void value
}
void nullableCompileFixture
