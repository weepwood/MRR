import { describe, expect, it } from 'vitest'
import { resolveImageUrl } from '../constants'

describe('archive image source resolution', () => {
  it('uses the backend-selected img_url before the compatibility ossUrl field', () => {
    expect(resolveImageUrl({
      img_url: 'http://local/image.jpg',
      ossUrl: 'https://oss/image.jpg',
    })).toBe('http://local/image.jpg')
  })

  it('falls back to ossUrl for legacy responses without img_url', () => {
    expect(resolveImageUrl({ ossUrl: 'https://oss/image.jpg' }))
      .toBe('https://oss/image.jpg')
  })

  it('preserves signed query parameters when adding a cache buster', () => {
    expect(resolveImageUrl({ img_url: 'https://oss/image.jpg?signature=1' }, 123))
      .toBe('https://oss/image.jpg?signature=1&_=123')
  })

  it('keeps inline and object image URLs intact during refresh', () => {
    const inline = 'data:image/svg+xml;charset=utf-8,%3Csvg%2F%3E'
    const blob = 'blob:https://example.test/123'
    expect(resolveImageUrl({ img_url: inline }, 42, '/proxy')).toBe(inline)
    expect(resolveImageUrl({ ossUrl: blob }, 42, '/proxy')).toBe(blob)
  })

  it('resolves content gateway URLs against the production root base URL', () => {
    expect(resolveImageUrl({ img_url: '/api/v1/img/content/5' }, undefined, '/'))
      .toBe('/api/v1/img/content/5')
  })

  it('routes content gateway URLs through the development proxy and keeps the cache buster', () => {
    expect(resolveImageUrl({ img_url: '/api/v1/img/content/5' }, 42, '/proxy'))
      .toBe('/proxy/api/v1/img/content/5?_=42')
  })

  it('does not prefix absolute image source URLs returned in direct mode', () => {
    expect(resolveImageUrl({ img_url: 'http://10.0.0.11/ba-img/2024.04/24.04.30/1-2/0001.jpg' }, undefined, '/proxy'))
      .toBe('http://10.0.0.11/ba-img/2024.04/24.04.30/1-2/0001.jpg')
  })

  it('routes protected relative image URLs through the supplied API base URL', () => {
    expect(resolveImageUrl(
      { img_url: '/api/v1/external/archive/image/123' },
      undefined,
      '/proxy',
    )).toBe('/proxy/api/v1/external/archive/image/123')
  })
})
