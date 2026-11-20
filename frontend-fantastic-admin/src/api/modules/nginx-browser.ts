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

export async function fetchNginxBrowserFile(server: string, path: string, signal?: AbortSignal): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/nginx-browser/file', {
    params: { server, path },
    responseType: 'blob',
    signal,
    skipGlobalError: true,
  })
  return response as unknown as Blob
}
