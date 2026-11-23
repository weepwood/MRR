import { describe, expect, it } from 'vitest'
import { imageFetchInit } from '../image-fetch'

describe('client image fetch credentials', () => {
  it('sends same-origin credentials to the content gateway so the image access cookie is used', () => {
    const init = imageFetchInit(new URL('http://mrr.local/api/v1/img/content/5'), 'http://mrr.local')

    expect(init).toEqual({ method: 'GET', mode: 'same-origin', credentials: 'same-origin' })
  })

  it('never sends credentials to cross-origin image sources', () => {
    const nginx = imageFetchInit(new URL('http://10.0.0.11/ba-img/0001.jpg'), 'http://mrr.local')
    const oss = imageFetchInit(new URL('https://bucket.oss.example/0001.jpg?signature=x'), 'http://mrr.local')

    expect(nginx.credentials).toBe('omit')
    expect(oss.credentials).toBe('omit')
    expect(oss.mode).toBe('cors')
  })

  it('treats a different port as a different origin', () => {
    expect(imageFetchInit(new URL('http://mrr.local:18045/api/v1/img/content/5'), 'http://mrr.local').credentials)
      .toBe('omit')
  })
})
