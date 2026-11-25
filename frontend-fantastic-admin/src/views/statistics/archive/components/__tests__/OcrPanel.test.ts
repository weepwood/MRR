import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
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
        'el-drawer': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
        'el-alert': { props: ['title'], template: '<p>{{ title }}</p>' },
        'el-tag': true,
        'el-progress': true,
      },
    },
  })
}

describe('病案工作台 OCR 身份隔离', () => {
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
