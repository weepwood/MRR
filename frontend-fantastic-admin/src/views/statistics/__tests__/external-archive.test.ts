import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ExternalArchivePage from '../external-archive.vue'

const api = vi.hoisted(() => ({
  exchangeExternalArchiveTicket: vi.fn(),
  getExternalArchiveContext: vi.fn(),
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
  StatisticsArchivePage: {
    props: ['externalSession', 'accessMode'],
    template: '<div class="archive-page-stub" :data-access-mode="accessMode">{{ externalSession.clientId }}</div>',
  },
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
    api.exchangeExternalArchiveTicket.mockReset()
    api.getExternalArchiveContext.mockReset()
    router.back.mockReset()
    router.replace.mockReset().mockResolvedValue(undefined)
  })

  it('renders the current archive page after the Ticket is exchanged', async () => {
    api.exchangeExternalArchiveTicket.mockResolvedValue({ data: session })

    const wrapper = mountPage()
    await flushPromises()

    const archivePage = wrapper.find('.archive-page-stub')
    expect(archivePage.exists()).toBe(true)
    expect(archivePage.attributes('data-access-mode')).toBe('external-ticket')
    expect(archivePage.text()).toContain('his')
    expect(router.replace).toHaveBeenCalledWith({
      path: '/archive/external',
      query: { bah: '123456' },
    })
  })

  it('keeps the session and offers recovery actions after a timeout', async () => {
    sessionStorage.setItem('MRR-EXTERNAL-ARCHIVE:session', JSON.stringify(session))
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
    expect(sessionStorage.getItem('MRR-EXTERNAL-ARCHIVE:session')).not.toBeNull()
  })

  it('continues with a longer timeout and reuses a server session when available', async () => {
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
    expect(router.replace).toHaveBeenCalledWith({
      path: '/archive/external',
      query: { bah: '123456' },
    })
  })

  it('still treats explicit authorization rejection as a terminal error', async () => {
    sessionStorage.setItem('MRR-EXTERNAL-ARCHIVE:session', JSON.stringify(session))
    api.exchangeExternalArchiveTicket.mockRejectedValue({
      message: '外部影像访问票据无效或已过期',
      response: { status: 403 },
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('无法访问影像档案袋')
    expect(wrapper.text()).not.toContain('继续等待')
    expect(sessionStorage.getItem('MRR-EXTERNAL-ARCHIVE:session')).toBeNull()
  })
})
