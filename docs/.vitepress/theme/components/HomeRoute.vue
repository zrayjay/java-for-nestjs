<script setup lang="ts">
import { withBase } from 'vitepress'
import catalog from '../../course.json'
</script>

<template>
  <section class="home-route" aria-labelledby="route-title">
    <div class="route-heading">
      <div><p class="route-label">LEARNING PATH</p><h2 id="route-title">一条完整的后端学习路线</h2></div>
      <p class="route-summary">9 个部分 · 45 章计划<br>从运行时直觉，到可复现的工程交付</p>
    </div>
    <ol class="route-parts">
      <li v-for="part in catalog" :key="part.number">
        <a :href="withBase(`/course/#part-${part.number}`)">
          <span class="part-number">{{ part.number }}</span>
          <span class="part-title">{{ part.title }}</span>
          <span class="part-count">{{ part.chapters.length }} 章 <span aria-hidden="true">↗</span></span>
        </a>
      </li>
    </ol>
    <div class="route-next">
      <p><strong>先看机制，再亲手验证。</strong> 正文交付前，可以运行集成探针，了解已验证的技术组合。</p>
      <a :href="withBase('/00-introduction/run-probe')">运行兼容探针 <span aria-hidden="true">→</span></a>
    </div>
  </section>
</template>

<style scoped>
.home-route { padding: 38px 0 76px; border-top: 1px solid var(--vp-c-divider); }
.route-heading { display: flex; justify-content: space-between; align-items: end; gap: 28px; margin-bottom: 28px; }
.route-label { margin: 0 0 10px; color: var(--vp-c-brand-1); font-family: var(--vp-font-family-mono); font-size: 10px; letter-spacing: .16em; }
.route-heading h2 { margin: 0; color: var(--vp-c-text-1); font-family: var(--course-heading-font); font-size: 27px; line-height: 1.5; font-weight: 600; letter-spacing: -.02em; }
.route-summary { margin: 0; font-size: 12px; line-height: 1.9; color: var(--vp-c-text-3); text-align: right; }
.route-parts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 32px; list-style: none; margin: 0; padding: 0; }
.route-parts li { min-width: 0; border-bottom: 1px solid var(--vp-c-divider); }
.route-parts a { display: flex; align-items: center; gap: 14px; min-height: 70px; text-decoration: none; color: var(--vp-c-text-1); }
.route-parts a:hover .part-title { color: var(--vp-c-brand-1); }
.part-number { font-family: var(--vp-font-family-mono); font-size: 11px; color: var(--vp-c-text-3); }
.part-title { font-size: 13px; font-weight: 500; }
.part-count { display: flex; gap: 12px; margin-left: auto; flex-shrink: 0; color: var(--vp-c-text-3); font-size: 10px; }
.route-next { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-top: 36px; padding: 22px 24px; border-left: 2px solid var(--vp-c-brand-1); background: var(--vp-c-bg-soft); }
.route-next p { margin: 0; color: var(--vp-c-text-2); font-size: 12px; line-height: 1.9; }
.route-next strong { color: var(--vp-c-text-1); font-weight: 600; }
.route-next a { white-space: nowrap; color: var(--vp-c-brand-1); text-decoration: none; font-weight: 600; font-size: 12px; }
@media (max-width: 960px) { .route-parts { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .route-heading { display: block; } .route-summary { text-align: left; margin-top: 14px; } .route-parts { grid-template-columns: 1fr; } .route-next { align-items: start; flex-direction: column; gap: 14px; padding: 18px; } .home-route { padding-bottom: 44px; } .route-heading h2 { font-size: 24px; } }
</style>
