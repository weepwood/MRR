import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildExternalArchiveCanonicalText,
  createExternalArchiveSignature,
  sha256Hex,
} from '../external-archive-signature'

describe('external archive signature', () => {
  const rawBody = '{"externalUserId":"HIS-USER-1","bah":"1","allowDownload":false}'

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('calculates the SHA-256 hash of the exact raw request body', async () => {
    await expect(sha256Hex(rawBody))
      .resolves.toBe('bc55bf7f1b857edbc1fd47cb30e8bec473a854d3a04bdbc144e6192248279973')
  })

  it('builds the canonical text in the backend-defined order', () => {
    expect(buildExternalArchiveCanonicalText({
      method: 'post',
      path: '/api/v1/integration/archive/tickets',
      timestamp: '1784383200',
      nonce: 'nonce-123',
      rawBody,
      bodyHash: 'bc55bf7f1b857edbc1fd47cb30e8bec473a854d3a04bdbc144e6192248279973',
    })).toBe([
      'POST',
      '/api/v1/integration/archive/tickets',
      '1784383200',
      'nonce-123',
      'bc55bf7f1b857edbc1fd47cb30e8bec473a854d3a04bdbc144e6192248279973',
    ].join('\n'))
  })

  it('matches the HMAC-SHA256 test vector used by external clients', async () => {
    const result = await createExternalArchiveSignature({
      method: 'POST',
      path: '/api/v1/integration/archive/tickets',
      timestamp: '1784383200',
      nonce: 'nonce-123',
      rawBody,
      secret: 'test-secret',
    })

    expect(result.signature)
      .toBe('c91f433ca3754cb682844e59d27c8c2a9aeb9e6dbbf6b394710ebafceaf6a6e8')
  })

  it('uses the pure JavaScript fallback when crypto.subtle is unavailable', async () => {
    vi.stubGlobal('crypto', {})

    const result = await createExternalArchiveSignature({
      method: 'POST',
      path: '/api/v1/integration/archive/tickets',
      timestamp: '1784383200',
      nonce: 'nonce-123',
      rawBody,
      secret: 'test-secret',
    })

    expect(result.bodyHash)
      .toBe('bc55bf7f1b857edbc1fd47cb30e8bec473a854d3a04bdbc144e6192248279973')
    expect(result.signature)
      .toBe('c91f433ca3754cb682844e59d27c8c2a9aeb9e6dbbf6b394710ebafceaf6a6e8')
  })
})
