# MRR 表格操作列规范

## 目标

表格中的行级操作统一使用 `MrrTableActions`：高频操作显示为带提示的图标按钮，低频操作或窄屏场景收纳到“更多操作”菜单。

页面级新增、导入、导出和刷新仍放在页面标题或工具栏中，不放入每一行。

## 文件

```text
src/components/MrrTableActions/
├── index.vue
└── types.ts

src/composables/useTableActionLayout.ts
```

## 基本用法

```vue
<script setup lang="ts">
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'

const actions: MrrTableAction[] = [
  {
    key: 'edit',
    label: '编辑',
    icon: 'i-ri:edit-line',
    tone: 'primary',
    placement: 'inline',
  },
  {
    key: 'delete',
    label: '删除',
    icon: 'i-ri:delete-bin-line',
    tone: 'danger',
  },
]

const { maxInlineActions, actionColumnWidth } = useTableActionLayout(actions.length, 2)
</script>

<template>
  <el-table-column
    label="操作"
    :width="actionColumnWidth"
    fixed="right"
    align="center"
  >
    <template #default="{ row }">
      <MrrTableActions
        :actions="actions"
        :max-inline="maxInlineActions"
        @select="handleAction($event, row)"
      />
    </template>
  </el-table-column>
</template>
```

## 展示规则

- 单个操作：图标按钮，操作列通常为 56px；
- 两个操作：宽屏直显，中屏保留一个图标并显示更多菜单；
- 三个及以上：仅将最高频操作设为 `placement: 'inline'`，其余设为 `overflow` 或 `auto`；
- 窄屏：所有操作自动进入更多菜单；
- 没有可用操作时不渲染空菜单；
- 无行级权限时优先隐藏整列。

## 操作定义

```ts
interface MrrTableAction {
  key: string
  label: string
  icon: string
  tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
  permission?: string | string[]
  visible?: boolean
  disabled?: boolean
  disabledReason?: string
  loading?: boolean
  placement?: 'auto' | 'inline' | 'overflow'
}
```

组件会通过现有 `useAuth()` 处理 `permission`。页面已有计算权限时，也可以直接通过 `visible` 或条件渲染操作列控制。

## 交互边界

- 组件只发出 `select` 事件，不直接调用业务 API；
- 删除、禁用、取消任务等危险操作仍由页面弹出二次确认；
- 禁用操作应填写 `disabledReason`；
- 每个按钮必须有明确 `label`，组件会将其用于 Tooltip 和 `aria-label`；
- 不使用颜色作为唯一的状态表达；
- 下拉菜单保持 teleport，避免被表格容器的 `overflow` 裁剪。

## 推荐图标

| 操作 | 图标 |
| --- | --- |
| 查看 | `i-ri:eye-line` |
| 编辑 | `i-ri:edit-line` |
| 删除 | `i-ri:delete-bin-line` |
| 审核通过 | `i-ri:check-line` |
| 拒绝 | `i-ri:close-line` |
| 重置密码 | `i-ri:key-2-line` |
| 禁用 | `i-ri:forbid-line` |
| 下载 | `i-ri:download-line` |
| 重试 | `i-ri:restart-line` |
| 取消 | `i-ri:stop-circle-line` |
