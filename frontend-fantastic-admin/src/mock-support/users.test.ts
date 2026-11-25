import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'
import { createAdditionalMockUsers, createBaseMockUsers, filterMockUsers } from './users'

describe('synthetic user list', () => {
  const clock = createDemoClock(new Date('2026-11-26T00:00:00Z'))
  const users = [...createBaseMockUsers(clock), ...createAdditionalMockUsers(clock)]
  it('preserves original identities and includes all states on the first page', () => {
    expect(users).toHaveLength(28)
    expect(users.slice(0, 3).map(row => row.username)).toEqual(['admin', 'auditor', 'operator'])
    expect(new Set(users.map(row => row.id)).size).toBe(28)
    expect(new Set(users.map(row => row.username)).size).toBe(28)
    expect(['PENDING', 'ACTIVE', 'REJECTED', 'DISABLED'].map(status => filterMockUsers(users.slice(0, 20), { status }).length)).toEqual([4, 10, 3, 3])
    expect(['PENDING', 'ACTIVE', 'REJECTED', 'DISABLED'].map(status => filterMockUsers(users, { status }).length)).toEqual([6, 13, 4, 5])
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
