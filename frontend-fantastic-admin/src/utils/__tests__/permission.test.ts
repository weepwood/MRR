import { describe, expect, it } from 'vitest'
import {
  PERMISSION_HIERARCHY,
  checkAnyPermission,
  checkPermission,
  resolvePermissions,
} from '../permission'

describe('permission — PERMISSION_HIERARCHY', () => {
  it('record:manage 包含管理、编辑、查看与导出权限', () => {
    expect(PERMISSION_HIERARCHY['record:manage']).toEqual([
      'record:manage',
      'record:edit',
      'record:read',
      'record:download',
      'record:pdf:export',
    ])
  })

  it('record:edit 包含 edit+read', () => {
    expect(PERMISSION_HIERARCHY['record:edit']).toEqual(['record:edit', 'record:read'])
  })

  it('导出权限分别包含查看权限', () => {
    expect(PERMISSION_HIERARCHY['record:download']).toEqual(['record:download', 'record:read'])
    expect(PERMISSION_HIERARCHY['record:pdf:export']).toEqual(['record:pdf:export', 'record:read'])
  })

  it('role:manage 包含 manage+read', () => {
    expect(PERMISSION_HIERARCHY['role:manage']).toEqual(['role:manage', 'role:read'])
  })
})

describe('permission — resolvePermissions', () => {
  it('record:manage 展开为 5 个权限', () => {
    expect(resolvePermissions(['record:manage']).sort()).toEqual([
      'record:download',
      'record:edit',
      'record:manage',
      'record:pdf:export',
      'record:read',
    ])
  })

  it('非层级权限原样保留', () => {
    expect(resolvePermissions(['statistics:read', 'log:read'])).toEqual(['statistics:read', 'log:read'])
  })

  it('混合层级和非层级权限', () => {
    const result = resolvePermissions(['record:manage', 'statistics:read'])
    expect(result).toContain('record:manage')
    expect(result).toContain('record:edit')
    expect(result).toContain('record:read')
    expect(result).toContain('record:download')
    expect(result).toContain('record:pdf:export')
    expect(result).toContain('statistics:read')
  })

  it('空数组返回空数组', () => {
    expect(resolvePermissions([])).toEqual([])
  })

  it('去重：重复权限只保留一份', () => {
    const result = resolvePermissions(['record:read', 'record:read', 'record:edit'])
    expect(result.filter(p => p === 'record:read')).toHaveLength(1)
    expect(result.filter(p => p === 'record:edit')).toHaveLength(1)
  })
})

describe('permission — checkPermission', () => {
  it('拥有 record:manage 则通过查看和导出检查', () => {
    expect(checkPermission(['record:manage'], 'record:read')).toBe(true)
    expect(checkPermission(['record:manage'], 'record:download')).toBe(true)
    expect(checkPermission(['record:manage'], 'record:pdf:export')).toBe(true)
  })

  it('拥有 record:read 不能下载或导出 PDF', () => {
    expect(checkPermission(['record:read'], 'record:download')).toBe(false)
    expect(checkPermission(['record:read'], 'record:pdf:export')).toBe(false)
  })

  it('拥有 record:read 不通过 record:manage 检查', () => {
    expect(checkPermission(['record:read'], 'record:manage')).toBe(false)
  })

  it('拥有精确权限通过检查', () => {
    expect(checkPermission(['statistics:read'], 'statistics:read')).toBe(true)
  })

  it('缺少权限不通过检查', () => {
    expect(checkPermission(['record:read'], 'statistics:read')).toBe(false)
  })

  it('空权限列表不通过检查', () => {
    expect(checkPermission([], 'record:read')).toBe(false)
  })
})

describe('permission — checkAnyPermission', () => {
  it('拥有 targets 中的任一权限返回 true', () => {
    expect(checkAnyPermission(['record:read'], ['statistics:read', 'record:read'])).toBe(true)
  })

  it('不拥有 targets 中任何权限返回 false', () => {
    expect(checkAnyPermission(['log:read'], ['statistics:read', 'record:read'])).toBe(false)
  })

  it('空 targets 返回 false', () => {
    expect(checkAnyPermission(['record:read'], [])).toBe(false)
  })

  it('空用户权限返回 false', () => {
    expect(checkAnyPermission([], ['record:read'])).toBe(false)
  })
})
