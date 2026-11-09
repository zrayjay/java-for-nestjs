<script setup lang="ts">
import { withBase } from 'vitepress'
</script>

<template>
  <section class="home-hero" aria-labelledby="course-title">
    <div class="hero-copy">
      <p class="eyebrow"><span class="status-dot" /> 为 NestJS 开发者编写 · 持续建设中</p>
      <h1 id="course-title">带着 NestJS 的经验，<br>走进 <em>Java</em> 的世界。</h1>
      <p class="hero-description">从类型、运行时到事务与工程交付。用熟悉的概念找到入口，理解每一个类比的边界，亲手验证真正的机制。</p>
      <div class="hero-actions">
        <a class="action-primary" :href="withBase('/00-introduction/learning-guide')">阅读学习指南 <span aria-hidden="true">↗</span></a>
        <a class="action-secondary" :href="withBase('/course/')">浏览 45 章目录 <span aria-hidden="true">→</span></a>
      </div>
      <p class="hero-note">课程正文正在建设。现在可以了解学习路线、技术基线，并运行兼容探针。</p>
    </div>
    <div class="migration-map" aria-label="技术概念迁移示意，映射均有边界">
      <div class="map-header"><span>认知迁移地图</span><span class="map-tag">NEST → JAVA</span></div>
      <div class="map-labels"><span>从熟悉的概念</span><span>到新的机制</span></div>
      <div class="map-row"><span>TypeScript</span><span aria-hidden="true">→</span><strong>Java 类型系统</strong></div>
      <div class="map-row"><span>Nest Provider</span><span aria-hidden="true">→</span><strong>Spring Bean</strong></div>
      <div class="map-row"><span>Prisma 事务</span><span aria-hidden="true">→</span><strong>Spring 事务代理</strong></div>
      <div class="map-row"><span>Node.js / V8</span><span aria-hidden="true">→</span><strong>JVM 运行时</strong></div>
      <p class="map-boundary"><span aria-hidden="true">↳</span> 相似概念只是入口，机制差异需要逐一验证。</p>
    </div>
  </section>
</template>

<style scoped>
.home-hero { display: grid; grid-template-columns: 1.35fr 1fr; gap: 68px; align-items: center; padding: 86px 0 60px; }
.eyebrow { display: flex; align-items: center; gap: 9px; margin: 0 0 24px; font-size: 12px; font-weight: 600; letter-spacing: .06em; color: var(--vp-c-brand-1); }
.status-dot { height: 6px; width: 6px; border-radius: 50%; background: currentColor; }
.hero-copy h1 { margin: 0; color: var(--vp-c-text-1); font-family: var(--course-heading-font); font-size: clamp(32px, 3.5vw, 48px); font-weight: 700; letter-spacing: -.04em; line-height: 1.45; }
.hero-copy em { font-style: normal; color: var(--vp-c-brand-1); }
.hero-description { margin: 24px 0 28px; max-width: 490px; color: var(--vp-c-text-2); font-size: 16px; line-height: 1.95; }
.hero-actions { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.action-primary, .action-secondary { display: inline-flex; gap: 20px; align-items: center; min-height: 44px; font-size: 14px; font-weight: 600; text-decoration: none; }
.action-primary { padding: 0 20px; background: var(--vp-c-brand-1); color: var(--course-button-text); border-radius: 5px; }
.action-primary:hover { background: var(--vp-c-brand-2); }
.action-secondary { color: var(--vp-c-text-1); }
.action-secondary:hover { color: var(--vp-c-brand-1); }
.hero-note { margin-top: 22px; max-width: 450px; color: var(--vp-c-text-3); font-size: 12px; line-height: 1.8; }
.migration-map { padding: 27px; border: 1px solid var(--vp-c-divider); border-radius: 8px; background: var(--course-map-bg); box-shadow: 7px 7px 0 var(--course-map-shadow); transform: translateY(4px); }
.map-header { display: flex; justify-content: space-between; gap: 12px; align-items: center; padding-bottom: 22px; color: var(--vp-c-text-1); font-size: 13px; font-weight: 600; }
.map-tag { color: var(--vp-c-brand-1); font-family: var(--vp-font-family-mono); font-size: 10px; letter-spacing: .04em; }
.map-labels { display: flex; justify-content: space-between; padding: 12px 0; border-top: 1px solid var(--vp-c-divider); color: var(--vp-c-text-3); font-size: 11px; }
.map-row { display: grid; grid-template-columns: 1fr 20px 1fr; align-items: center; gap: 12px; padding: 15px 0; border-bottom: 1px solid var(--vp-c-divider); font-family: var(--vp-font-family-mono); font-size: 12px; color: var(--vp-c-text-2); }
.map-row strong { justify-self: end; color: var(--vp-c-text-1); font-weight: 500; white-space: nowrap; }
.map-boundary { display: flex; gap: 8px; margin: 20px 0 0; color: var(--vp-c-text-3); font-size: 11px; line-height: 1.8; }
@media (max-width: 960px) { .home-hero { gap: 32px; grid-template-columns: 1.2fr 1fr; } .migration-map { padding: 20px; } }
@media (max-width: 760px) { .home-hero { grid-template-columns: 1fr; padding: 44px 0; gap: 32px; } .hero-copy h1 { font-size: clamp(30px, 7.5vw, 42px); } .migration-map { max-width: 520px; } .hero-actions { gap: 20px; } }
</style>
