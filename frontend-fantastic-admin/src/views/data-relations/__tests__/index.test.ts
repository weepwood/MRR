import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import DataRelationWorkbenchPage from '../index.vue'

const apiMocks = vi.hoisted(() => ({
  getDataRelationOverview: vi.fn(),
  getDataQualitySummary: vi.fn(),
  getDataQualityIssues: vi.fn(),
  getDataQualityRepairPreview: vi.fn(),
  getArchiveRelationDetail: vi.fn(),
  runDataQualityChecks: vi.fn(),
  searchDataRelationArchives: vi.fn(),
}))

vi.mock('@/api/modules/data-relations', () => apiMocks)
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

const overview = {
  generatedAt: '2026-07-21T00:00:00Z',
  archiveCount: 200000,
  healthScore: 98.75,
  relations: [
    {
      tableName: 'mr_scan',
      label: '扫描记录',
      relation: 'mr_scan.archive_id → mr_archive.id',
      totalCount: 30000000,
      linkedCount: 29500000,
      missingCount: 500000,
      coverage: 98.33,
      estimated: true,
      source: 'POSTGRES_STATISTICS',
      coverageIncluded: true,
      status: 'WARNING',
    },
    {
      tableName: 'mr_patient',
      label: '患者记录',
      relation: 'mr_patient.bah ⇢ mr_archive（遗留关联）',
      totalCount: 200000,
      linkedCount: 0,
      missingCount: 200000,
      coverage: 0,
      estimated: false,
      source: 'LEGACY_CODE_ONLY',
      coverageIncluded: false,
      status: 'LEGACY',
    },
  ],
  latestQualityRun: {
    id: 7,
    status: 'SUCCESS',
    criticalCount: 3,
    warningCount: 9,
  },
  relationChecks: [
    {
      checkCode: 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED',
      checkName: '扫描记录主档关联缺失（估算）',
      severity: 'WARNING',
      issueCount: 500000,
      sampledCount: 200,
    },
  ],
  notes: ['mr_scan 覆盖率来自 PostgreSQL 统计信息'],
}

