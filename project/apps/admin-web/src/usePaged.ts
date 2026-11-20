import { shallowRef, watch } from 'vue'

export function usePaged<T>(
  read: (page: number, size: number) => Promise<{ items: T[]; total: number }>,
  size = 20,
) {
  const page = shallowRef(1)
  const items = shallowRef<T[]>([])
  const total = shallowRef(0)
  const busy = shallowRef(false)
  const failure = shallowRef<unknown>()
  let generation = 0

  async function load() {
    const current = ++generation
    busy.value = true
    failure.value = undefined
    try {
      const result = await read(page.value, size)
      if (current !== generation) return
      items.value = result.items
      total.value = result.total
    } catch (error) {
      if (current !== generation) return
      items.value = []
      total.value = 0
      failure.value = error
    } finally {
      if (current === generation) busy.value = false
    }
  }

  watch(page, load, { immediate: true })
  return { page, items, total, busy, failure, size, load }
}
