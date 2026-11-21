import api, { getRequest } from '../index'

export interface NginxBrowserServer {
  key: string
  name: string
  baseUrl?: string | null
  configured: boolean
}

export interface NginxBrowserEntry {
  name: string
  path: string
  directory: boolean
  size: number
  lastModified?: string | null
}

export interface NginxBrowserPage {
  server: string
  serverName: string
  baseUrl: string
  path: string
  entries: NginxBrowserEntry[]
  offset: number
  limit: number
  totalEntries: number
  truncated: boolean
  loadedDirectories: number
  loadedFiles: number
  loadedBytes: number
}

export interface NginxBrowseParams {
  server?: string
  path?: string
  offset?: number
  limit?: number
}

export function getNginxBrowserServers(signal?: AbortSignal) {
  return getRequest<NginxBrowserServer[]>('/api/v1/nginx-browser/servers', { signal, skipGlobalError: true })
}

export function browseNginxDirectory(params: NginxBrowseParams = {}, signal?: AbortSignal) {
  return getRequest<NginxBrowserPage>('/api/v1/nginx-browser', { params, signal, skipGlobalError: true })
}

export async function fetchNginxBrowserFile(server: string, path: string, signal?: AbortSignal, maxBytes?: number): Promise<Blob> {
  const budget = maxBytes === undefined ? undefined : new AbortController()
  let exceeded = false
  const abort = () => budget?.abort()
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) {
    abort()
  }
  try {
    const blob = await api.get<Blob, Blob>('/api/v1/nginx-browser/file', {
      params: { server, path },
      // Pages 的浏览器 Mock 使用 Fetch Response 将文本转换为 Blob。
      adapter: import.meta.env.MODE === 'pages' ? 'fetch' : undefined,
      responseType: 'blob',
      signal: budget?.signal ?? signal,
      skipGlobalError: true,
      onDownloadProgress: maxBytes === undefined
        ? undefined
        : ({ loaded, total }) => {
            if (loaded > maxBytes || (total !== undefined && total > maxBytes)) {
              exceeded = true
              abort()
            }
          },
    })
    if (maxBytes !== undefined && blob.size > maxBytes) {
      throw new RangeError('Preview size exceeded')
    }
    return blob
  }
  catch (error) {
    if (exceeded) {
      throw new RangeError('Preview size exceeded')
    }
    throw error
  }
  finally {
    signal?.removeEventListener('abort', abort)
  }
}
