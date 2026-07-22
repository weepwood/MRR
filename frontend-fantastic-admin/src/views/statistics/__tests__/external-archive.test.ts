import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ExternalArchivePage from '../external-archive.vue'

const api = vi.hoisted(() => ({
  clearExternalArchiveSession: vi.fn(),
  exchangeExternalArchiveTicket: vi.fn(),
  getExternalArchiveContext: vi.fn(),
  setExternalArchiveSession: vi.fn(),
}))

const router = vi.hoisted(() => ({
  back: vi.fn(),
  replace: vi.fn(),
}))

const route = vi.hoisted(() => ({
  query: {
    ticket: 'ticket-123',
  } as Record<string, string>,
}))

vi.mock('@/api/modules/external-archive', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

const session = {
  clientId: 'his',
  externalUserId: 'doctor-1',
  allowDownload: false,
  expiresIn: 600,
  cases: [
    {
      bah: '123456',
      patientName: '测试患者',
    },
  ],
}

const stubs = {
  'el-button': {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-result': {
    props: ['title', 'subTitle', 'icon'],
    template: '<section class="result-stub"><h1>{{ title }}</h1><p>{{ subTitle }}</p><slot name="extra" /></section>',
  },
}

function mountPage() {
  return mount(ExternalArchivePage, {
    global: { stubs },
  })
}

describe('external archive timeout recovery', () => {
  beforeEach(() => {
    sessionStorage.clear()
    route.query = { ticket: 'ticket-123' }
    api.clearExternalArchiveSession.mockReset()
    api.exchangeExternalArchiveTicket.mockReset()
    api.getExternalArchiveContext.mockReset()
    api.setExternalArchiveSession.mockReset()
    router.back.mockReset()
    router.replace.mockReset().mockResolvedValue(undefined)
  })

  it('redirects to the current archive page after the Ticket is exchanged', async () => {
    api.exchangeExternalArchiveTicket.mockResolvedValue({ data: session })

    mountPage()
    await flushPromises()

    expect(api.setExternalArchiveSession).toHaveBeenCalledWith(session)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/archive',
      query: { external: 'ticket', bah: '123456' },
    })
    expect(sessionStorage.getItem('MRR-EXTERNAL-ARCHIVE:session')).toBeNull()
  })

  it('clears unverified memory state and offers recovery actions after a timeout', async () => {
    api.exchangeExternalArchiveTicket.mockRejectedValue({
      code: 'ECONNABORTED',
      message: 'timeout of 60000ms exceeded',
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('连接时间较长')
    expect(wrapper.text()).toContain('继续等待')
    expect(wrapper.text()).toContain('重新尝试')
    expect(wrapper.text()).toContain('返回上一页')
    expect(api.clearExternalArchiveSession).toHaveBeenCalled()
    expect(api.setExternalArchiveSession).not.toHaveBeenCalled()
  })

  it('continues with a longer timeout and reuses a verified server session when available', async () => {
    api.exchangeExternalArchiveTicket.mockRejectedValueOnce({
      code: 'ETIMEDOUT',
      message: 'request timed out',
    })
    api.getExternalArchiveContext.mockResolvedValueOnce({ data: session })

    const wrapper = mountPage()
    await flushPromises()

    const continueButton = wrapper.findAll('button').find(button => button.text() === '继续等待')
    expect(continueButton).toBeDefined()
    await continueButton!.trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(api.getExternalArchiveContext).toHaveBeenCalledWith({ timeout: 180_000 })
    expect(api.setExternalArchiveSession).toHaveBeenCalledWith(session)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/archive',
      query: { external: 'ticket', bah: '123456' },
    })
  })

  it('still treats explicit authorization rejection as a terminal error', async () => {
    api.exchangeExternalArchiveTicket.mockRejectedValue({
      message: '外部影像访问票据无效或已过期',
      response: { status: 403 },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('无法访问影像档案袋')
    expect(wrapper.text()).not.toContain('继续等待')
    expect(api.clearExternalArchiveSession).toHaveBeenCalled()
    expect(api.setExternalArchiveSession).not.toHaveBeenCalled()
  })
})
