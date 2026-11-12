import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile, rm, copyFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  loadMap,
  validateMap,
  chapterStructure,
  sections,
} from '../lib/chapter-map.mjs'
import { checkLinks } from '../check-example-links.mjs'
import { verifyCompatibility } from '../verify-compatibility.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const source = await loadMap(root)
const fresh = () => {
  const map = structuredClone(source)
  for (const c of map.chapters) {
    c.status = 'planned'
    c.examples = []
    c.tests = []
    c.verification = []
    c.reviews = []
  }
  return map
}
async function fixture(t) {
  const dir = await mkdtemp(resolve(tmpdir(), 'chapter-validator-'))
  t.after(async () => {
    assert.equal(dirname(dir), resolve(tmpdir()))
    assert.ok(dir.startsWith(resolve(tmpdir(), 'chapter-validator-')))
    await rm(dir, { recursive: true, force: true })
  })
  return dir
}
function chapterBody(c, overrides = {}) {
  return `---\nchapter_id: '${c.id}'\nteaching_status: ${c.status}\nbaseline: Java21 / Spring Boot3.5.16\n${overrides.meta || ''}---\n# Synthetic fixture\n\n## 知识点\n\n### ch01-kp01 · 合成知识点\n\n${sections.map((s) => `#### ${s}\n\n用于验证结构的合成正文，不是课程内容。\n`).join('\n')}\n## 章末验收与审查记录\n\n合成证据。\n`
}

test('45 planned chapters do not require fabricated body files', async (t) => {
  await validateMap(fresh(), await fixture(t))
})
for (const [name, change, pattern] of [
  ['duplicate ID', (m) => (m.chapters[1].id = '01'), /编号/],
  [
    'duplicate path',
    (m) => (m.chapters[1].path = m.chapters[0].path),
    /路径重复/,
  ],
  ['path traversal', (m) => (m.chapters[0].path = 'docs/../secret.md'), /路径/],
  ['wrong part', (m) => (m.chapters[0].part = '02'), /所属部分/],
  [
    'unknown prerequisite',
    (m) => (m.chapters[0].prerequisites = ['99']),
    /先修/,
  ],
  ['dependency cycle', (m) => (m.chapters[0].prerequisites = ['02']), /成环/],
  ['unknown status', (m) => (m.chapters[0].status = 'complete'), /未知状态/],
  ['missing tags', (m) => (m.chapters[0].tags = []), /标签/],
  ['missing body', (m) => (m.chapters[0].status = 'done'), /ENOENT/],
  [
    'missing example',
    (m) => (m.chapters[0].examples = ['examples/not-there.java']),
    /ENOENT/,
  ],
])
  test(`reject ${name}`, async (t) => {
    const m = fresh()
    change(m)
    await assert.rejects(validateMap(m, await fixture(t)), pattern)
  })
test('ten-part structure parses headings, ignores fenced fake headings', () => {
  const c = { id: '01', status: 'draft' }
  chapterStructure(chapterBody(c), c)
  assert.throws(
    () =>
      chapterStructure(
        chapterBody(c).replace(
          '#### 7. 类比边界与错误直觉',
          '```md\n#### 7. 类比边界与错误直觉\n```',
        ),
        c,
      ),
    /缺段/,
  )
  assert.throws(
    () =>
      chapterStructure(
        chapterBody(c).replace(
          '#### 10. 实操练习与验收',
          '#### 11. 实操练习与验收',
        ),
        c,
      ),
    /缺段/,
  )
  assert.throws(
    () =>
      chapterStructure(
        chapterBody(c).replace('### ch01-kp01 · 合成知识点', '## point-01'),
        c,
      ),
    /知识点/,
  )
})
test('knowledge point IDs must match the chapter and be unique', () => {
  const c = { id: '01', status: 'draft' }
  const body = chapterBody(c)
  assert.throws(
    () => chapterStructure(body.replace('ch01-kp01', 'ch02-kp01'), c),
    /稳定ID/,
  )
  assert.throws(
    () => chapterStructure(body.replace('ch01-kp01 · ', ''), c),
    /稳定ID/,
  )
  const point = body.slice(
    body.indexOf('### ch01-kp01'),
    body.indexOf('## 章末验收与审查记录'),
  )
  assert.throws(
    () =>
      chapterStructure(
        body.replace(
          '## 章末验收与审查记录',
          point + '\n## 章末验收与审查记录',
        ),
        c,
      ),
    /ID重复/,
  )
})

