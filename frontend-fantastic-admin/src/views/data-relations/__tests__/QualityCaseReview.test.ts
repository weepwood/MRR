import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import QualityCaseReview from '../components/QualityCaseReview.vue'

const api = vi.hoisted(() => ({ getQualityReview: vi.fn(), listQualityReviews: vi.fn(), getQualityReviewEvents: vi.fn(), decideQualityReview: vi.fn(), recheckQualityReview: vi.fn() }))
const confirm = vi.hoisted(() => vi.fn())
vi.mock('@/api/modules/quality-case-review', () => api)
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))
const session = reactive({ token: 'synthetic', allowed: true })
vi.mock('@/utils/session', () => ({ hasPermission: () => session.allowed }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))
const old = { id: '1', entityType: 'mr_scan', entityId: '4', checkCode: 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED', issueId: '10', runId: '1', status: 'CONFIRMED', revision: 1, actorId: '11', updatedAt: '2026-11-01T00:00:00Z' }
function ctx(review = old, reviewedThisSample = false) {
  return { enabled: true, latest: true, reviewedThisSample, review, events: [] }
}
function panel(issueId?: number) {
  return mount(QualityCaseReview, { props: { issueId }, global: { stubs: {
    'MrrSectionCard': { template: '<section><slot name="actions" /><slot /></section>' },
    'AppError': { props: ['message'], template: '<p>{{ message }}</p>' },
    'AppEmpty': { props: ['description'], template: '<p>{{ description }}</p>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-table': { props: ['data'], emits: ['row-click'], template: '<div><button v-for="row in data" :key="row.id" @click="$emit(\'row-click\',row,{})">案件 {{ row.id }}</button><slot /></div>' },
    'el-table-column': true,
    'el-timeline': { template: '<div><slot /></div>' },
    'el-timeline-item': { template: '<div><slot /></div>' },
  } } })
}
const button = (w: ReturnType<typeof panel>, label: string) => w.findAll('button').find(b => b.text() === label)!
describe('异常人工决策', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    session.allowed = true
    session.token = 'synthetic'
    confirm.mockResolvedValue(undefined)
  })
  it('旧样本决策不等于本次处理，明确确认并提交原版本', async () => {
    api.getQualityReview.mockResolvedValueOnce({ data: ctx() })
    const w = panel(20)
    await flushPromises()
    expect(w.text()).toContain('本次样本尚未记录决策')
    expect(w.text()).toContain('不代表本次异常已处理')
    confirm.mockRejectedValueOnce('cancel')
    await button(w, '确认异常').trigger('click')
    await flushPromises()
    expect(api.decideQualityReview).not.toHaveBeenCalled()
    const next = { ...old, issueId: '20', runId: '2', revision: 2 }
    api.getQualityReview.mockResolvedValueOnce({ data: ctx(next, true) })
    await button(w, '确认异常').trigger('click')
    await flushPromises()
    expect(api.decideQualityReview).toHaveBeenCalledExactlyOnceWith(20, 1, 'CONFIRM')
    expect(w.text()).toContain('本次样本已有决策')
    expect(button(w, '确认异常').attributes('disabled')).toBeDefined()
    w.unmount()
  })
  it('切换异常取消旧请求，权限撤销清空，历史样本不可写', async () => {
    let resolve!: (data: unknown) => void
    api.getQualityReview.mockImplementationOnce(() => new Promise(r => resolve = r))
    const w = panel(20)
    const signal = api.getQualityReview.mock.calls[0][1] as AbortSignal
    api.getQualityReview.mockResolvedValueOnce({ data: { ...ctx(), latest: false } })
    await w.setProps({ issueId: 21 })
    await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve({ data: ctx() })
    await flushPromises()
    expect(w.text()).toContain('历史样本（只读）')
    expect(button(w, '确认异常')).toBeUndefined()
    session.allowed = false
    await flushPromises()
    expect(w.text()).toBe('')
    w.unmount()
  })
  it('逐实体复验展示时点结果，不更改案件或提交决策，并拒绝过期版本', async () => {
    api.getQualityReview.mockResolvedValue({ data: ctx() })
    const w = panel(20)
    await flushPromises()
    api.recheckQualityReview.mockResolvedValueOnce({ data: { caseId: '1', revision: 1, status: 'LINK_PRESENT_VERIFIED', observedAt: '2026-11-25T00:00:00Z' } })
    await button(w, '逐记录只读复验').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('当前唯一关联已核对')
    expect(w.text()).toContain('不关闭案件')
    expect(api.decideQualityReview).not.toHaveBeenCalled()
    api.recheckQualityReview.mockResolvedValueOnce({ data: { caseId: '1', revision: 2, status: 'LINK_PRESENT_VERIFIED', observedAt: '2026-11-25T00:00:00Z' } })
    await button(w, '逐记录只读复验').trigger('click')
    await flushPromises()
    expect(w.text()).not.toContain('当前唯一关联已核对')
    expect(w.text()).toContain('决策读取或保存失败')
    w.unmount()
  })
  it('案件历史列表可打开审计，样本清理后仍独立展示', async () => {
    api.listQualityReviews.mockResolvedValue({ data: { enabled: false, reviews: [old] } })
    api.getQualityReviewEvents.mockResolvedValue({ data: [{ revision: 1, issueId: '10', runId: '1', status: 'CONFIRMED', reason: 'CONFIRMED_EVIDENCE', actorId: '11', occurredAt: '2026-11-01T00:00:00Z' }] })
    const w = panel()
    await flushPromises()
    await button(w, '案件 1').trigger('click')
    await flushPromises()
    expect(api.getQualityReviewEvents.mock.calls[0].slice(0, 2)).toEqual(['1', 0])
    expect(w.text()).toContain('样本 #10 · 操作者 #11')
    session.token = 'other'
    await flushPromises()
    expect(w.text()).not.toContain('样本 #10')
    w.unmount()
  })
})
