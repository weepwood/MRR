# MRR 管理壳层规范

本文档说明 `src/assets/styles/admin-shell.css` 的设计目标、使用边界和页面迁移方式。

## 1. 设计来源与适配原则

本次改造参考 `Daymychen/art-design-pro` 的以下思路：

- 顶栏、侧栏和内容区形成清晰但克制的表面层级。
- 页面采用统一容器宽度与响应式边距，避免宽屏内容失控。
- 导航激活态使用细强调条与低饱和背景，而不是大面积高亮。
- 表单、表格、面板共享圆角、边框、阴影和动效令牌。
- 页面结构由“页面头 + 工具栏 + 内容面板”组成。

MRR 保留 Fantastic Admin 的路由、权限、菜单、标签栏和组件体系，不迁移参考项目的布局状态管理，也不复制其 Tailwind 组件实现。

## 2. 全局壳层行为

引入 `admin-shell.css` 后自动生效的部分包括：

1. 主内容区使用 `clamp()` 响应式边距。
2. 内容最大宽度为 `1760px`，在超宽屏上居中。
3. 顶栏采用半透明表面、轻量模糊和滚动阴影。
4. 一级导航增加细强调条、轻边框和短距离悬停动效。
5. Element Plus 按钮、输入框、表格、分页和弹窗共享控制尺寸与焦点环。
6. 暗色模式使用独立的表面、边框和阴影映射。
7. `prefers-reduced-motion` 下关闭非必要位移动画。

这些规则不改变业务数据、请求流程、权限判断和路由行为。

## 3. 页面标准结构

新页面或重构页面应优先采用以下结构：

```vue
<template>
  <div class="mrr-page">
    <header class="mrr-page-header">
      <div class="mrr-page-heading">
        <h1 class="mrr-page-title">病案管理</h1>
        <p class="mrr-page-description">
          查询、维护并追踪病案归档状态。
        </p>
      </div>

      <div class="mrr-page-actions">
        <el-button>导出</el-button>
        <el-button type="primary">新增病案</el-button>
      </div>
    </header>

    <section class="mrr-toolbar">
      <div class="mrr-toolbar__group">
        <!-- 查询控件 -->
      </div>
      <div class="mrr-toolbar__group mrr-toolbar__group--end">
        <!-- 查询、重置等操作 -->
      </div>
    </section>

    <section class="mrr-table-panel">
      <el-table :data="records">
        <!-- columns -->
      </el-table>
      <footer class="mrr-table-panel__footer">
        <span>共 {{ total }} 条</span>
        <el-pagination />
      </footer>
    </section>
  </div>
</template>
```

## 4. 通用组合类

| 类名 | 用途 |
| --- | --- |
| `mrr-page` | 页面根容器，统一纵向间距 |
| `mrr-page-header` | 页面标题、说明和主操作 |
| `mrr-page-title` | 页面一级标题 |
| `mrr-page-description` | 页面用途或数据口径说明 |
| `mrr-page-actions` | 页面右上角操作区 |
| `mrr-toolbar` | 查询、筛选和批量操作区 |
| `mrr-toolbar__group` | 工具栏内的控件分组 |
| `mrr-panel` | 普通内容面板 |
| `mrr-panel--flat` | 仅边框、不使用阴影的面板 |
| `mrr-panel--interactive` | 可交互面板的轻量悬停态 |
| `mrr-table-panel` | 表格与分页的整体容器 |
| `mrr-status-dot` | 文本状态前的小圆点 |

业务指标卡继续使用 `mrr-metric-card`，不要用 `mrr-panel` 替代。

## 5. 令牌使用规则

页面局部样式应优先使用以下变量，不直接重复硬编码：

- `--mrr-shell-page-padding`
- `--mrr-shell-content-max-width`
- `--mrr-shell-section-gap`
- `--mrr-shell-radius-sm/md/lg`
- `--mrr-shell-surface-raised`
- `--mrr-shell-surface-soft`
- `--mrr-shell-border`
- `--mrr-shell-shadow-sm/md`
- `--mrr-shell-focus-ring`

颜色语义仍以 `globals.css` 中的 `--color-*`、`--text-*`、`--surface-*` 为准。壳层令牌只描述空间、表面和交互层级。

## 6. 迁移顺序

页面应按以下顺序渐进迁移：

1. 将页面根节点替换为 `mrr-page`。
2. 统一页面标题与操作区。
3. 将搜索条件收敛到 `mrr-toolbar`。
4. 将表格与分页包裹进 `mrr-table-panel`。
5. 删除页面内重复的卡片圆角、边框、阴影和输入框样式。
6. 检查 1440px、1024px、768px 和 375px 宽度。
7. 同时验证明暗主题与减少动画模式。

## 7. 禁止事项

- 不在业务页面重新定义另一套全局颜色或圆角系统。
- 不使用高饱和渐变作为普通管理页面背景。
- 不为静态信息卡添加明显缩放、弹跳或长时动画。
- 不用阴影替代所有边框；默认优先边框，阴影仅表达层级。
- 不直接复制参考项目的 Store、路由或权限实现。
