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

  it('routes protected relative image URLs through the supplied API base URL', () => {
    expect(resolveImageUrl(
      { img_url: '/api/v1/external/archive/image/123' },
      undefined,
      '/proxy',
    )).toBe('/proxy/api/v1/external/archive/image/123')
  })
})
