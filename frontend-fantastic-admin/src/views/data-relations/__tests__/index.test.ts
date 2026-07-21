import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
      directives: {
        loading: {},
      },
      stubs: {
        'el-alert': {
          props: ['title'],
          template: '<div class="el-alert">{{ title }}</div>',
        },
        'el-button': {
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
        'el-card': {
          template: '<section class="el-card"><slot name="header" /><slot /></section>',
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
    expect(wrapper.text()).toContain('98.75%')
    expect(wrapper.text()).toContain('200,000')
    expect(wrapper.text()).toContain('扫描记录')
    expect(wrapper.text()).toContain('患者记录')
    expect(wrapper.text()).toContain('扫描记录主档关联缺失（估算）')
  })

  it('keeps the first phase visibly read-only', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('第一阶段仅提供只读检查和修复预览')
    expect(wrapper.text()).toContain('修复预览')
  })
})
