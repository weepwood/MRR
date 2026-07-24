import { describe, expect, it } from 'vitest'
import {
  DEFAULT_DOCUMENTATION_SETTINGS,
  normalizeDocumentationSettings,
} from '../documentation-settings'

describe('documentation settings', () => {
  it('normalizes safe configured links', () => {
    expect(normalizeDocumentationSettings({
      documentationUserGuideUrl: ' https://docs.example.test/user/ ',
      documentationDeveloperUrl: '/development/',
      documentationOperationsUrl: '',
    })).toEqual({
      documentationUserGuideUrl: 'https://docs.example.test/user/',
      documentationDeveloperUrl: '/development/',
      documentationOperationsUrl: '',
    })
  })

  it('falls back when a public response contains unsafe links', () => {
    expect(normalizeDocumentationSettings({
      documentationUserGuideUrl: 'javascript:alert(1)',
      documentationDeveloperUrl: '//docs.example.test/internal',
      documentationOperationsUrl: 'https://user:secret@example.test/operations',
    })).toEqual(DEFAULT_DOCUMENTATION_SETTINGS)
  })
})
