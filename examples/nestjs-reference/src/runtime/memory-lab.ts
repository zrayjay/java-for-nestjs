export function observeMemory(): {
  primitive: number
  shared: number
  sameReference: boolean
  sameText: boolean
} {
  const primitive = 1
  const original = { value: 1 }
  const alias = original
  const change = (copied: number, box: { value: number }): void => {
    copied = 99
    box.value = 2
    box = { value: 99 }
    // Read the local assignments so this negative example stays explicit.
    if (copied !== box.value) throw new Error('unexpected local state')
  }
  change(primitive, alias)
  const first = { text: 'ok' }
  const second = { text: 'ok' }
  return {
    primitive,
    shared: original.value,
    sameReference: first === second,
    sameText: first.text === second.text,
  }
}
