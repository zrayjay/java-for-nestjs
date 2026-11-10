import { readdir, readFile, stat, realpath } from 'node:fs/promises'
import { resolve, dirname, relative, sep, isAbsolute } from 'node:path'
import { fileURLToPath } from 'node:url'
import { markdown } from './lib/chapter-map.mjs'

export async function filesIn(dir, extension) {
  const entries = await readdir(dir, { withFileTypes: true })
  const groups = await Promise.all(
    entries
      .filter(
        (e) =>
          !['node_modules', '.git', 'target', 'dist', '.vitepress'].includes(
            e.name,
          ),
      )
      .map(async (e) =>
        e.isDirectory()
          ? filesIn(resolve(dir, e.name), extension)
          : e.name.endsWith(extension)
            ? [resolve(dir, e.name)]
            : [],
      ),
  )
  return groups.flat()
}

export async function checkLinks(root, { built = false, base = '/' } = {}) {
  root = await realpath(root)
  const errors = [],
    cache = new Map()
  const files = await filesIn(root, built ? '.html' : '.md')
  async function targetExists(path) {
    if (!cache.has(path))
      cache.set(
        path,
        stat(path).catch(() => null),
      )
    return cache.get(path)
  }
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    let links
    if (built) {
      links = [...source.matchAll(/\b(?:href|src)="([^"]*)"/g)].map((m) =>
        markdown.utils.unescapeAll(m[1]),
      )
    } else {
      links = []
      const collect = (tokens) => {
        for (const token of tokens) {
          if (token.type === 'link_open') links.push(token.attrGet('href'))
          if (token.type === 'image') links.push(token.attrGet('src'))
          if (token.children) collect(token.children)
        }
      }
      collect(markdown.parse(source, {}))
    }
    for (const href of links) {
      if (!href || /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(href)) continue
      try {
        const [url, fragment] = href.split('#')
        let path = decodeURIComponent(url.split('?')[0])
        if (built && path.startsWith('/')) {
          if (!path.startsWith(base)) throw new Error('站点base不一致')
          path = path.slice(base.length)
          path = resolve(root, path)
        } else if (!built && path.startsWith('/')) {
          path = resolve(root, 'docs', path.slice(1))
        } else path = path ? resolve(dirname(file), path) : file
        const rel = relative(root, path)
        if (rel === '..' || rel.startsWith(`..${sep}`) || isAbsolute(rel))
          throw new Error('链接越出仓库/产物')
        let info = await targetExists(path)
        if (info?.isDirectory()) {
          path = resolve(path, built ? 'index.html' : 'index.md')
          info = await targetExists(path)
        }
        if (!info && built && !/\.[a-z0-9]+$/i.test(path)) {
          path += '.html'
          info = await targetExists(path)
        }
        if (!info?.isFile()) throw new Error('目标文件不存在')
        if (built && fragment && path.endsWith('.html')) {
          const html = await readFile(path, 'utf8')
          const anchors = [...html.matchAll(/\bid="([^"]+)"/g)].map((m) =>
            markdown.utils.unescapeAll(m[1]),
          )
          if (!anchors.includes(decodeURIComponent(fragment)))
            throw new Error('锚点不存在')
        }
      } catch (error) {
        errors.push(`${relative(root, file)} -> ${href}: ${error.message}`)
      }
    }
  }
  if (errors.length) throw new Error(errors.join('\n'))
  return files.length
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const root = fileURLToPath(new URL('../', import.meta.url))
  const built = process.argv.includes('--built')
  const count = await checkLinks(
    built
      ? resolve(
          process.env.DOCS_OUT_DIR || resolve(root, 'docs/.vitepress/dist'),
        )
      : root,
    { built, base: process.env.DOCS_BASE || '/' },
  )
  console.log(
    `${count}份${built ? 'HTML页面及锚点' : 'Markdown源码文件链接'}检查通过`,
  )
}
