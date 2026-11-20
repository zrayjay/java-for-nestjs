<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  total: number
  size: number
  pending: boolean
}>()
const emit = defineEmits<{ change: [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <footer class="paging">
    <span>{{ total }} 条记录</span>
    <button
      class="secondary"
      type="button"
      :disabled="page <= 1 || pending"
      @click="emit('change', page - 1)"
    >
      上一页
    </button>
    <span>{{ page }} / {{ pages }}</span>
    <button
      class="secondary"
      type="button"
      :disabled="page >= pages || pending"
      @click="emit('change', page + 1)"
    >
      下一页
    </button>
  </footer>
</template>

<style scoped>
.paging {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1rem;
  flex-wrap: wrap;
}
.paging span:first-child {
  margin-right: auto;
}
</style>
