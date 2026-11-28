import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'
import { createAdditionalMockUsers, createBaseMockUsers, filterMockUsers, orderMockUsers } from './users'

describe('synthetic user list', () => {
  const clock = createDemoClock(new Date('2026-11-26T00:00:00Z'))
  const users = [...createBaseMockUsers(clock), ...createAdditionalMockUsers(clock)]
  it('preserves original identities and includes all states on the first page', () => {
    expect(users).toHaveLength(28)
    expect(users.slice(0, 3).map(row => row.username)).toEqual(['admin', 'auditor', 'operator'])
    expect(new Set(users.map(row => row.id)).size).toBe(28)
    expect(new Set(users.map(row => row.username)).size).toBe(28)
    expect(['PENDING', 'ACTIVE', 'REJECTED', 'DISABLED'].map(status => filterMockUsers(orderMockUsers(users).slice(0, 20), { status }).length)).toEqual([6, 9, 3, 2])
    expect(['PENDING', 'ACTIVE', 'REJECTED', 'DISABLED'].map(status => filterMockUsers(users, { status }).length)).toEqual([6, 13, 4, 5])
  })
  it('orders pending globally before pagination and uses deterministic descending dates and IDs', () => {
    const ordered = orderMockUsers(users)
    expect(ordered.slice(0, 6).map(row => row.status)).toEqual(Array.from({ length: 6 }).fill('PENDING'))
    expect(ordered.slice(6).every(row => row.status !== 'PENDING')).toBe(true)
    expect(ordered.slice(0, 6).map(row => row.username)).toEqual(['apply.archive', 'apply.audit', 'apply.longtext', 'apply.contact', 'apply.extra01', 'apply.extra02'])
    expect(users[0]?.username).toBe('admin')
    const tied = [{ ...users[0]!, id: 101 }, { ...users[0]!, id: 102 }]
    expect(orderMockUsers(tied).map(row => row.id)).toEqual([102, 101])
  })
  it('searches contact and role, preserves long applications and credential edge cases', () => {
    expect(filterMockUsers(users, { keyword: 'apply.longtext@example.invalid' })).toHaveLength(1)
    expect(filterMockUsers(users, { keyword: '审计员', status: 'disabled' })).toHaveLength(2)
    expect(users.find(row => row.username === 'apply.longtext')?.applyRemark).toContain('\n\n')
    expect(users.find(row => row.username === 'reject.incomplete')?.rejectReason).toBeTruthy()
    expect(users.find(row => row.username === 'apply.contact')?.applyRemark).toBeUndefined()
    expect(Date.parse(users.find(row => row.username === 'expired.demo')!.temporaryPasswordExpiresAt!)).toBeLessThan(Date.parse(clock.iso()))
    expect(users.filter(row => row.status !== 'ACTIVE').every(row => row.permissions.length === 0)).toBe(true)
  })
})
