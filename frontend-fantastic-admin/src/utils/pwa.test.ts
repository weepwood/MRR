import { describe, expect, it } from 'vitest'
import { buildPwaServiceWorkerUrl } from './pwa'

describe('PWA service worker URL', () => {
  it('uses the deployment build id to force update checks', () => {
    expect(buildPwaServiceWorkerUrl('https://mrr.example', '0.8.0-abc123-2026-07-24')).toBe(
      'https://mrr.example/sw.js?v=0.8.0-abc123-2026-07-24',
    )
  })

  it('encodes build identifiers without changing the service worker scope path', () => {
    expect(buildPwaServiceWorkerUrl('https://mrr.example:8002', 'release/0.8 中文')).toBe(
      'https://mrr.example:8002/sw.js?v=release%2F0.8+%E4%B8%AD%E6%96%87',
    )
  })
})
