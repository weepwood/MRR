import { describe, expect, it } from 'vitest'
import { shouldLogoutForUnauthorizedResponse } from '@/utils/unauthorized-session'

describe('unauthorized response session handling', () => {
  it('does not redirect an anonymous caller to login', () => {
    expect(shouldLogoutForUnauthorizedResponse(false)).toBe(false)
  })

  it('redirects a caller with a login session to login', () => {
    expect(shouldLogoutForUnauthorizedResponse(true)).toBe(true)
  })
})
