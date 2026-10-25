import fs from 'node:fs'

const html = fs.readFileSync('dist/index.html', 'utf8')
const urls = []
for (const m of html.matchAll(/href="([^"]+)"/g)) {
  if (/modulepreload/.test(m.input.slice(Math.max(0, m.index - 40), m.index)) || /stylesheet/.test(m.input.slice(Math.max(0, m.index - 40), m.index))) {
    urls.push(m[1])
  }
}
for (const m of html.matchAll(/src="(\/assets\/[^"]+)"/g)) {
  urls.push(m[1])
}
const uniq = [...new Set(urls)]
let raw = 0
let gz = 0
let br = 0
let files = 0
for (const p of uniq) {
  const f = `dist${p}`
  if (!fs.existsSync(f)) {
    console.log('MISSING', p)
    continue
  }
  raw += fs.statSync(f).size
  files++
  if (fs.existsSync(`${f}.gz`)) { gz += fs.statSync(`${f}.gz`).size }
  if (fs.existsSync(`${f}.br`)) { br += fs.statSync(`${f}.br`).size }
}
console.log(`首屏文件数: ${files} raw: ${(raw / 1024).toFixed(0)}KB gz: ${(gz / 1024).toFixed(0)}KB br: ${(br / 1024).toFixed(0)}KB`)
