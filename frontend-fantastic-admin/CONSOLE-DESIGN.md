# MRR Console Design System

MRR Console 是面向医疗档案业务的现代 SaaS 管理界面设计系统。项目继续使用 Vue 3、Element Plus 和普通 CSS，不引入 TailwindCSS，也不复制特定框架组件。

本文档是 MRR 管理端的正式设计基线。`DESIGN.md` 中的外部产品视觉分析仅作为参考，不得覆盖本文档定义的业务原则、组件结构和语义令牌。

## 1. 设计定位

整体界面应具备以下特征：

- 产品化：界面像持续运营的 SaaS 产品，而不是传统后台模板。
- 专业可信：医疗档案场景强调稳定、清晰和可审计，不使用夸张装饰。
- 信息高效：导航、筛选、表格和操作保持紧凑，但不拥挤。
- 层级明确：导航层、工作区、内容卡片和浮层具有稳定的视觉深度。
- 一致可扩展：页面通过公共组件组合，不在业务页面重新发明页头、卡片和筛选区。

### 1.1 八项检查原则

每个页面在开发和截图审查时必须检查：

1. **Contrast（对比）**：主操作、异常状态和核心数据是否清晰突出；一个操作区域最多一个主按钮。
2. **Hierarchy（层级）**：用户是否能依次识别页面任务、核心指标、筛选条件和数据内容。
3. **Alignment（对齐）**：页面标题、指标区、内容面板是否共享统一内容边线。
4. **Proximity（亲密性）**：筛选、批量操作、表格和分页是否作为同一个任务区域组织。
5. **Repetition（一致性）**：页头、卡片、状态、按钮和控件是否复用公共组件。
6. **Balance（平衡）**：导航、侧栏、图表和数据区的视觉重量是否合理。
7. **White Space（留白）**：通过间距和分隔建立分组，不依赖多层卡片嵌套。
8. **Unity（统一）**：页面是否明显属于同一个 MRR 产品，而不是不同模板的拼接。

## 2. 应用壳层

### 2.1 主导航

- 主导航采用 72px 图标轨道。
- 主入口使用紧凑图标和短标题。
- 激活项使用低饱和品牌背景、细边框和品牌文字。
- Hover 不放大图标，不使用强烈位移。
- 账号入口固定在导航底部。

### 2.2 工作区导航

- 二级导航采用 248px 工作区面板。
- 顶部展示当前业务分区名称。
- 菜单项高度约 38px，圆角 8px。
- 折叠模式保留图标，并通过 Tooltip 辅助识别。
- 悬浮菜单使用 popover 表面、细边框、模糊背景和中等阴影。

### 2.3 顶部区域

顶部区域由页签和工具栏组成：

- 使用半透明表面和背景模糊。
- 页签使用紧凑圆角矩形，而不是占满整行的大标签。
- 激活页签为白色卡片表面，非激活页签保持低视觉重量。
- 工具栏左右区域用细分隔线区分。
- 页面滚动后仅增加轻微阴影。

### 2.4 主工作区

- 使用柔和中性背景，不让所有内容直接铺在纯白页面上。
- 页面内容边距为 18–28px，根据视口自动调整。
- 普通业务内容由白色或深色卡片承载。
- 特殊沉浸式页面，例如影像档案袋，可以覆盖普通工作区规则。

## 3. 页面结构

普通业务页面依次组合：

1. `MrrPageShell`
2. `MrrPageHeader`
3. `MrrMetricCard`（只在存在真正业务指标时使用）
4. `MrrSectionCard`、`MrrChartCard` 或 `MrrDataTablePanel`
5. `MrrFilterBar`
6. `MrrSelectionBar`（存在批量选择时使用）
7. `MrrStatusTag`

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
      <MrrFilterBar variant="embedded">
        <el-input placeholder="搜索账号" />
        <template #actions>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </template>
      </MrrFilterBar>
    </template>

    <MrrSelectionBar :count="selectedRows.length" @clear="clearSelection">
      <el-button type="primary">批量操作</el-button>
    </MrrSelectionBar>

    <el-table :data="rows">...</el-table>

    <template #pagination>
      <el-pagination />
    </template>
  </MrrDataTablePanel>
