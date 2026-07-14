# MRR Console Design System

MRR Console 是面向医疗档案管理后台的页面级设计系统。目标是让普通管理页、统计页、监控页和设置页共享一致的页面骨架、间距、状态表达和 Element Plus 外观。

## 1. 页面结构

普通业务页面按以下顺序组合：

1. `MrrPageShell`：控制页面宽度和纵向区块间距。
2. `MrrPageHeader`：统一标题、说明和右侧操作。
3. `MrrMetricCard`：展示 3–6 个核心指标。
4. `MrrSectionCard` 或 `MrrDataTablePanel`：承载图表、详情、表单和数据表格。
5. `MrrFilterBar`：统一列表筛选条件和查询操作。
6. `MrrStatusTag`：统一正常、运行、警告、异常和未知状态。

影像档案袋属于沉浸式特殊页面，不强制使用普通页面骨架。

## 2. 页面宽度

```vue
<MrrPageShell width="fluid">...</MrrPageShell>
<MrrPageShell width="standard">...</MrrPageShell>
<MrrPageShell width="narrow">...</MrrPageShell>
```

- `fluid`：列表、统计和监控页面，最大宽度 1680px。
- `standard`：普通详情和双栏页面，最大宽度 1440px。
- `narrow`：设置和表单页面，最大宽度 1120px。

页面本身不要再增加额外的外层 `padding`。布局容器已经提供统一内容边距。

## 3. 标准列表页示例

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

## 4. 语义色

指标卡和状态只能按业务含义选择颜色：

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

## 5. 页面开发约束

- 页面标题固定使用 `MrrPageHeader`，不再定义局部 `.page-header`。
- 核心指标固定使用 `MrrMetricCard`，不再定义 `.summary-card`、`.stat-card`。
- 普通内容容器使用 `MrrSectionCard`。
- 带筛选和分页的表格使用 `MrrDataTablePanel`。
- 筛选条件使用 `MrrFilterBar`，查询按钮在前，重置按钮在后。
- 状态使用 `MrrStatusTag`，不在页面自行映射标签颜色。
- 页面局部 CSS 只处理该业务独有布局，不重新定义颜色、圆角、阴影和控件高度。
- 页面内不硬编码主色和语义色，统一使用 `tokens.css` 中的变量。
- 普通卡片默认 `shadow="never"`，通过边框区分层级。
- 行内编辑和查看操作优先使用 link 按钮；危险操作必须二次确认。

## 6. 迁移顺序

1. 用户管理：标准列表页面样板。
2. 病案扫描数据统计：统计分析页面样板。
3. 系统与数据库监控：高密度监控页面样板。
4. 权限、记录、患者、日志、审计、档案装箱等普通业务页。
5. 系统设置、首页和其他特殊页面。

每迁移一个页面，应删除对应的重复页头、摘要卡、筛选栏和普通卡片 CSS。
