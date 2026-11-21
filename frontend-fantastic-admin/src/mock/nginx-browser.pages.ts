import { defineFakeRoute } from 'vite-plugin-fake-server/client'

// Pages 专用版本只使用浏览器可执行代码；本地 mock 仍采用 nginx-browser.ts。
// GitHub Pages 无服务器端 rawResponse，故仅展示目录及文本示例。
const servers = [
  { key: 'default', name: '默认节点（演示）', configured: true, baseUrl: 'https://example.invalid/images/' },
  { key: 'BA01', name: 'BA01（演示）', configured: true, baseUrl: 'https://example.invalid/images/' },
  { key: 'BA02', name: 'BA02', configured: false },
  { key: 'BA03', name: 'BA03', configured: false },
]
const demoFolder = '样例目录/'
const modifiedAt = '2026-01-15T09:30:00+08:00'
const demoFiles = [
  { name: '存储说明.txt', path: `${demoFolder}存储说明.txt`, directory: false, size: 39, lastModified: modifiedAt },
]

export default defineFakeRoute([
  {
    url: '/api/v1/nginx-browser/servers',
    response: () => ({ code: 200, data: servers }),
  },
  {
    url: '/api/v1/nginx-browser',
    response: ({ query }) => {
      const server = servers.find(item => item.configured && item.key === (query?.server ?? 'default'))
      const requestedPath = String(query?.path || '')
      if (!server || (requestedPath !== '' && requestedPath !== demoFolder)) {
        return { code: 404, message: '示例目录不存在' }
      }
      const entries = requestedPath
        ? demoFiles
        : [{ name: '样例目录', path: demoFolder, directory: true, size: 0, lastModified: modifiedAt }]
      return {
        code: 200,
        data: {
          server: server.key,
          serverName: server.name,
          baseUrl: server.baseUrl,
          path: requestedPath,
          entries,
          offset: 0,
          limit: 200,
          totalEntries: entries.length,
          truncated: false,
          loadedDirectories: requestedPath ? 0 : 1,
          loadedFiles: requestedPath ? entries.length : 0,
          loadedBytes: entries.reduce((total, entry) => total + entry.size, 0),
        },
      }
    },
  },
  {
    url: '/api/v1/nginx-browser/file',
    headers: { 'content-type': 'text/plain; charset=utf-8' },
    response: () => 'MRR 文件浏览演示数据\n此页面不连接真实 Nginx 文件服务。\n',
  },
])
