import { describe, expect, it } from 'vitest'
import {
  DEFAULT_LOGIN_PAGE_SETTINGS,
  normalizeLoginPageSettings,
} from '../login-page-settings'

describe('login page settings', () => {
  it('uses configured branding and falls back for blank values', () => {
    const settings = normalizeLoginPageSettings({
      systemName: '医院病案影像平台',
      systemShortName: '影像平台',
      systemEnglishName: '   ',
      loginFeatureEnabled: 'false',
    })

    expect(settings.systemName).toBe('医院病案影像平台')
    expect(settings.systemShortName).toBe('影像平台')
    expect(settings.systemEnglishName).toBe(DEFAULT_LOGIN_PAGE_SETTINGS.systemEnglishName)
    expect(settings.loginFeatureEnabled).toBe(false)
  })

  it('keeps administrator contact hidden unless the public endpoint marks it visible', () => {
    const hidden = normalizeLoginPageSettings({
      systemAdminPhone: '0571-12345678',
      systemAdminContactVisible: 'false',
    })
    const visible = normalizeLoginPageSettings({
      systemAdminContactVisible: 'true',
      systemAdminDisplayName: 'MRR 运维组',
      systemAdminPhone: '0571-12345678',
    })

    expect(hidden.systemAdminContactVisible).toBe(false)
    expect(visible.systemAdminContactVisible).toBe(true)
    expect(visible.systemAdminDisplayName).toBe('MRR 运维组')
    expect(visible.systemAdminPhone).toBe('0571-12345678')
  })
})
