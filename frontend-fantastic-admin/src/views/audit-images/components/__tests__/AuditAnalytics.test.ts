import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuditAnalytics from '../AuditAnalytics.vue'

const analyticsStubs = {
  'el-card': { template: '<section class="el-card"><slot name="header"/><slot/></section>' },
  'el-icon': { template: '<i><slot/></i>' },
  'el-tag': { template: '<span><slot/></span>' },
  'el-table': { template: '<div class="el-table"><slot/></div>' },
  'el-table-column': { template: '<div><slot :row="{ label: \'dev\', count: 1 }" /></div>' },
  'el-button': { template: '<button><slot/></button>' },
  'MrrChartCard': {
    props: ['empty', 'emptyDescription'],
    template: '<section><span v-if="empty">{{ emptyDescription }}</span><slot v-else /></section>',
  },
  'MrrLineChart': {
    name: 'MrrLineChart',
    props: ['height'],
    template: '<div class="line-chart" />',
  },
}

const analytics = {
  totalAccesses: 128,
  uniqueUsers: 6,
  uniqueTargets: 24,
  abnormalAccesses: 5,
  averageDurationMs: 18.6,
  trend: [
    { date: '2026-07-12', count: 48 },
    { date: '2026-07-13', count: 80 },
  ],
  actionDistribution: [
    { label: 'VIEW_IMAGE', count: 80 },
    { label: 'DOWNLOAD', count: 48 },
  ],
  topUsers: [
    { label: 'dev', count: 90 },
    { label: 'doctor-a', count: 38 },
  ],
}

describe('auditAnalytics', () => {
  it('renders summary metrics and all three chart views', () => {
    const wrapper = mount(AuditAnalytics, {
      props: { analytics },
      global: {
        stubs: analyticsStubs,
      },
    })

    expect(wrapper.text()).toContain('128')
    expect(wrapper.text()).toContain('访问用户数')
    expect(wrapper.text()).toContain('异常访问告警')
    expect(wrapper.findAllComponents({ name: 'MrrLineChart' })).toHaveLength(2)
    expect(wrapper.text()).toContain('按用户查看')
  })

  it('lets the access trend chart fill the available card height', () => {
    const wrapper = mount(AuditAnalytics, {
      props: { analytics },
      global: {
        stubs: analyticsStubs,
      },
    })

    expect(wrapper.findComponent({ name: 'MrrLineChart' }).props('height')).toBe(250)
  })

  it('shows a clear empty state when no audit data matches', () => {
    const wrapper = mount(AuditAnalytics, {
      props: {
        analytics: {
          totalAccesses: 0,
          uniqueUsers: 0,
          uniqueTargets: 0,
          abnormalAccesses: 0,
          averageDurationMs: 0,
          trend: [],
          actionDistribution: [],
          topUsers: [],
        },
      },
      global: {
        stubs: analyticsStubs,
      },
    })

    expect(wrapper.text()).toContain('暂无趋势数据')
  })
})
