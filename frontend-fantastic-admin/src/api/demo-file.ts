// Decode only bounded demo envelopes; production requests retain their binary contract.
export const isDemoFileMode = import.meta.env.MODE === 'pages' || import.meta.env.MODE === 'mock'

export function demoCsvResponse(payload: { text: string, name: string } | undefined) {
  if (!payload || typeof payload.text !== 'string' || payload.text.length > 2 * 1024 * 1024 || !payload.text.startsWith('\uFEFF')
    || !/^mrr-demo-[a-z-]+\.csv$/.test(payload.name)) { throw new Error('演示 CSV 文件无效') }
  return { data: new Blob([payload.text], { type: 'text/csv;charset=utf-8' }), headers: { 'content-disposition': `attachment; filename="${payload.name}"` } }
}

export function demoWorkbookBlob(payload: { base64: string, name: string } | undefined) {
  if (!payload || typeof payload.base64 !== 'string' || payload.base64.length > 2 * 1024 * 1024 || payload.name !== 'mrr-demo-patients.xlsx') { throw new Error('演示工作簿无效') }
  const data = atob(payload.base64)
  if (!data.startsWith('PK\x03\x04')) { throw new Error('演示工作簿签名无效') }
  return new Blob([Uint8Array.from(data, c => c.charCodeAt(0))], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
}

export function demoImageBlob(payload: { svg: string } | undefined) {
  if (!payload || typeof payload.svg !== 'string' || payload.svg.length > 65536 || !payload.svg.startsWith('<svg ') || !payload.svg.endsWith('</svg>')) { throw new Error('演示影像无效') }
  return new Blob([payload.svg], { type: 'image/svg+xml' })
}
