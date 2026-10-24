import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IdCardSearchPage from '../index.vue'

const { search, push } = vi.hoisted(() => ({ search: vi.fn(), push: vi.fn() }))

vi.mock('@/api/modules/search', () => ({ getArchiveCasesByIdCard: search }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

describe('身份证查询入口', () => {
  beforeEach(() => {
    search.mockReset()
    push.mockReset()
    push.mockResolvedValue(undefined)
  })

  it('通过 POST 获取受控令牌，跳转 URL 不包含身份证原文', async () => {
    search.mockResolvedValue({ data: { token: 'opaque-test-token', maskedIdCard: '110***********1234', cases: [] } })
    const wrapper = mount(IdCardSearchPage, {
      global: {
        stubs: {
          'el-input': {
            inheritAttrs: false,
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<input @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          'el-button': { template: '<button class="search-btn"><slot /></button>' },
          'el-alert': true,
        },
      },
    })

    await wrapper.find('input').setValue('110101199001011234')
    await wrapper.find('.search-btn').trigger('click')
    await flushPromises()

    expect(search).toHaveBeenCalledWith('110101199001011234')
    expect(push).toHaveBeenCalledWith({ name: 'scanImg', query: { id: 'opaque-test-token' } })
    expect(JSON.stringify(push.mock.calls)).not.toContain('110101199001011234')
  })
})
