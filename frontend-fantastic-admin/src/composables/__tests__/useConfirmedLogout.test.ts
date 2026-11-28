import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cancelActionDialogs, currentActionDialog, finishActionDialog } from '@/ui/components/ActionDialog'
import { useConfirmedLogout } from '../useConfirmedLogout'

const user = vi.hoisted(() => ({ isLogin: true, sessionEpoch: 1, logout: vi.fn(async (_redirect?: string) => {}) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => user }))

beforeEach(() => {
  user.isLogin = true
  user.sessionEpoch = 1
  user.logout.mockClear()
})
afterEach(cancelActionDialogs)

it('取消不退出，确认后只退出一次', async () => {
  const { confirmLogout, logoutPending } = useConfirmedLogout()
  const cancelled = confirmLogout('/')
  expect(logoutPending.value).toBe(true)
  expect(user.logout).not.toHaveBeenCalled()
  finishActionDialog(currentActionDialog.value!)
  await cancelled
  expect(logoutPending.value).toBe(false)
  expect(user.logout).not.toHaveBeenCalled()
  const confirmed = confirmLogout('/')
  await confirmLogout('/')
  expect(currentActionDialog.value?.options.confirmButtonType).toBe('danger')
  finishActionDialog(currentActionDialog.value!, '')
  await confirmed
  expect(user.logout).toHaveBeenCalledExactlyOnceWith('/')
  expect(logoutPending.value).toBe(false)
  expect(currentActionDialog.value).toBeUndefined()
})

it('旧会话的确认不能退出替换后的会话', async () => {
  const { confirmLogout } = useConfirmedLogout()
  const request = confirmLogout()
  user.sessionEpoch++
  finishActionDialog(currentActionDialog.value!, '')
  await request
  expect(user.logout).not.toHaveBeenCalled()
})

it('匿名会话不创建退出确认', async () => {
  user.isLogin = false
  await useConfirmedLogout().confirmLogout()
  expect(currentActionDialog.value).toBeUndefined()
  expect(user.logout).not.toHaveBeenCalled()
})
