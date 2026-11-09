import { defineConfig } from 'vitepress'
import { dirname, relative, resolve, sep } from 'node:path'
import catalog from './course.json'

const repository = 'https://github.com/zrayjay/java-for-nestjs'
const docsRoot = resolve('docs')
const base = process.env.DOCS_BASE || '/'
if (!base.startsWith('/') || !base.endsWith('/') || base.includes('..')) {
  throw new Error('DOCS_BASE须为以/开始和结束的站点子路径。')
}

export default defineConfig({
  lang: 'zh-CN',
  title: 'Java for NestJS',
  description: '以NestJS为认知参照的Java与Spring Boot中文系统教程。',
  base,
  outDir: process.env.DOCS_OUT_DIR,
  cleanUrls: true,
  lastUpdated: true,
  head: [['meta', { name: 'theme-color', content: '#215b46' }]],
  themeConfig: {
    siteTitle: 'Java for NestJS',
    nav: [
      { text: '学习指南', link: '/00-introduction/learning-guide' },
      { text: '课程目录', link: '/course/' },
      { text: '技术基线', link: '/00-introduction/compatibility-matrix' },
    ],
    sidebar: [
      { text: '从这里开始', items: [
        { text: '学习指南', link: '/00-introduction/learning-guide' },
        { text: '课程目录 · 45章', link: '/course/' },
        { text: '运行兼容探针', link: '/00-introduction/run-probe' },
        { text: '图表与阅读说明', link: '/00-introduction/diagrams' },
      ] },
      { text: '课程路线 · 正文待建设', collapsed: false, items: catalog.map(part => ({
        text: `${part.number} · ${part.title}`, link: `/course/#part-${part.number}`,
      })) },
      { text: '工程与维护', collapsed: true, items: [
        { text: '兼容矩阵', link: '/00-introduction/compatibility-matrix' },
        { text: '技术基线 ADR', link: '/adr/0001-technology-baseline' },
        { text: 'P0-02 验证记录', link: '/progress/P0-02' },
        { text: '文档站运行指南', link: '/00-introduction/docs-site' },
      ] },
    ],
    socialLinks: [{ icon: 'github', link: repository }],
    outline: { label: '本页目录', level: [2, 3] },
    docFooter: { prev: '上一篇', next: '下一篇' },
    lastUpdated: { text: '最后更新' },
    darkModeSwitchLabel: '切换外观',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '返回顶部',
    skipToContentLabel: '跳转到正文',
    editLink: { pattern: `${repository}/edit/main/docs/:path`, text: '在GitHub上改进此页' },
    search: { provider: 'local', options: { locales: { root: { translations: {
      button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
      modal: {
        displayDetails: '显示详细内容', resetButtonTitle: '清空搜索', backButtonTitle: '返回',
        noResultsText: '没有找到相关内容',
        footer: { selectText: '选择', selectKeyAriaLabel: '回车', navigateText: '切换', navigateUpKeyAriaLabel: '上箭头', navigateDownKeyAriaLabel: '下箭头', closeText: '关闭', closeKeyAriaLabel: 'Esc' },
      },
    } } } } },
    footer: { message: '以熟悉的经验为起点，以可验证的理解为终点。', copyright: 'MIT License · Java for NestJS contributors' },
  },
  markdown: {
    theme: { light: 'github-light', dark: 'github-dark' },
    config(md) {
      const fence = md.renderer.rules.fence!
      md.renderer.rules.fence = (tokens, index, options, env, self) => {
        if (tokens[index].info.trim() === 'mermaid') {
          const code = md.utils.escapeHtml(JSON.stringify(tokens[index].content))
          return `<MermaidDiagram :code="${code}" />\n`
        }
        return fence(tokens, index, options, env, self)
      }
      // Keep repository-relative links usable without publishing files outside docs/.
      const link = md.renderer.rules.link_open!
      md.renderer.rules.link_open = (tokens, index, options, env, self) => {
        const token = tokens[index]
        const href = token.attrGet('href')
        if (href && href.startsWith('.') && env.path) {
          const [path, anchor] = href.split('#')
          const target = resolve(dirname(env.path), decodeURIComponent(path))
          const inDocs = relative(docsRoot, target)
          if (inDocs === '..' || inDocs.startsWith(`..${sep}`)) {
            const sourcePath = relative(resolve('.'), target).split(sep).map(encodeURIComponent).join('/')
            token.attrSet('href', `${repository}/blob/main/${sourcePath}${anchor ? `#${anchor}` : ''}`)
          } else {
            const sitePath = inDocs.split(sep).map(encodeURIComponent).join('/')
            token.attrSet('href', `/${sitePath}${anchor ? `#${anchor}` : ''}`)
          }
        }
        return link(tokens, index, options, env, self)
      }
    },
  },
})
