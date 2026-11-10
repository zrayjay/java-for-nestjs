import { readFile, realpath, stat } from 'node:fs/promises'
import { resolve, relative, isAbsolute, sep } from 'node:path'
import { parse } from 'yaml'
import MarkdownIt from 'markdown-it'

export const markdown = new MarkdownIt({ html: true })
export const sections = [
  '1. 一句话定义',
  '2. 为什么需要它',
  '3. 生活类比',
  '4. Node.js / TypeScript / NestJS 技术对照',
  '5. 两端代码对照',
  '6. 原理与流程图',
  '7. 类比边界与错误直觉',
  '8. 企业应用场景与选型',
  '9. 常见错误与排查',
  '10. 实操练习与验收',
]

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
const text = (value) => typeof value === 'string' && value.trim().length > 0
const unique = (items) => new Set(items).size === items.length
const within = (root, target) => {
  const rel = relative(root, target)
  return rel !== '..' && !rel.startsWith(`..${sep}`) && !isAbsolute(rel)
}

export async function checkedPath(root, path, { mustExist = true } = {}) {
  assert(
    text(path) && !path.includes('\\') && !isAbsolute(path),
    `非法路径: ${path}`,
  )
  assert(!path.split('/').includes('..'), `路径越界: ${path}`)
  const target = resolve(root, path)
  assert(within(root, target), `路径越界: ${path}`)
  if (mustExist) {
    assert(
      within(await realpath(root), await realpath(target)),
      `符号链接越界: ${path}`,
    )
    assert((await stat(target)).isFile(), `应为真实文件: ${path}`)
  }
  return target
}

export async function loadMap(root) {
  return parse(await readFile(resolve(root, 'chapter-map.yml'), 'utf8'))
}

export function chapterStructure(source, chapter) {
  const front = source.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/)
  assert(front, `${chapter.id}: 缺少frontmatter`)
  const meta = parse(front[1])
  assert(
    meta.chapter_id === chapter.id,
    `${chapter.id}: frontmatter chapter_id必须是带引号的两位字符串`,
  )
  assert(
    meta.teaching_status === chapter.status,
    `${chapter.id}: teaching_status与映射不一致/模板冒充正文`,
  )
  assert(
    text(meta.baseline) && !meta.baseline.includes('待'),
    `${chapter.id}: 缺少明确版本基线`,
  )
  assert(
    !/【[^】]*】|\bTODO\b|\bTBC\b/i.test(source),
    `${chapter.id}: 存在模板占位内容`,
  )
  const tokens = markdown.parse(source.slice(front[0].length), {})
  const headings = tokens.flatMap((t, i) =>
    t.type === 'heading_open'
      ? [
          {
            level: Number(t.tag.slice(1)),
            title: tokens[i + 1].content,
            line: t.map[0],
          },
        ]
      : [],
  )
  const body = source.slice(front[0].length).split(/\r?\n/)
  const scope = headings.findIndex((h) => h.level === 2 && h.title === '知识点')
  assert(scope >= 0, `${chapter.id}: 缺少知识点区域`)
  const end = headings.findIndex((h, i) => i > scope && h.level <= 2)
  const points = headings.slice(scope + 1, end < 0 ? undefined : end)
  assert(
    points.some((h) => h.level === 3),
    `${chapter.id}: 至少一个真实知识点`,
  )
  assert(points[0]?.level === 3, `${chapter.id}: 知识点必须以三级标题开始`)
  for (let i = 0; i < points.length; i++) {
    if (points[i].level !== 3) continue
    let j = i + 1
    while (j < points.length && points[j].level !== 3) j++
    const block = points.slice(i + 1, j)
    assert(
      block.length === 10 &&
        block.every((h, n) => h.level === 4 && h.title === sections[n]),
      `${chapter.id}/${points[i].title}: 十段式缺段或顺序/标题错误`,
    )
    for (let n = 0; n < block.length; n++) {
      const stop =
        block[n + 1]?.line ??
        points[j]?.line ??
        (end < 0 ? body.length : headings[end].line)
      const content = body
        .slice(block[n].line + 1, stop)
        .join('\n')
        .trim()
      assert(
        content.length > 0 && !/^<!--[^]*-->$/.test(content),
        `${chapter.id}: 空段落 ${block[n].title}`,
      )
    }
  }
}

