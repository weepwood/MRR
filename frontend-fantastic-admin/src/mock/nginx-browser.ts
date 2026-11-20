import { Buffer } from 'node:buffer'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'

const servers = [
  { key: 'default', name: '默认节点', configured: true, baseUrl: 'https://default.example.test/images/' },
  { key: 'BA01', name: 'BA01', configured: true, baseUrl: 'https://ba01.example.test/images/' },
  { key: 'BA02', name: 'BA02', configured: false },
  { key: 'BA03', name: 'BA03', configured: false },
]
const demoImage = readFileSync(resolve('public/pwa/icon-192.png'))
const demoText = 'MRR 文件浏览演示数据\n'
const modifiedAt = '2026-01-15T09:30:00+08:00'
const demoFolder = '样例目录/'
const demoFiles = [
  { name: '示例图标.png', path: `${demoFolder}示例图标.png`, directory: false, size: demoImage.length, lastModified: modifiedAt },
  { name: '存储说明.txt', path: `${demoFolder}存储说明.txt`, directory: false, size: Buffer.byteLength(demoText), lastModified: modifiedAt },
]

export default defineFakeRoute([
  {
    url: '/api/v1/nginx-browser/servers',
    response: () => ({ code: 200, data: servers }),
  },
  {
    url: '/api/v1/nginx-browser',
    response: ({ query }) => {
      const server = servers.find(server => server.configured && server.key === (query?.server ?? 'default'))
      const path = String(query?.path || '')
      if (!server || (path !== '' && path !== demoFolder)) {
        return { code: 404, message: '示例目录不存在' }
      }
      const entries = path
        ? demoFiles
        : [{ name: '样例目录', path: demoFolder, directory: true, size: 0, lastModified: modifiedAt }]
      return {
        code: 200,
        data: {
          server: server.key,
          serverName: server.name,
          baseUrl: server.baseUrl,
          path,
          entries,
          offset: 0,
          limit: 200,
          totalEntries: entries.length,
          truncated: false,
          loadedDirectories: path ? 0 : entries.length,
          loadedFiles: path ? entries.length : 0,
          loadedBytes: entries.reduce((total, entry) => total + entry.size, 0),
        },
      }
    },
  },
  {
    url: '/api/v1/nginx-browser/file',
    rawResponse: (request, response) => {
      const query = new URL(request.url ?? '/', 'http://localhost').searchParams
      const path = query.get('path')
      if (!servers.some(server => server.configured && server.key === query.get('server'))) {
        response.statusCode = 404
        response.end()
      }
      else if (path === demoFiles[0].path) {
        response.setHeader('Content-Type', 'image/png')
        response.end(demoImage)
      }
      else if (path === demoFiles[1].path) {
        response.setHeader('Content-Type', 'text/plain; charset=utf-8')
        response.end(demoText)
      }
      else {
        response.statusCode = 404
        response.end()
      }
    },
  },
])