</MrrPageShell>
```

页面标题默认只使用中文标题和业务说明。`eyebrow` 只用于必要的业务域说明，不默认添加装饰性英文标题。

## 4. 页面宽度

- `fluid`：列表、统计和监控页面，最大宽度 1720px。
- `standard`：详情和双栏页面，最大宽度 1440px。
- `narrow`：设置和表单页面，最大宽度 1120px。

页面自身不再添加额外外层 `padding`。

## 5. 间距与语义令牌

MRR 使用 4px 基础间距：

- 页面模块之间：24px。
- 卡片标题与内容：16px。
- 表单控件之间：8px。
- 紧密元数据：4px。

业务页面优先使用：

- `--mrr-app-shell-bg`
- `--mrr-navigation-bg`
- `--mrr-navigation-hover`
- `--mrr-navigation-active`
- `--mrr-workspace-bg`
- `--mrr-card`
- `--mrr-muted`
- `--mrr-border`
- `--mrr-primary`
- `--mrr-destructive`
- `--mrr-ring`

禁止在业务页面硬编码普通背景、导航背景、边框、阴影和品牌色。

## 6. 按钮

- 一个操作区域最多使用一个主按钮。
- 主按钮用于查询、保存、提交、创建和当前批量任务的主要动作。
- 普通按钮用于刷新、重置、取消和辅助操作。
- Text 或 Link 按钮用于表格行内操作。
- 危险操作使用低饱和危险样式，并必须二次确认。
- 默认高度 36px，小型 32px，大型 40px。
- Hover 只改变背景、边框或文字颜色。
- Active 可以下移 1px，但不能造成布局变化。

## 7. 输入与筛选

- 输入框、选择器、日期控件和数字输入共享相同高度、边框和聚焦 Ring。
- 独立查询区使用 `MrrFilterBar variant="standalone"`。
- 数据表格内部筛选使用 `MrrFilterBar variant="embedded"`，避免卡片内部再次嵌套完整卡片。
- 查询按钮位于操作区首位，重置按钮位于其后。
- 错误状态必须同时显示错误边框和错误文字。
- 只读与禁用状态必须在可读性和交互性上明确区分。

## 8. 卡片与指标

### 普通内容卡片

- 使用 card 表面、1px 边框、统一圆角和极轻阴影。
- 卡片头部可使用非常浅的 muted 表面。
- 图标控制在 30–42px 容器内。
- 普通卡片不执行悬浮位移。

### 指标卡

- 允许使用非常轻的语义色渐变和右上角柔光。
- 默认是只读展示，不执行 Hover 位移。
- 只有明确可点击时设置 `interactive`，Hover 最多上移 1px。
- 指标数字使用等宽数字特性。
- 颜色必须表达业务含义，不用于随机装饰。
- 页码、当前选择数量等界面状态不作为首屏业务指标。

语义颜色：

- 蓝色：默认总量和主指标。
- 绿色：成功、完成和健康状态。
- 黄色：待处理、警告和临近阈值。
- 红色：失败、异常和风险。
- 紫色：页数、容量和性能指标。
- 灰色：中性辅助信息。

## 9. 数据表格

- 表头使用低对比 muted 表面。
- 行 Hover 使用极浅强调色。
- 表格操作列固定在右侧。
- 行内详情使用 Link 按钮，不使用重复的实心主按钮。
- 分页固定在数据面板底部。
- 结果数量使用带状态点的小型 Badge。
- 加载、空状态和错误状态保持容器高度稳定。
- 批量操作只在选中数据后通过 `MrrSelectionBar` 出现。

## 10. 动效

- 普通交互过渡控制在 120–220ms。
- 导航展开与收起不超过 220ms。
- 页面切换不保留离场页面，避免新旧页面叠加残影。
- 菜单浮层使用轻微淡入和 0.98 缩放。
- 不可点击内容不得通过 Hover 位移暗示交互。
- 必须支持 `prefers-reduced-motion`。

## 11. 页面开发约束

- 页面标题必须使用 `MrrPageHeader`。
- 核心指标必须使用 `MrrMetricCard`。
- 普通内容容器使用 `MrrSectionCard`。
- 带筛选和分页的表格使用 `MrrDataTablePanel`。
- 筛选条件使用 `MrrFilterBar`。
- 批量选择操作使用 `MrrSelectionBar`。
- 状态使用 `MrrStatusTag`。
- 页面局部 CSS 只处理业务特有布局。
- 不在业务页面覆盖公共 `.el-button`、`.el-input`、`.el-select`、`.el-card` 和 `.el-table` 外观。
- 普通业务页面禁止重新定义 `.page-shell`、`.page-header`、`.summary-grid`、`.list-card` 和 `.pager`。

## 12. 迁移顺序

已建立的样板：

1. 用户管理：标准列表样板。
2. 扫描影像记录：高密度列表与批量操作样板。
3. 病案扫描数据统计：指标、图表和表格混合样板。

后续顺序：

1. 患者与权限页面。
2. 日志、访问审计和接口性能分析。
3. 系统与数据库监控。
4. OSS 迁移和档案装箱。
5. 系统设置、首页和其他特殊页面。
6. 影像档案袋单独按照沉浸式工作台规则优化。

每迁移一个页面，应删除对应的重复页头、摘要卡、筛选栏、表格容器和分页 CSS。
