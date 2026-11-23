import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ArchiveSearchBar from '../ArchiveSearchBar.vue'

const stubs = {
  'el-input': {
    inheritAttrs: false,
    props: ['modelValue', 'placeholder'],
    template: '<input :name="$attrs.name" :aria-label="$attrs[\'aria-label\']" :aria-describedby="$attrs[\'aria-describedby\']" :placeholder="placeholder" :value="modelValue">',
  },
  'MrrFilterActionButton': { template: '<button type="button"><slot /></button>' },
  'MrrInputLengthBadge': true,
}

describe('archive search bar', () => {
  it('keeps the compact sidebar structure without visible labels by default', () => {
    const wrapper = mount(ArchiveSearchBar, { global: { stubs } })

    expect(wrapper.find('.search-card').classes()).not.toContain('is-labelled')
    expect(wrapper.findAll('.search-field__label')).toHaveLength(0)
    expect(wrapper.find('label').exists()).toBe(false)
    expect(wrapper.find('.search-hint').exists()).toBe(false)
    expect(wrapper.get('input[name="archive-bah"]').attributes('aria-describedby')).toBeUndefined()
  })

  it('shows visible labels and the lookup rules before the first search', () => {
    const wrapper = mount(ArchiveSearchBar, { props: { labelled: true }, global: { stubs } })

    expect(wrapper.find('.search-card').classes()).toContain('is-labelled')
    expect(wrapper.findAll('label .search-field__label').map(label => label.text()))
      .toEqual(['病案号', '上架号', '身份证号'])
    const hint = wrapper.get('.search-hint')
    expect(hint.text()).toContain('10000000')
    for (const name of ['archive-bah', 'archive-sjh', 'archive-id-card']) {
      expect(wrapper.get(`input[name="${name}"]`).attributes('aria-describedby')).toBe(hint.attributes('id'))
    }
  })

  it('marks the shelf number as required for large record numbers until an ID card takes over', async () => {
    const wrapper = mount(ArchiveSearchBar, {
      props: { labelled: true, searchBah: '10000001' },
      global: { stubs },
    })
    expect(wrapper.findAll('.search-field__label')[1].text()).toContain('（必填）')

    await wrapper.setProps({ searchIdCard: '110101199001011234' })
    expect(wrapper.findAll('.search-field__label')[1].text()).not.toContain('（必填）')
  })
})
