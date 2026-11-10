<script setup lang="ts">
import { onMounted, onUnmounted, shallowRef, useId, watch } from 'vue'
import { useData } from 'vitepress'

const props = defineProps<{ code: string }>()
const { isDark } = useData()
const svg = shallowRef('')
const error = shallowRef(false)
const id = `diagram-${useId().replace(/[^a-zA-Z0-9-]/g, '')}`
let generation = 0
let stop: (() => void) | undefined

onMounted(() => {
  stop = watch(
    [() => props.code, isDark],
    async () => {
      const current = ++generation
      error.value = false
      try {
        const { default: mermaid } = await import('mermaid')
        if (current !== generation) return
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: isDark.value ? 'dark' : 'neutral',
          fontFamily: 'Microsoft YaHei, sans-serif',
        })
        const result = await mermaid.render(`${id}-${current}`, props.code)
        if (current === generation) svg.value = result.svg
      } catch {
        if (current === generation) {
          svg.value = ''
          error.value = true
        }
      }
    },
    { immediate: true },
  )
})

onUnmounted(() => {
  generation++
  stop?.()
})
</script>

<template>
  <figure class="mermaid-diagram">
    <!-- SVG is produced by Mermaid's strict renderer from repository-authored diagrams. -->
    <div
      v-if="svg"
      class="diagram-image"
      role="img"
      aria-label="流程图，下方可查看文字源码"
      v-html="svg"
    />
    <p v-else class="diagram-message" role="status">
      {{ error ? '流程图暂不可用，请查看下方源码。' : '正在绘制流程图…' }}
    </p>
    <details class="diagram-source">
      <summary>查看图表源码</summary>
      <pre><code>{{ code }}</code></pre>
    </details>
  </figure>
</template>

<style scoped>
.mermaid-diagram {
  margin: 24px 0;
  padding: 20px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg);
}
.diagram-image {
  overflow-x: auto;
  text-align: center;
}
.diagram-image :deep(svg) {
  max-width: 100%;
  height: auto;
  margin: 0 auto;
}
.diagram-message {
  color: var(--vp-c-text-2);
  font-size: 13px;
}
.diagram-source {
  margin-top: 16px;
  border-top: 1px solid var(--vp-c-divider);
  padding-top: 12px;
  font-size: 12px;
  color: var(--vp-c-text-2);
}
.diagram-source summary {
  cursor: pointer;
}
.diagram-source pre {
  overflow-x: auto;
  padding: 12px 0 0;
  white-space: pre;
}
</style>
