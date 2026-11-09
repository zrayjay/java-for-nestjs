import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import HomeHero from './components/HomeHero.vue'
import HomeRoute from './components/HomeRoute.vue'
import MermaidDiagram from './components/MermaidDiagram.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('HomeHero', HomeHero)
    app.component('HomeRoute', HomeRoute)
    app.component('MermaidDiagram', MermaidDiagram)
  },
} satisfies Theme
