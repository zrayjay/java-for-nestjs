import { readFile, writeFile } from 'node:fs/promises'

const catalog = JSON.parse(await readFile(new URL('../docs/.vitepress/course.json', import.meta.url), 'utf8'))
const chapters = catalog.flatMap(part => part.chapters)
if (chapters.length !== 45 || chapters.some((chapter, index) => chapter.number !== index + 1)) {
  throw new Error('课程目录必须按顺序包含01~45章，每章编号唯一。')
}

const cell = value => String(value).replaceAll('|', '&#124;')
const lines = [
  '---',
  'title: 课程目录',
  'description: 从NestJS迁移到Java与Spring Boot的45章学习地图。',
  '---',
  '',
  '<!-- 由 scripts/generate-course-index.mjs 从 course.json 生成。请修改数据源后重新生成。 -->',
  '# 课程目录',
  '',
  '45章，9个部分。从运行时差异出发，走向可交付的Java后端。',
  '',
  '::: info 建设状态',
  '**正文尚未交付。** 下方为计划索引，不计作完成章节。第02、08、18、27章将作为首轮试点；先完成样章及独立审查，再扩展其余内容。',
  ':::',
  '',
  '[了解如何学习](../00-introduction/learning-guide.md) · [查看已验证的技术版本](../00-introduction/compatibility-matrix.md)',
]

for (const part of catalog) {
  lines.push('', `## Part ${part.number} · ${part.title} {#part-${part.number}}`, '',
    '| 章节 | 主题 | 核心知识点 | 认知参照 | 状态 |', '|---|---|---|---|---|')
  for (const chapter of part.chapters) {
    lines.push(`| ${String(chapter.number).padStart(2, '0')} | ${cell(chapter.title)} | ${cell(chapter.topics)} | ${cell(chapter.reference)} | ${chapter.pilot ? '试点计划 · 待编写' : '待编写'} |`)
  }
}

lines.push('', '## 从已有成果开始', '',
  '- [学习指南](../00-introduction/learning-guide.md)：先修知识与能力验收。',
  '- [运行兼容探针](../00-introduction/run-probe.md)：真实数据库、缓存、事务与认证验证。',
  '- [图表说明](../00-introduction/diagrams.md)：了解教程使用的流程图及阅读方式。', '')

await writeFile(new URL('../docs/course/index.md', import.meta.url), lines.join('\n'))
console.log(`已生成${catalog.length}部分、${chapters.length}章的课程索引。`)