test('reject template masquerade, placeholders and empty sections', () => {
  const c = { id: '01', status: 'done' },
    body = chapterBody(c)
  assert.throws(
    () =>
      chapterStructure(
        body.replace('teaching_status: done', 'teaching_status: template'),
        c,
      ),
    /模板/,
  )
  assert.throws(() => chapterStructure(body + '\n【待填写】', c), /占位/)
  assert.throws(
    () =>
      chapterStructure(
        body.replace('用于验证结构的合成正文，不是课程内容。', ''),
        c,
      ),
    /空段落/,
  )
})
test('completed synthetic no-code chapter requires verification and independent review records', async (t) => {
  const dir = await fixture(t),
    m = fresh(),
    c = m.chapters[0]
  c.status = 'done'
  c.noCodeReason = '本合成示例只验证文档校验器，无需教学示例代码。'
  await mkdir(dirname(resolve(dir, c.path)), { recursive: true })
  await writeFile(resolve(dir, c.path), chapterBody(c))
  await assert.rejects(validateMap(m, dir), /运行验证/)
  await writeFile(resolve(dir, 'evidence.md'), 'Synthetic verification record')
  c.verification = ['evidence.md']
  await assert.rejects(validateMap(m, dir), /独立审查/)
  c.reviews = [
    {
      path: 'evidence.md',
      reviewer: 'Synthetic reviewer',
      date: '2026-11-10',
      independent: true,
      conclusion: 'approved',
    },
  ]
  await validateMap(m, dir)
  c.noCodeReason = null
  await assert.rejects(validateMap(m, dir), /示例\/测试/)
})
test('source links fail for missing local files while fenced sample links are ignored', async (t) => {
  const dir = await fixture(t)
  await writeFile(
    resolve(dir, 'index.md'),
    '[missing](absent.md)\n```md\n[example](fictional.md)\n```',
  )
  await assert.rejects(checkLinks(dir), /absent.md/)
  await writeFile(resolve(dir, 'absent.md'), '# Existing')
  assert.equal(await checkLinks(dir), 2)
})
test('built HTML checks clean URLs, encoded anchors and deployment base', async (t) => {
  const dir = await fixture(t)
  await writeFile(
    resolve(dir, 'index.html'),
    '<a href="/book/chapter#%E7%9F%A5%E8%AF%86">章</a>',
  )
  await writeFile(resolve(dir, 'chapter.html'), '<h1 id="知识">Knowledge</h1>')
  await checkLinks(dir, { built: true, base: '/book/' })
  await writeFile(
    resolve(dir, 'chapter.html'),
    '<h1 id="other">Wrong anchor</h1>',
  )
  await assert.rejects(
    checkLinks(dir, { built: true, base: '/book/' }),
    /锚点不存在/,
  )
  await assert.rejects(
    checkLinks(dir, { built: true, base: '/wrong/' }),
    /base不一致/,
  )
})
test('version drift is blocked against actual package/POM/wrapper/CI sources', async (t) => {
  const dir = await fixture(t)
  const paths = [
    'chapter-map.yml',
    'package.json',
    'pom.xml',
    '.mvn/wrapper/maven-wrapper.properties',
    'docs/00-introduction/compatibility-matrix.md',
    '.github/workflows/docs.yml',
    '.github/workflows/foundation.yml',
    '.github/workflows/transactions.yml',
    '.github/workflows/data-access.yml',
    '.github/workflows/security.yml',
    '.github/workflows/cache.yml',
    'examples/prisma-reference/package.json',
    'examples/nestjs-reference/package.json',
    'examples/nestjs-security-reference/package.json',
    'examples/transaction-lab/pom.xml',
    'examples/data-access-lab/pom.xml',
    'examples/spring-web-lab/pom.xml',
    'examples/security-lab/pom.xml',
  ]
  for (const path of paths) {
    await mkdir(dirname(resolve(dir, path)), { recursive: true })
    await copyFile(resolve(root, path), resolve(dir, path))
  }
  await verifyCompatibility(dir)
  const pkg = JSON.parse(
    await (
      await import('node:fs/promises')
    ).readFile(resolve(dir, 'package.json'), 'utf8'),
  )
  pkg.pnpm.overrides.vite = '0.0.0'
  await writeFile(resolve(dir, 'package.json'), JSON.stringify(pkg))
  await assert.rejects(verifyCompatibility(dir), /版本漂移 vite/)
})
