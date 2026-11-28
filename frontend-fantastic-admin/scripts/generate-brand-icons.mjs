import { Buffer } from 'node:buffer'

import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
// A single folded medical record. Short negative-space text rows remain inside the page.
// Brand-only muted blue, retaining the default theme hue.
const ink = '#4775D2'
const fold = '#A3BDF0'
const paper = '#EAF0FA'
const mark = `<path d="M16 5h21l17 17v31a6 6 0 0 1-6 6H16a6 6 0 0 1-6-6V11a6 6 0 0 1 6-6Zm5 27a2.5 2.5 0 0 0 0 5h22a2.5 2.5 0 0 0 0-5Zm0 11a2.5 2.5 0 0 0 0 5h13a2.5 2.5 0 0 0 0-5Z" fill="${ink}" fill-rule="evenodd"/><path d="M37 5v12a5 5 0 0 0 5 5h12Z" fill="${fold}"/>`
function svg(size, maskable = false, apple = false, app = false) {
  // The document fits within the maskable 80%-diameter safe circle.
  const body = maskable || apple
    ? `<rect width="64" height="64" fill="${paper}"/><g transform="translate(12.8 12.8) scale(.6)">${mark}</g>`
    : app ? `<rect x="2" y="2" width="60" height="60" rx="14" fill="${paper}"/><g transform="translate(7.04 7.04) scale(.78)">${mark}</g>` : mark
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 64 64" role="img" aria-label="MRR 病案文件管理系统">${body}</svg>\n`
}
async function save(path, bytes) {
  const target = resolve(root, path)
  await mkdir(dirname(target), { recursive: true })
  await writeFile(target, bytes)
}
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ deviceScaleFactor: 1 })
  async function png(size, maskable = false, apple = false, app = false) {
    await page.setViewportSize({ width: size, height: size })
    await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block}</style>${svg(size, maskable, apple, app)}`)
    return page.screenshot({ omitBackground: true, animations: 'disabled' })
  }
  const master = svg(64)
  await save('src/assets/images/mrr-logo.svg', master)
  // In-app dark mode is independent of the OS color scheme. Keep the same geometry.
  await save('src/assets/images/mrr-logo-dark.svg', master.replaceAll(ink, '#7097E0').replaceAll(fold, '#C5D7F7'))
  await save('public/favicon.svg', master)
  for (const size of [192, 512]) {
    await save(`public/pwa/icon-${size}.svg`, svg(size, false, false, true))
    await save(`public/pwa/icon-${size}.png`, await png(size, false, false, true))
  }
  await save('public/pwa/icon-maskable.svg', svg(512, true))
  await save('public/pwa/icon-maskable-512.png', await png(512, true))
  await save('public/pwa/apple-touch-icon.png', await png(180, false, true))
  const images = []
  for (const size of [16, 32, 48]) { images.push({ size, bytes: await png(size) }) }
  // ICO directory embeds PNG images; offsets and dimensions are explicit.
  const header = Buffer.alloc(6 + images.length * 16)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(images.length, 4)
  let offset = header.length
  images.forEach(({ size, bytes }, i) => {
    const pos = 6 + i * 16
    header[pos] = size
    header[pos + 1] = size
    header.writeUInt16LE(1, pos + 4)
    header.writeUInt16LE(32, pos + 6)
    header.writeUInt32LE(bytes.length, pos + 8)
    header.writeUInt32LE(offset, pos + 12)
    offset += bytes.length
  })
  await save('public/favicon.ico', Buffer.concat([header, ...images.map(item => item.bytes)]))
}
finally { await browser.close() }