export async function validateMap(map, root) {
  assert(map?.schemaVersion === 1, '不支持的schemaVersion')
  assert(Array.isArray(map.parts) && map.parts.length === 9, '必须有9个部分')
  assert(
    map.parts.every(
      (p, i) =>
        p.id === String(i + 1).padStart(2, '0') &&
        text(p.title) &&
        /^\d{2}-[a-z-]+$/.test(p.directory),
    ),
    '部分编号/标题/目录无效',
  )
  assert(unique(map.parts.map((p) => p.directory)), '部分目录重复')
  assert(
    Array.isArray(map.chapters) && map.chapters.length === 45,
    '必须有45章',
  )
  const ids = map.chapters.map((c) => c.id)
  assert(
    ids.every((id, i) => id === String(i + 1).padStart(2, '0')),
    '章节编号须按顺序唯一覆盖01~45',
  )
  assert(unique(map.chapters.map((c) => c.path?.toLowerCase())), '章节路径重复')
  for (const c of map.chapters) {
    const part = map.parts.find((p) => p.id === c.part)
    assert(
      part &&
        text(c.title) &&
        text(c.topics) &&
        text(c.reference) &&
        typeof c.pilot === 'boolean',
      `${c.id}: 章节基本信息无效`,
    )
    const ranges = [
      [1, 5],
      [6, 12],
      [13, 16],
      [17, 23],
      [24, 28],
      [29, 33],
      [34, 37],
      [38, 41],
      [42, 45],
    ]
    const [min, max] = ranges[Number(c.part) - 1]
    assert(
      Number(c.id) >= min && Number(c.id) <= max,
      `${c.id}: 章节所属部分不符计划`,
    )
    assert(
      new RegExp(
        `^docs/${part.directory}/${c.id}-[a-z0-9]+(?:-[a-z0-9]+)*\\.md$`,
      ).test(c.path),
      `${c.id}: 正文路径不符计划`,
    )
    assert(
      ['planned', 'draft', 'in_review', 'done'].includes(c.status),
      `${c.id}: 未知状态`,
    )
    assert(
      Array.isArray(c.prerequisites) &&
        unique(c.prerequisites) &&
        c.prerequisites.every((id) => ids.includes(id) && id !== c.id),
      `${c.id}: 未知或重复先修章节`,
    )
    assert(
      Array.isArray(c.tags) &&
        c.tags.length &&
        c.tags.every(text) &&
        unique(c.tags),
      `${c.id}: 缺少技术标签`,
    )
    for (const field of ['examples', 'tests', 'verification', 'reviews'])
      assert(Array.isArray(c[field]), `${c.id}: ${field}必须是数组`)
    for (const path of [...c.examples, ...c.tests, ...c.verification])
      await checkedPath(root, path)
    for (const path of [...c.examples, ...c.tests])
      assert(
        /^(examples|project)\//.test(path),
        `${c.id}: 示例和测试必须位于工程目录`,
      )
    for (const review of c.reviews) {
      await checkedPath(root, review.path)
      assert(
        text(review.reviewer) &&
          /^\d{4}-\d{2}-\d{2}$/.test(review.date) &&
          review.independent === true &&
          review.conclusion === 'approved',
        `${c.id}: 独立审查记录不完整`,
      )
    }
    const path = await checkedPath(root, c.path, {
      mustExist: c.status !== 'planned',
    })
    if (c.status !== 'planned') {
      chapterStructure(await readFile(path, 'utf8'), c)
      assert(
        (c.examples.length && c.tests.length) ||
          (text(c.noCodeReason) && c.noCodeReason.trim().length >= 10),
        `${c.id}: 缺少示例/测试或明确无需代码的原因`,
      )
      if (['in_review', 'done'].includes(c.status))
        assert(c.verification.length, `${c.id}: 缺少运行验证证据`)
      if (c.status === 'done')
        assert(c.reviews.length, `${c.id}: 缺少独立审查证据`)
    }
  }
  const visiting = new Set(),
    visited = new Set()
  const visit = (id) => {
    assert(!visiting.has(id), `先修依赖成环: ${id}`)
    if (visited.has(id)) return
    visiting.add(id)
    map.chapters.find((c) => c.id === id).prerequisites.forEach(visit)
    visiting.delete(id)
    visited.add(id)
  }
  ids.forEach(visit)
  return map
}

export function toCatalog(map) {
  return map.parts.map((p) => ({
    number: p.id,
    title: p.title,
    chapters: map.chapters
      .filter((c) => c.part === p.id)
      .map((c) => ({
        number: Number(c.id),
        title: c.title,
        topics: c.topics,
        reference: c.reference,
        pilot: c.pilot,
        status: c.status,
        path: c.path,
      })),
  }))
}
