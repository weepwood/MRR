# MRR Console Design System

MRR Console 是面向医疗档案管理后台的页面级设计系统。视觉参考 `shadcn-ui/ui`，但不引入 React、TailwindCSS 或 shadcn 组件代码；项目继续使用 Vue 3、Element Plus 和普通 CSS，通过语义令牌复现其设计原则。

## 1. 设计原则

MRR Console 采用以下 shadcn 风格原则：

- 语义令牌优先：页面只消费 background、foreground、card、muted、accent、border、input、ring 和 destructive 等语义变量。
- 单层表面：普通卡片使用背景和 1px 边框区分层级，不依赖渐变、顶部彩条和大面积阴影。
- 低噪声控件：按钮、输入框和选择器保持紧凑尺寸，状态通过背景、边框和 ring 表达。
- 阴影只用于浮层：卡片默认无阴影；Select、Dropdown、Popover 和 Dialog 才使用中等阴影。
- 一个区域一个主操作：查询、保存或提交可以使用 primary，其余操作降低视觉重量。
- 品牌色克制使用：MRR 保留医疗蓝，但蓝色只用于主操作、焦点和关键状态。
- 明暗主题一致：暗色主题不重新设计组件，只替换语义表面、边框和透明度。

## 2. 页面结构

普通业务页面按以下顺序组合：

1. `MrrPageShell`：控制页面宽度和纵向区块间距。
2. `MrrPageHeader`：统一标题、说明和右侧操作。
3. `MrrMetricCard`：展示 3–6 个核心指标。
4. `MrrSectionCard` 或 `MrrDataTablePanel`：承载图表、详情、表单和数据表格。
5. `MrrFilterBar`：统一列表筛选条件和查询操作。
6. `MrrStatusTag`：统一正常、运行、警告、异常和未知状态。

影像档案袋属于沉浸式特殊页面，不强制使用普通页面骨架。

## 3. 页面宽度

```vue
<MrrPageShell width="fluid">...</MrrPageShell>
<MrrPageShell width="standard">...</MrrPageShell>
<MrrPageShell width="narrow">...</MrrPageShell>
```

- `fluid`：列表、统计和监控页面，最大宽度 1680px。
- `standard`：普通详情和双栏页面，最大宽度 1440px。
- `narrow`：设置和表单页面，最大宽度 1120px。

页面本身不要再增加额外的外层 `padding`。

## 4. 标准列表页示例

```vue
<MrrPageShell width="fluid">
  <MrrPageHeader
    title="用户管理"
    description="管理系统账号、角色分配和启停状态。"
    icon="i-ant-design:team-outlined"
  >
    <template #actions>
      <el-button>刷新</el-button>
    </template>
  </MrrPageHeader>

  <section class="mrr-metric-grid">
    <MrrMetricCard
      label="账号总数"
      :value="128"
      note="当前系统账号"
      icon="i-ant-design:user-outlined"
    />
  </section>

  <MrrDataTablePanel title="账号列表" :count="128">
    <template #filters>
      <MrrFilterBar>
        <el-input placeholder="搜索账号" />
        <template #actions>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </template>
      </MrrFilterBar>
    </template>

    <el-table :data="rows">...</el-table>

    <template #pagination>
      <el-pagination :total="128" />
    </template>
  </MrrDataTablePanel>
</MrrPageShell>
```

## 5. 语义令牌

页面样式优先使用：

- `--mrr-background` / `--mrr-foreground`
- `--mrr-card` / `--mrr-card-foreground`
- `--mrr-popover` / `--mrr-popover-foreground`
- `--mrr-muted` / `--mrr-muted-foreground`
- `--mrr-secondary` / `--mrr-secondary-foreground`
- `--mrr-accent` / `--mrr-accent-foreground`
- `--mrr-border` / `--mrr-input` / `--mrr-ring`
- `--mrr-primary` / `--mrr-primary-foreground`
- `--mrr-destructive` / `--mrr-destructive-muted`

禁止页面自行硬编码普通表面、边框、焦点环和主色。

## 6. 按钮

- 默认按钮对应 shadcn 的 outline：背景为页面表面，使用 1px 边框。
- primary 使用纯色品牌背景，不使用渐变和高光。
- danger 使用低饱和危险背景与危险色文字，避免所有危险按钮都成为大面积红块。
- text 对应 ghost，link 只用于真正的链接式操作。
- 默认高度 36px，小型 32px，大型 40px。
- Hover 只调整背景或边框，不执行上浮动画。
- Active 可以下移 1px，提供按压反馈但不引发布局抖动。
- Focus 使用 `border-ring + 3px ring`，不能只取消 outline。
- 一个页面区块最多保留一个 primary。

## 7. 输入控件

- 输入框、选择器、日期控件、文本域和数字输入框共享同一 input、border 和 ring。
- 默认高度 36px，圆角 8px，正文 13px。
- Hover 强化边框，不增加明显阴影。
- Focus 使用品牌 ring，错误状态使用 destructive ring。
- Placeholder 使用 muted foreground，不单独指定固定灰色。
- 只读使用 muted 表面；禁用在此基础上降低透明度并停止交互。
- Select、Dropdown、Popover 和日期面板使用 popover 表面、1px ring 和中等浮层阴影。
- 表单错误必须同时显示边框和错误文字。

## 8. 卡片与数据展示

- 普通卡片使用 card 背景、1px border、12px 圆角、无阴影。
- 指标卡不使用顶部彩条、渐变背景和悬浮位移。
- 图标只作为小尺寸语义提示，不作为大面积装饰。
- 表格表头使用 muted 表面，行 Hover 使用更浅的 muted 表面。
- Badge 和状态标签使用小圆角，不默认使用胶囊形。
- 分页当前页使用 primary，其余按钮保持 ghost 风格。

## 9. 语义色

| 颜色 | 含义 |
| --- | --- |
| Blue | 默认总量、请求量、主要运行信息 |
| Green | 正常、成功、完成、启用 |
| Amber | 待处理、警告、等待 |
| Danger / Rose | 失败、禁用、严重异常 |
| Violet | 容量、页数、性能指标 |
| Teal | 连接、线程、用户和并发 |
| Slate | 中性辅助指标 |

禁止为了视觉丰富而随机分配颜色。

## 10. 页面开发约束

- 页面标题固定使用 `MrrPageHeader`，不再定义局部 `.page-header`。
- 核心指标固定使用 `MrrMetricCard`，不再定义 `.summary-card`、`.stat-card`。
- 普通内容容器使用 `MrrSectionCard`。
- 带筛选和分页的表格使用 `MrrDataTablePanel`。
- 筛选条件使用 `MrrFilterBar`，查询按钮在前，重置按钮在后。
- 状态使用 `MrrStatusTag`，不在页面自行映射标签颜色。
- 页面局部 CSS 只处理业务独有布局。
- 普通卡片默认 `shadow="never"`。
- 行内编辑和查看使用 link 或 text 按钮；危险操作必须二次确认。
- 不在业务页面覆盖 `.el-button`、`.el-input`、`.el-select`、`.el-card` 和 `.el-table` 的公共外观。

## 11. 迁移顺序

1. 用户管理：标准列表页面样板。
2. 病案扫描数据统计：统计分析页面样板。
3. 系统与数据库监控：高密度监控页面样板。
4. 权限、记录、患者、日志、审计、档案装箱等普通业务页。
5. 系统设置、首页和其他特殊页面。

每迁移一个页面，应删除对应的重复页头、摘要卡、筛选栏和普通卡片 CSS。
