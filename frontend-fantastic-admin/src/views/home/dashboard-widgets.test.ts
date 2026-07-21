import { describe, expect, it } from 'vitest'
import {
  createDefaultWidgetPreferences,
  mergeWidgetPreferences,
  moveWidget,
  setWidgetPinned,
  sortWidgetPreferences,
  updateWidgetPreference,
} from './dashboard-widgets'
import type { DashboardWidgetDefinition, DashboardWidgetPreference } from './dashboard-widgets'

const definitions: DashboardWidgetDefinition[] = [
  {
    id: 'archive',
    title: '影像档案袋',
    description: '查看病案影像',
    icon: 'archive-icon',
    path: '/archive/embed',
    defaultSize: 'wide',
    defaultColor: 'blue',
    defaultPinned: true,
  },
  {
    id: 'records',
    title: '记录管理',
    description: '维护记录',
    icon: 'records-icon',
    path: '/records',
    defaultSize: 'small',
  },
  {
    id: 'monitoring',
    title: '系统监控',
    description: '查看状态',
    icon: 'monitoring-icon',
    path: '/monitoring',
    defaultSize: 'large',
    defaultColor: 'green',
  },
]

describe('dashboard widgets', () => {
  it('应根据定义创建稳定的默认布局', () => {
    expect(createDefaultWidgetPreferences(definitions)).toEqual([
      { id: 'archive', title: '影像档案袋', description: '查看病案影像', size: 'wide', color: 'blue', pinned: true, visible: true, order: 0 },
      { id: 'records', title: '记录管理', description: '维护记录', size: 'small', color: 'default', pinned: false, visible: true, order: 1 },
      { id: 'monitoring', title: '系统监控', description: '查看状态', size: 'large', color: 'green', pinned: false, visible: true, order: 2 },
    ])
  })

  it('应修复非法本地数据并补充后来新增的小组件', () => {
    const stored = [
      { id: 'records', title: '', description: 42, size: 'invalid', color: 'neon', pinned: 'yes', visible: true, order: 9 },
      { id: 'removed-widget', title: '旧组件', size: 'small', color: 'red', pinned: false, visible: true, order: 0 },
    ]

    expect(mergeWidgetPreferences(definitions, stored)).toEqual([
      { id: 'records', title: '记录管理', description: '维护记录', size: 'small', color: 'default', pinned: false, visible: true, order: 0 },
      { id: 'archive', title: '影像档案袋', description: '查看病案影像', size: 'wide', color: 'blue', pinned: true, visible: true, order: 1 },
      { id: 'monitoring', title: '系统监控', description: '查看状态', size: 'large', color: 'green', pinned: false, visible: true, order: 2 },
    ])
  })

  it('应允许修改并持久化小组件颜色', () => {
    const preferences = createDefaultWidgetPreferences(definitions)
    const result = updateWidgetPreference(preferences, 'records', { color: 'orange' })

    expect(result.find(item => item.id === 'records')?.color).toBe('orange')
  })

  it('置顶后应移动到普通组件之前并保留稳定顺序', () => {
    const preferences = createDefaultWidgetPreferences(definitions)
    const result = setWidgetPinned(preferences, 'monitoring', true)

    expect(sortWidgetPreferences(result).map(item => item.id)).toEqual(['archive', 'monitoring', 'records'])
    expect(result.find(item => item.id === 'monitoring')?.pinned).toBe(true)
  })

  it('拖拽重排后应重新生成连续顺序', () => {
    const preferences: DashboardWidgetPreference[] = [
      { id: 'archive', title: '影像档案袋', description: '查看病案影像', size: 'wide', color: 'blue', pinned: false, visible: true, order: 0 },
      { id: 'records', title: '记录管理', description: '维护记录', size: 'small', color: 'default', pinned: false, visible: true, order: 1 },
      { id: 'monitoring', title: '系统监控', description: '查看状态', size: 'large', color: 'green', pinned: false, visible: true, order: 2 },
    ]

    const result = moveWidget(preferences, 'monitoring', 'archive')

    expect(result.map(item => item.id)).toEqual(['monitoring', 'archive', 'records'])
    expect(result.map(item => item.order)).toEqual([0, 1, 2])
  })
})