function mountPage() {
  return mount(DataRelationWorkbenchPage, {
    global: {
      components: { MrrPageHeader, MrrPageShell },
      directives: {
        loading: {},
      },
      stubs: {
        'FaIcon': true,
        'el-alert': {
          props: ['title'],
          template: '<div class="el-alert">{{ title }}</div>',
        },
        'el-button': {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
        'el-card': {
          template: '<section class="el-card"><slot name="header" /><slot /></section>',
        },
        // 指标卡统一由 MrrMetricCard 渲染，这里只验证数据接线。
        'MrrMetricCard': {
          props: ['label', 'value', 'note'],
          template: '<article><span>{{ label }}</span><strong>{{ value }}</strong><small>{{ note }}</small></article>',
        },
        'el-descriptions': {
          template: '<section><slot /></section>',
        },
        'el-descriptions-item': {
          props: ['label'],
          template: '<div>{{ label }}<slot /></div>',
        },
        'el-drawer': {
          props: ['modelValue'],
          template: '<aside v-if="modelValue"><slot /></aside>',
        },
        'el-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'el-input': true,
        'el-option': true,
        'el-progress': true,
        'el-select': true,
        'el-tab-pane': {
          props: ['label'],
          template: '<section><h3>{{ label }}</h3><slot /></section>',
        },
        'el-tabs': {
          template: '<div><slot /></div>',
        },
        'el-table': {
          props: ['data'],
          template: '<div class="el-table"><div v-for="(row, index) in data" :key="index">{{ row.label || row.checkName || row.check_name || row.detail || row.id }}</div><slot /></div>',
        },
        'el-table-column': true,
        'el-tag': {
          template: '<span><slot /></span>',
        },
      },
    },
  })
}

describe('dataRelationWorkbenchPage', () => {
  beforeEach(() => {
    Object.values(apiMocks).forEach(mock => mock.mockReset())
    apiMocks.getDataRelationOverview.mockResolvedValue({ code: 200, data: overview })
    apiMocks.getDataQualitySummary.mockResolvedValue({
      code: 200,
      data: {
        running: false,
        enabled: true,
        latestRun: overview.latestQualityRun,
        checks: overview.relationChecks,
      },
    })
    apiMocks.getDataQualityIssues.mockResolvedValue({
      code: 200,
      data: [
        {
          id: 10,
          check_code: 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED',
          check_name: '扫描记录主档关联缺失（估算）',
          severity: 'WARNING',
          entity_type: 'mr_scan',
          entity_id: '30000001',
          bah: '00789508',
          detail: 'archive_id 为空',
        },
      ],
    })
  })

  it('loads overview and latest quality results without starting a new check', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(apiMocks.getDataRelationOverview).toHaveBeenCalledTimes(1)
    expect(apiMocks.getDataQualitySummary).toHaveBeenCalledTimes(1)
    expect(apiMocks.getDataQualityIssues).toHaveBeenCalledWith(300)
    expect(apiMocks.runDataQualityChecks).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('数据关系工作台')
    expect(wrapper.text()).toContain('查找病案')
    expect(wrapper.get('label[for="relation-search-type"]').text()).toBe('查询类型')
    expect(wrapper.get('label[for="relation-search-value"]').text()).toBe('查询值')
    expect(wrapper.text()).toContain('98.75%')
    expect(wrapper.text()).toContain('200,000')
    expect(wrapper.text()).toContain('扫描记录')
    expect(wrapper.find('.relation-card .mrr-status-tag--warning').text()).toBe('WARNING')
    expect(wrapper.text()).toContain('患者记录')
    expect(wrapper.text()).toContain('扫描记录主档关联缺失（估算）')
  })

  it('keeps the first phase visibly read-only', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('第一阶段仅提供只读检查和修复预览')
    expect(wrapper.text()).toContain('修复预览')
  })

  it('preserves header refresh and check actions through the shared header slot', async () => {
    apiMocks.runDataQualityChecks.mockResolvedValue({ code: 200, data: { running: false } })
    const wrapper = mountPage()
    await flushPromises()
    const header = wrapper.findComponent(MrrPageHeader)
    await header.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    await flushPromises()
    expect(apiMocks.getDataRelationOverview).toHaveBeenCalledTimes(2)
    await header.findAll('button').find(button => button.text() === '执行检查')!.trigger('click')
    await flushPromises()
    expect(apiMocks.runDataQualityChecks).toHaveBeenCalledTimes(1)
  })
  it('切换候选时清除旧详情，较早的响应不能覆盖当前病案', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any
    const detail = (id: number) => ({ data: { archive: { id, bah: `00${id}` }, statistics: [], patients: [], boxes: [], scanSummary: {}, scanSamples: [], comparisons: [], warnings: [] } })
    let resolveFirst!: (value: ReturnType<typeof detail>) => void
    apiMocks.getArchiveRelationDetail.mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }))
    apiMocks.getArchiveRelationDetail.mockResolvedValueOnce(detail(2))
    const first = vm.openArchive({ id: 1, matchType: 'EXACT' })
    const second = vm.openArchive({ id: 2, matchType: 'EXACT' })
    await second
    await flushPromises()
    expect(wrapper.text()).toContain('病案主档 #2')
    resolveFirst(detail(1))
    await first
    await flushPromises()
    expect(wrapper.text()).toContain('病案主档 #2')
    expect(wrapper.text()).not.toContain('病案主档 #1')
  })

  it('详情失败时不显示旧详情，并可重试当前病案', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any
    apiMocks.getArchiveRelationDetail.mockRejectedValueOnce(new Error('failed'))
    await vm.openArchive({ id: 7, matchType: 'EXACT' })
    await flushPromises()
    expect(wrapper.text()).toContain('病案关系加载失败')
    expect(wrapper.text()).not.toContain('查询并选择一份病案后查看跨表关系')
    apiMocks.getArchiveRelationDetail.mockResolvedValueOnce({ data: { archive: { id: 7 }, statistics: [], patients: [], boxes: [], scanSummary: {}, scanSamples: [], comparisons: [], warnings: [] } })
    await wrapper.findAll('button').find(button => button.text() === '重试')!.trigger('click')
    await flushPromises()
    expect(apiMocks.getArchiveRelationDetail).toHaveBeenLastCalledWith(7)
    expect(wrapper.text()).toContain('病案主档 #7')
  })

  it('查询失败不保留先前候选，也不会显示为无结果', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.searchValue = '00001'
    apiMocks.searchDataRelationArchives.mockResolvedValueOnce({ data: [{ id: 1 }, { id: 2 }] })
    await vm.searchArchives()
    await flushPromises()
    expect(wrapper.text()).toContain('病案主档候选')
    apiMocks.searchDataRelationArchives.mockRejectedValueOnce(new Error('failed'))
    await vm.searchArchives()
    await flushPromises()
    expect(wrapper.text()).not.toContain('病案主档候选')
    expect(wrapper.text()).toContain('病案查询失败')
    expect(wrapper.text()).not.toContain('当前条件未找到病案主档')
  })

  it('新查询使旧查询和旧病案详情失效，并区分无匹配结果', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any
    let resolveDetail!: (value: unknown) => void
    let resolveSearch!: (value: unknown) => void
    apiMocks.getArchiveRelationDetail.mockReturnValueOnce(new Promise((resolve) => { resolveDetail = resolve }))
    const oldDetail = vm.openArchive({ id: 1, matchType: 'EXACT' })
    vm.searchValue = '00001'
    apiMocks.searchDataRelationArchives.mockReturnValueOnce(new Promise((resolve) => { resolveSearch = resolve }))
    const oldSearch = vm.searchArchives()
    vm.searchValue = '00002'
    apiMocks.searchDataRelationArchives.mockResolvedValueOnce({ data: [] })
    await vm.searchArchives()
    resolveSearch({ data: [{ id: 1 }, { id: 2 }] })
    resolveDetail({ data: { archive: { id: 1 } } })
    await Promise.all([oldSearch, oldDetail])
    await flushPromises()
    expect(wrapper.text()).toContain('当前条件未找到病案主档')
    expect(wrapper.text()).not.toContain('病案主档候选')
    expect(wrapper.text()).not.toContain('病案主档 #1')
  })
})
