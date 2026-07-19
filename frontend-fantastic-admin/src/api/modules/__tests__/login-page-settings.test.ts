import { describe, expect, it } from 'vitest'
import {
  DEFAULT_LOGIN_PAGE_SETTINGS,
  normalizeLoginPageSettings,
  serializeLoginPageSettings,
} from '../login-page-settings'

describe('login page settings', () => {
  it('uses configured copy and falls back for blank values', () => {
    const settings = normalizeLoginPageSettings({
      loginBrandTitle: '医院病案影像平台',
      loginFormTitle: '   ',
    })

    expect(settings.loginBrandTitle).toBe('医院病案影像平台')
    expect(settings.loginFormTitle).toBe(DEFAULT_LOGIN_PAGE_SETTINGS.loginFormTitle)
    expect(settings.loginHelpText).toBe(DEFAULT_LOGIN_PAGE_SETTINGS.loginHelpText)
  })

  it('serializes only login-page copy fields', () => {
    const serialized = serializeLoginPageSettings({ ...DEFAULT_LOGIN_PAGE_SETTINGS })

    expect(serialized.loginBrandTitle).toBe('病案文件管理系统')
    expect(serialized.loginFormTitle).toBe('登录 MRR')
    expect(serialized).not.toHaveProperty('developerModeEnabled')
    expect(Object.keys(serialized)).toHaveLength(Object.keys(DEFAULT_LOGIN_PAGE_SETTINGS).length)
  })
})
