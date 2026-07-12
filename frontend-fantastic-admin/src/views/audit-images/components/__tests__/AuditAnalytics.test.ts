import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuditAnalytics from '../AuditAnalytics.vue'

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
        stubs: {
          'el-card': { template: '<section class="el-card"><slot name="header"/><slot/></section>' },
          'el-icon': { template: '<i><slot/></i>' },
        },
      },
    })

    expect(wrapper.text()).toContain('128')
    expect(wrapper.text()).toContain('独立用户')
    expect(wrapper.text()).toContain('异常请求')
    expect(wrapper.find('[data-testid="audit-trend-chart"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="audit-action-chart"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="audit-user-chart"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('查看本地图片')
    expect(wrapper.text()).toContain('doctor-a')
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
        stubs: {
          'el-card': { template: '<section><slot name="header"/><slot/></section>' },
          'el-icon': true,
        },
      },
    })

    expect(wrapper.text()).toContain('当前筛选条件下暂无可分析数据')
  })
})
