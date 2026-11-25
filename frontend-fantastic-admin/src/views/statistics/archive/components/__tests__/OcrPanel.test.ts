import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import OcrPanel from '../OcrPanel.vue'

const api = vi.hoisted(() => ({
  getOcrFact: vi.fn(),
  getOcrJobs: vi.fn(),
  createOcrJob: vi.fn(),
  cancelOcrJob: vi.fn(),
  getOcrJob: vi.fn(),
}))
vi.mock('@/api/modules/ocr', () => api)
const permissions = reactive({ read: true, text: true })
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: (p: string) => p === 'ocr:read' ? permissions.read : p === 'ocr:text-read' ? permissions.text : true }) }))
vi.mock('../ClassificationReview.vue', () => ({ default: { template: '<div />' } }))
const session = reactive({ token: 'synthetic-a' })
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))

function panel() {
  return mount(OcrPanel, {
    props: { scanId: 11, unclassified: true, expectedArchiveId: 1 },
    global: {
      stubs: {
        'el-config-provider': { template: '<div><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'ElDrawer': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
        'el-alert': { props: ['title'], template: '<p>{{ title }}</p>' },
        'el-tag': true,
        'el-progress': true,
      },
    },
  })
}

describe('病案工作台 OCR 身份隔离', () => {
  beforeEach(() => { permissions.read = true; permissions.text = true; vi.resetAllMocks() })
  it('拒绝属于其他病案的 OCR 内容并禁止基于错误身份创建任务', async () => {
    vi.clearAllMocks()
    api.getOcrFact.mockResolvedValue({ data: { scanId: 11, archiveId: 2, status: 'SUCCEEDED', text: '不得显示的合成文本' } })
    const wrapper = panel()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('无法读取文字识别结果')
    expect(wrapper.get('textarea').element.value).toBe('')
    expect(api.getOcrJobs).not.toHaveBeenCalled()
    await (wrapper.vm as any).run(false)
    expect(api.createOcrJob).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('内嵌面板切换已选影像时保留展开状态但不接受旧文本', async () => {
    vi.clearAllMocks()
    let resolve!: (value: unknown) => void
    api.getOcrFact.mockImplementationOnce(() => new Promise(r => resolve = r))
    const wrapper = panel()
    await wrapper.setProps({ inline: true })
    await wrapper.get('button').trigger('click')
    api.getOcrFact.mockResolvedValueOnce({ data: { scanId: 12, archiveId: 1, status: 'SUCCEEDED', text: '新影像合成文本' } })
    api.getOcrJobs.mockResolvedValue({ data: [] })
    await wrapper.setProps({ scanId: 12 })
    await flushPromises()
    resolve({ data: { scanId: 11, archiveId: 1, status: 'SUCCEEDED', text: '旧影像合成文本' } })
    await flushPromises()
    expect(wrapper.get('section[aria-label="影像文字识别"]').exists()).toBe(true)
    expect(wrapper.get('textarea').element.value).toBe('新影像合成文本')
    await wrapper.findAll('button').find(b => b.text() === '收起识别')!.trigger('click')
    expect(wrapper.find('textarea').exists()).toBe(false)
    wrapper.unmount()
  })

  it('外置工具栏仅在明确打开且有权限时读取OCR，收起释放状态', async () => {
    api.getOcrFact.mockResolvedValue({ data: { scanId: 11, archiveId: 1, status: 'SUCCEEDED', text: '合成原文' } })
    api.getOcrJobs.mockResolvedValue({ data: [] })
    const wrapper = panel()
    await wrapper.setProps({ inline: true, externalTrigger: true })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(api.getOcrFact).not.toHaveBeenCalled()
    ;(wrapper.vm as any).togglePanel()
    await flushPromises()
    expect(wrapper.emitted('openChange')?.at(-1)).toEqual([true])
    expect(wrapper.get('textarea').element.value).toBe('合成原文')
    ;(wrapper.vm as any).togglePanel()
    await flushPromises()
    expect(wrapper.emitted('openChange')?.at(-1)).toEqual([false])
    expect(wrapper.find('textarea').exists()).toBe(false)
    permissions.read = false
    await flushPromises()
    ;(wrapper.vm as any).togglePanel()
    await flushPromises()
    expect(api.getOcrFact).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('任务身份不一致时禁止继续发起识别，文本权限撤销立即隐藏内容', async () => {
    api.getOcrFact.mockResolvedValue({ data: { scanId: 11, archiveId: 1, status: 'SUCCEEDED', text: '合成原文' } })
    api.getOcrJobs.mockResolvedValue({ data: [{ id: 'task', archiveId: 1 }] })
    api.getOcrJob.mockResolvedValue({ data: { id: 'task', archiveId: 2, status: 'RUNNING' } })
    const wrapper = panel()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('任务身份不一致')
    await (wrapper.vm as any).run(false)
    expect(api.createOcrJob).not.toHaveBeenCalled()
    permissions.text = false
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(api.getOcrFact).toHaveBeenLastCalledWith(11, false)
    permissions.read = false
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })

  it('会话变更关闭 OCR 面板并丢弃迟到结果', async () => {
    vi.clearAllMocks()
    let resolve!: (value: unknown) => void
    api.getOcrFact.mockImplementationOnce(() => new Promise(r => resolve = r))
    const wrapper = panel()
    await wrapper.get('button').trigger('click')
    session.token = 'synthetic-b'
    resolve({ data: { scanId: 11, archiveId: 1, status: 'SUCCEEDED', text: '旧会话合成文本' } })
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(api.getOcrJobs).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
