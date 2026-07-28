import type { AxiosAdapter, AxiosRequestConfig } from 'axios'
import axios from 'axios'
import api from './index'

export interface RawApiResponse<T> {
  data: T
  headers: unknown
}

export function preserveRawResponseAdapter(adapter: AxiosAdapter): AxiosAdapter {
  return async (config) => {
    const response = await adapter(config)
    return {
      ...response,
      data: {
        data: response.data,
        headers: response.headers,
      } satisfies RawApiResponse<unknown>,
    }
  }
}

export function getRawRequest<T = unknown>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<RawApiResponse<T>> {
  const adapter = axios.getAdapter(config?.adapter ?? api.defaults.adapter)
  return api.get<RawApiResponse<T>>(url, {
    ...config,
    adapter: preserveRawResponseAdapter(adapter),
  }) as unknown as Promise<RawApiResponse<T>>
}
