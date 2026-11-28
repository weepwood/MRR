import type { CredentialAwareUser } from '../api/user-credential-types'
import { demoClock } from './clock'

export interface MockUser extends CredentialAwareUser {
  id: number
  username: string
  displayName: string
  roleCode: string
  roleName: string
  permissions: string[]
  status: string
}

type Clock = typeof demoClock

// Preserve the original three login/audit fixtures. Additional accounts are
// synthetic list samples, not real identities or usable credentials.
export function createBaseMockUsers(clock: Clock = demoClock): MockUser[] {
  return [
    {
      id: 1,
      username: 'admin',
      displayName: '系统管理员',
      roleCode: 'ADMIN',
      roleName: '管理员',
      permissions: ['*'],
      status: 'ACTIVE',
      lastLoginAt: clock.iso(),
      createdAt: clock.iso(5),
    },
    {
      id: 2,
      username: 'auditor',
      displayName: '审计员',
      roleCode: 'AUDITOR',
      roleName: '审计员',
      permissions: ['audit:read', 'logs:read', 'statistics:read'],
      status: 'ACTIVE',
      lastLoginAt: clock.iso(1),
      createdAt: clock.iso(6),
    },
    {
      id: 3,
      username: 'operator',
      displayName: '扫描操作员',
      roleCode: 'OPERATOR',
      roleName: '操作员',
      permissions: ['scan:read', 'scan:write', 'statistics:read'],
      status: 'ACTIVE',
      lastLoginAt: clock.iso(2),
      createdAt: clock.iso(7),
    },
  ]
}

