# MRR 表格操作列规范

## 目标

表格中的行级操作统一使用 `MrrTableActions`：桌面端优先直接显示紧凑图标，只有中屏、窄屏或操作数量超过当前可用空间时才显示水平省略号菜单。

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
import useAuth from '@/utils/composables/useAuth'

const { auth } = useAuth()
const actions: MrrTableAction[] = [
  {
    key: 'edit',
    label: '编辑',
    icon: 'i-ri:edit-line',
    tone: 'primary',
    permission: 'record:edit',
    placement: 'inline',
  },
  {
    key: 'delete',
    label: '删除',
    icon: 'i-ri:delete-bin-line',
    tone: 'danger',
    permission: 'record:edit',
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
        :permission-checker="auth"
        @select="handleAction($event, row)"
      />
    </template>
  </el-table-column>
</template>
```

## 展示规则

- 单个操作：直接显示一个图标按钮；
- 桌面端：最多直接显示三个紧凑图标，不显示无意义的“…”；
- 中屏：保留一个最高优先级图标，其余操作进入水平省略号菜单；
- 窄屏：所有操作进入水平省略号菜单；
- `placement: 'inline'`、`auto`、`overflow` 只决定排列优先级，不再强制某个操作永久进入菜单；
- 没有溢出操作时不渲染省略号按钮；
- 没有可用操作时不渲染空操作组；
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

组件不直接读取路由、用户 Store 或权限状态。操作声明了 `permission` 时，页面必须通过 `permissionChecker` 显式传入现有 `useAuth().auth`；未提供检查器时，这类操作默认隐藏。页面已经提前计算权限时，也可以通过 `visible` 或条件渲染整个操作列控制。

这种设计避免通用展示组件与路由初始化耦合，同时保持权限判断默认拒绝。

## 交互边界

- 组件只发出 `select` 事件，不直接调用业务 API；
- 删除、禁用、取消任务等危险操作仍由页面弹出二次确认；
- 禁用操作应填写 `disabledReason`；
- 直显按钮的 `label` 用于 Tooltip 和 `aria-label`；
- 省略号按钮只显示水平 `...` 图标，通过 `aria-label="更多操作"` 提供可访问名称，不额外常驻显示文字提示；
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
