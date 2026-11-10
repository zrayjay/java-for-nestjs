export type OrderLine = Readonly<{
  id: string
  customer: string
  quantity: number
}>

export function summarize(lines: readonly OrderLine[]): string[] {
  const ids = new Set<string>()
  const totals = new Map<string, number>()
  for (const line of lines) {
    if (
      typeof line.id !== 'string' ||
      typeof line.customer !== 'string' ||
      !/^[a-z][a-z0-9-]{0,15}$/.test(line.id) ||
      !/^[a-z][a-z0-9-]{0,15}$/.test(line.customer) ||
      !Number.isInteger(line.quantity) ||
      line.quantity < 0 ||
      line.quantity > 2147483647
    ) {
      throw new Error('invalid order line')
    }
    if (ids.has(line.id)) throw new Error(`duplicate order id: ${line.id}`)
    ids.add(line.id)
    const total = (totals.get(line.customer) ?? 0) + line.quantity
    if (total > 2147483647) throw new Error('quantity overflow')
    totals.set(line.customer, total)
  }
  return [...totals]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([customer, quantity]) => `${customer}:${quantity}`)
}
