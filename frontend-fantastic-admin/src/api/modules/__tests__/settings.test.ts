import { describe, expect, it, vi } from 'vitest'
import {
  deleteSetting,
  getSettingsSchema,
  getUiSettings,
  saveSystemSettings,
} from '../settings'

vi.mock('../../index', () => ({
  getRequest: vi.fn(),
  putRequest: vi.fn(),
  deleteRequest: vi.fn(),
}))

const { getRequest, putRequest, deleteRequest } = await import('../../index')

describe('settings api', () => {
  it('requests the runtime setting schema from the dedicated endpoint', () => {
    getSettingsSchema()

    expect(getRequest).toHaveBeenCalledWith('/api/v1/settings/schema')
  })

  it('requests global ui settings from the login-only endpoint', () => {
    getUiSettings()

    expect(getRequest).toHaveBeenCalledWith('/api/v1/settings/ui')
  })

  it('saves settings through the batch update endpoint', () => {
    saveSystemSettings({ loginMaxAttempts: '8' })

    expect(putRequest).toHaveBeenCalledWith('/api/v1/settings', { loginMaxAttempts: '8' })
  })

  it('clears a database override by deleting the single key', () => {
    deleteSetting('sessionExpireMinutes')

    expect(deleteRequest).toHaveBeenCalledWith('/api/v1/settings/sessionExpireMinutes')
  })

  it('encodes keys that contain reserved characters', () => {
    deleteSetting('weird/key ?')

    expect(deleteRequest).toHaveBeenCalledWith('/api/v1/settings/weird%2Fkey%20%3F')
  })
})