export function createAdditionalMockUsers(clock: Clock = demoClock): MockUser[] {
  const roles = {
    OPERATOR: { roleName: '操作员', permissions: ['scan:read', 'scan:write', 'statistics:read'] },
    AUDITOR: { roleName: '审计员', permissions: ['audit:read', 'logs:read', 'statistics:read'] },
    ADMIN: { roleName: '管理员', permissions: ['*'] },
  }
  const definitions: [string, string, 'PENDING' | 'ACTIVE' | 'REJECTED' | 'DISABLED', keyof typeof roles][] = [
    ['apply.archive', '档案申请演示', 'PENDING', 'OPERATOR'],
    ['scan.demo01', '扫描业务演示一', 'ACTIVE', 'OPERATOR'],
    ['disabled.archive', '离岗账号演示', 'DISABLED', 'OPERATOR'],
    ['apply.audit', '审计申请演示', 'PENDING', 'OPERATOR'],
    ['reject.incomplete', '资料不全演示', 'REJECTED', 'OPERATOR'],
    ['audit.demo01', '审计业务演示一', 'ACTIVE', 'AUDITOR'],
    ['initial.demo', '初始密码待修改', 'ACTIVE', 'OPERATOR'],
    ['apply.longtext', '长说明申请演示', 'PENDING', 'OPERATOR'],
    ['disabled.audit', '停用审计账号', 'DISABLED', 'AUDITOR'],
    ['reject.scope', '权限范围拒绝演示', 'REJECTED', 'OPERATOR'],
    ['reset.demo', '密码重置演示', 'ACTIVE', 'OPERATOR'],
    ['expired.demo', '临时密码过期演示', 'ACTIVE', 'OPERATOR'],
    ['apply.contact', '仅联系方式申请', 'PENDING', 'OPERATOR'],
    ['scan.demo02', '扫描业务演示二', 'ACTIVE', 'OPERATOR'],
    ['disabled.old', '历史停用账号', 'DISABLED', 'OPERATOR'],
    ['reject.review', '审核拒绝演示', 'REJECTED', 'OPERATOR'],
    ['admin.demo', '管理业务演示', 'ACTIVE', 'ADMIN'],
    ['apply.extra01', '补充申请演示一', 'PENDING', 'OPERATOR'],
    ['audit.demo02', '审计业务演示二', 'ACTIVE', 'AUDITOR'],
    ['disabled.extra01', '停用演示账号二', 'DISABLED', 'OPERATOR'],
    ['reject.extra', '重复申请演示', 'REJECTED', 'OPERATOR'],
    ['scan.demo03', '扫描业务演示三', 'ACTIVE', 'OPERATOR'],
    ['apply.extra02', '补充申请演示二', 'PENDING', 'OPERATOR'],
    ['disabled.extra02', '停用演示账号三', 'DISABLED', 'AUDITOR'],
    ['scan.demo04', '扫描业务演示四', 'ACTIVE', 'OPERATOR'],
  ]
  return definitions.map(([username, displayName, status, roleCode], index) => {
    const registration = status === 'PENDING' || status === 'REJECTED' || username === 'scan.demo01'
    const days = 2 + index
    const row: MockUser = {
      id: 101 + index,
      username,
      displayName,
      status,
      roleCode,
      roleName: roles[roleCode].roleName,
      permissions: status === 'ACTIVE' ? [...roles[roleCode].permissions] : [],
      createdAt: clock.iso(days + 8),
      lastLoginAt: status === 'ACTIVE' || status === 'DISABLED' ? clock.iso(days) : undefined,
    }
    if (registration) {
      row.appliedAt = clock.iso(days + 7)
      row.contactInfo = `${username}@example.invalid`
      row.applyRemark = username === 'apply.contact' ? undefined : '【模拟】申请使用病案管理系统，供业务操作与权限审核演示。'
      if (username === 'apply.longtext') {
        row.applyRemark = [
          '【模拟】申请访问档案工作台，用于日常扫描记录核对与资料整理。',
          '工作范围：查看模拟病案目录、核查上架状态、记录缺失项并反馈。',
          '使用说明：仅在演示环境验证，不涉及真实患者、医疗数据或实际授权。',
          `补充说明：${'此段为长文本展示测试，点击申请摘要可以阅读完整内容。'.repeat(12)}`,
        ].join('\n\n')
      }
      if (status !== 'PENDING') {
        row.reviewedAt = clock.iso(days + 5)
        row.reviewedBy = 1
      }
      if (status === 'REJECTED') {
        row.rejectReason = '【模拟】申请资料尚不完整，请补充使用范围和必要说明后重新申请。'
      }
    }
    if (['initial.demo', 'reset.demo', 'expired.demo'].includes(username)) {
      row.mustChangePassword = true
      row.passwordVersion = username === 'reset.demo' ? 2 : 1
      row.lastLoginAt = undefined
      row.temporaryPasswordExpiresAt = username === 'expired.demo'
        ? clock.iso(1)
        : new Date(Date.parse(clock.iso()) + 72 * 3_600_000).toISOString()
      if (username === 'reset.demo') {
        row.passwordResetAt = clock.iso()
        row.passwordResetBy = 1
      }
    }
    return row
  })
}

export function filterMockUsers(users: MockUser[], filters: { keyword?: string, roleCode?: string, status?: string }) {
  const normalize = (value: unknown) => String(value ?? '').trim().toLowerCase()
  const keyword = normalize(filters.keyword)
  const roleCode = normalize(filters.roleCode)
  const status = normalize(filters.status)
  return users.filter(row =>
    (!keyword || normalize([row.username, row.displayName, row.contactInfo, row.roleName, row.roleCode].join(' ')).includes(keyword))
    && (!roleCode || normalize(row.roleCode) === roleCode)
    && (!status || normalize(row.status) === status),
  )
}

// Match AuthUserMapper.findAllWithPagination: pending first, then application
// or creation time descending and id descending. Sort the full filtered list
// before slicing a page, never reorder the visible page only.
export function orderMockUsers(users: MockUser[]) {
  const priority = (row: MockUser) => row.status.toLowerCase() === 'pending' ? 0 : 1
  const time = (row: MockUser) => {
    const value = Date.parse(row.appliedAt ?? row.createdAt ?? '')
    return Number.isFinite(value) ? value : Number.POSITIVE_INFINITY
  }
  return [...users].sort((a, b) => {
    const group = priority(a) - priority(b)
    if (group) { return group }
    const first = time(a)
    const second = time(b)
    return (first === second ? 0 : first > second ? -1 : 1) || b.id - a.id
  })
}
