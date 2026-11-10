export function copyValues<T>(source: readonly T[], destination: T[]): void {
  for (const value of source) destination.push(value)
}

export function maxValue<T extends { value: number }>(left: T, right: T): T {
  return left.value >= right.value ? left : right
}

export function isNumberArray(value: unknown): value is number[] {
  return (
    Array.isArray(value) &&
    value.every((item: unknown) => typeof item === 'number')
  )
}

// Compile-only expected errors; never invoke this teaching fixture at runtime.
export function compileOnlyErrors(): void {
  const destination: number[] = []
  // @ts-expect-error The explicit number type argument rejects strings.
  copyValues<number>(['wrong'], destination)
  // @ts-expect-error The structural constraint requires a numeric value.
  maxValue({ value: 'wrong' }, { value: 'also wrong' })
}
