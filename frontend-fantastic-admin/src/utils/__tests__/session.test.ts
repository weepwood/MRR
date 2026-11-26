import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as session from '../session'

const store = vi.hoisted(() => ({
  isSessionVerified: false,
  profile: { username: 'synthetic', displayName: '测试', roleCode: 'ADMIN', roleName: '管理员' },
  permissions: ['record:edit'],
  requestLogout: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => store }))

beforeEach(() => {
  store.isSessionVerified = false
  store.profile = { username: 'synthetic', displayName: '测试', roleCode: 'ADMIN', roleName: '管理员' }
  store.permissions = ['record:edit']
  store.requestLogout.mockReset().mockResolvedValue(undefined)
})

describe('会话展示及前端操作权限契约', () => {
  it('未经验证的旧身份不提供显示信息或操作权限', () => {
    expect(session.getCurrentUser()).toEqual({})
    expect(session.getUserDisplayName()).toBe('')
    expect(session.getUserRoleName()).toBe('')
    expect(session.isAdminUser()).toBe(false)
    expect(session.hasPermission('record:edit')).toBe(false)
    expect(session.hasAnyPermission(['record:read'])).toBe(false)
    expect(session.isAuthenticated()).toBe(false)
  })

  it('验证后显示当前身份，普通角色只继承声明的权限', () => {
    store.isSessionVerified = true
    store.profile.roleCode = 'DOCTOR'
    store.profile.roleName = ''
    store.profile.displayName = ''
    expect(session.getCurrentUser()).toEqual(store.profile)
    expect(session.getUserDisplayName()).toBe('synthetic')
    expect(session.getUserRoleName()).toBe('DOCTOR')
    expect(session.isAdminUser()).toBe(false)
    expect(session.hasPermission('record:read')).toBe(true)
    expect(session.hasPermission('system:manage')).toBe(false)
    expect(session.hasAnyPermission(['system:manage', 'record:read'])).toBe(true)
    expect(session.hasAnyPermission(['system:manage'])).toBe(false)
    expect(session.isAuthenticated()).toBe(true)
  })

  it('已验证管理员展示正确身份，退出委托现有会话注销机制', () => {
    store.isSessionVerified = true
    expect(session.getUserDisplayName()).toBe('测试')
    expect(session.getUserRoleName()).toBe('管理员')
    expect(session.isAdminUser()).toBe(true)
    session.clearSession()
    expect(store.requestLogout).toHaveBeenCalledOnce()
  })
})
