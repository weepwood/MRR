const palette = ['#2563EB', '#0F766E', '#B45309', '#7C3AED', '#BE123C']

function escapeSvgText(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function pageSvg(bah: string, page: number, typeLabel: string) {
  const color = palette[page % palette.length]
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="595" height="842" viewBox="0 0 595 842">`
    + `<rect width="595" height="842" fill="#F8FAFC"/>`
    + `<rect x="40" y="40" width="515" height="762" fill="#FFFFFF" stroke="${color}" stroke-width="4"/>`
    + `<rect x="40" y="40" width="515" height="96" fill="${color}"/>`
    + `<text x="297" y="100" font-size="34" text-anchor="middle" fill="#FFFFFF" font-family="sans-serif">合成影像 · 非真实病案</text>`
    + `<text x="297" y="400" font-size="96" text-anchor="middle" fill="${color}" font-family="sans-serif">P${page}</text>`
    + `<text x="297" y="480" font-size="32" text-anchor="middle" fill="#334155" font-family="sans-serif">${escapeSvgText(typeLabel)}</text>`
    + `<text x="297" y="760" font-size="24" text-anchor="middle" fill="#64748B" font-family="monospace">BAH ${escapeSvgText(bah)}</text>`
    + `</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}
