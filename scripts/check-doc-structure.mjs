import { fileURLToPath } from 'node:url'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import {
  loadMap,
  validateMap,
  checkedPath,
  sections,
} from './lib/chapter-map.mjs'
const root = fileURLToPath(new URL('../', import.meta.url))
const map = await validateMap(await loadMap(root), root)
for (const file of ['chapter', 'code-comparison', 'analogy-review'])
  await checkedPath(root, `docs/templates/${file}.md`)
const template = await readFile(
  resolve(root, 'docs/templates/chapter.md'),
  'utf8',
)
if (sections.some((s) => !template.includes(`#### ${s}`)))
  throw new Error('章节模板与校验规则漂移')
const counts = Object.fromEntries(
  ['planned', 'draft', 'in_review', 'done'].map((s) => [
    s,
    map.chapters.filter((c) => c.status === s).length,
  ]),
)
console.log('章节映射、路径和十段式通过', counts)
