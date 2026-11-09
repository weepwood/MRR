# MRR 前端设计规范

本文是 MRR 管理端的视觉与交互规范，配合 [工程规范](ENGINEERING.md) 和 [项目设计导航](../docs/design/README.md) 使用。旧 [Console 设计文档](CONSOLE-DESIGN.md) 仅保留为兼容入口，不再独立定义数值。

核对日期：2026-11-09；事实基线：`5b9b920a`。本文整理现有设计并规定新增或改动页面的验收要求，不表示所有历史页面已完成迁移。未合并差异见 Components 中的状态表。

## Overview

MRR 服务于医院内网的病案查询、影像调阅、数据管理和运维。界面优先让操作者辨认当前病案、筛选条件、数据状态与可执行操作；设计密度、稳定性和错误表达应服务实际任务。

保持现有管理端外观：中性工作区、主色操作、细边框表面、紧凑控件和明确层级。指标、表格和档案袋按职责组织，不套用营销落地页的巨型标题、宣传卡片或 CTA 布局。本文不要求重新设计现有页面。

规则与源码冲突时，先确认分支、设置和实际渲染，再修正事实记录或提出独立变更；不能把文档当作已实施证明。架构、权限和数据边界服从 [ARCHITECTURE.md](../ARCHITECTURE.md)，执行流程服从 [AGENTS.md](../AGENTS.md)。

## Colors

### 令牌来源与依赖

设计数值的运行事实源是 [tokens.css](src/assets/styles/tokens.css)、[tones.css](src/assets/styles/tones.css)、主题配置和组件样式。本文列出用途及核对值，不建立第二套主题配置或静态调色板。

[globals.css](src/assets/styles/globals.css) 组织令牌、兼容层、基础样式、Element Plus 桥接与共享组件样式；不要在页面重新创建同类覆盖层。主色由应用设置与 [主题应用逻辑](src/store/modules/settings.ts) 配合运行，默认设置见 [settings.default.ts](src/settings.default.ts)。

| 语义 | 优先令牌 | 使用约束 |
| --- | --- | --- |
| 主操作与链接 | `--mrr-primary` / `--color-primary` | 默认主色 `#2563eb`；允许既有设置调整，不在页面固定品牌色 |
| 页面与内容表面 | `--mrr-background`、`--mrr-workspace-bg`、`--mrr-card` | 区分工作区和内容，不用同一背景抹平层级 |
| 浮层 | `--mrr-popover`、`--mrr-popover-foreground` | 菜单、提示和浮层必须保留可读的实色底 |
| 正文与说明 | `--mrr-foreground`、`--mrr-card-foreground`、`--mrr-muted-foreground` | 重要说明不使用低对比占位色，不只依赖颜色表达状态 |
| 边界与焦点 | `--mrr-border`、`--mrr-shell-divider`、`--mrr-ring` | 边框表达分区，焦点表达可操作位置 |
| 危险与语义状态 | `--mrr-destructive`、现有 tone / Element Plus 语义色 | 状态同时给出文字或图标；不任意改变既有业务配色含义 |

业务配色复用现有语义色和配置，不将随机色当作科室、分类或状态映射。明暗主题均需核对正文、说明、禁用、焦点和浮层；单个页面的对比度测量不能证明全站符合可访问性标准。

### Chrome 86 降级

最低浏览器基线为 Chrome 86。新布局使用前缀形式媒体查询，不依赖 `:has()`、列表形态的 `:not()`、容器查询、`dvh` 或新选择器才能工作。构建与 [兼容检查脚本](scripts/verify-chrome86-build.mjs) 是静态证据，不能代替真机检查。

`color-mix()` 不受 Chrome 86 支持。装饰性色调或渐变可以降级；以下情况必须保留独立实色或相邻的静态回退：

- 数据编码：图表色阶、热力图、进度填充等不能变为不可见。
- 浮层背景：不能让文字直接压在底层页面上。
- 承载分层的背景：含混色渐变的 `background` 简写不得连带丢失实色底，必要时单独声明 `background-color`。
- 唯一状态信号：不能因降级而失去选中、错误或禁用的识别方式。

回退优先用已有语义色或 Element Plus 同步色阶。不要为纯装饰逐处复制一套混色值。背景模糊和视图过渡只能作为增强；关闭增强后仍能完成任务。

## Typography

### 字体与实际字号

[base.css](src/assets/styles/base.css) 的正文字体栈为 `Inter, "HarmonyOS Sans SC", -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif`。中文字体由 [fonts.css](src/assets/styles/fonts.css) 从本地 `/fonts/` 加载 400/500/700 字重；Inter 是否可用取决于运行环境，不能把字体栈名称当作该字体已随包交付的证明。不得为管理端引入外部字体 CDN。

| 层级 | 当前共享实现 | 事实来源 |
| --- | --- | --- |
| 页面标题 | 28px；760px 以下 24px；请求字重 650 | [page-header.css](src/assets/styles/components/page-header.css) |
| 页头说明 | 全局页头规则为 14px，按模式设置间距和行高 | 同上；全局选择器优先于组件内的结构样式 |
| 分区标题 | 14px，请求字重 650 | [MrrSectionCard](src/components/MrrSectionCard/index.vue) |
| 正文 | 14px，行高 1.5 | [base.css](src/assets/styles/base.css) |
| 指标主值 | 26px，请求字重 680；文本型值使用 compact 变体 | [metric-card.css](src/assets/styles/components/metric-card.css) |
| 指标标签与说明 | 12px；说明按内容换行 | 同上 |

CSS 请求字重与字体文件字重不是一回事，按浏览器字体匹配规则呈现。不要为了复刻旧文档中的字重新增字体资源。

页面使用清晰的主标题和分区标题；已有英文标签保留为页头可选信息，不单独扩大视觉重量。说明应围绕数据口径、时间范围或操作方法。关键提示不可只通过省略号提供；如确需截断，必须有可访问的完整读取方式。

## Layout

### 页面壳层和有效宽度

[MrrPageShell](src/components/MrrPageShell/index.vue) 负责可收缩的单列网格，组件内不重复添加页面外层 padding。

| 宽度模式 | 默认最大宽度 | 常用场景 |
| --- | --- | --- |
| `fluid` | 1720px | 列表、统计、监控 |
| `standard` | 1440px | 详情、双栏内容 |
| `narrow` | 1120px | 设置、表单 |

上述值来自令牌，不是所有窗口中的实际内容宽度。布局必须扣除侧栏、工作区留白和滚动条；设置网格优先按实际内容区域自适应，不按整个窗口假设可用列数。

主侧栏默认 72px，次侧栏 232px，次侧栏收起为 64px；顶部、页签、工具栏分别使用现有 `--g-*` 布局变量。不同导航模式可以隐藏或组合侧栏，不固定要求所有页面同时出现两层侧栏。见 [tokens.css](src/assets/styles/tokens.css) 与 [应用壳层](src/layouts/index.vue)。

启用移动适配时，[settings store](src/store/modules/settings.ts) 在窗口宽度小于 1024px 时进入 mobile 模式；页面另有 1100/760/640/600px 等局部断点，不能把其中一个当成全站唯一断点。

### 间距、换行与滚动

间距令牌按 4px 递进：`--mrr-space-1/2/3/4/5/6/8` 分别为 4/8/12/16/20/24/32px；页面默认间距 `--mrr-page-gap` 为 24px。业务特有布局可使用已有局部间距，不为常规卡片复制整套样式。

- 页头文案、操作组、分区标题允许换行。使用 `min-width: 0` 和 `minmax(0, 1fr)` 让内容可收缩，不能靠裁切隐藏不可达的操作。
- 表格在自身区域横向滚动，页面主体不因表格固有宽度撑开。
- 分页在窄屏分别安排总数/条数、页码导航和跳页，前后翻页与页码保持同组。
- 筛选栏按可用宽度换行，显式 `gap` 操作组不叠加相邻按钮左边距。
- 窄屏日期范围、数字输入和抽屉按实际容器约束；日期弹层复用 [picker-options.ts](src/ui/provider/picker-options.ts)。
- 设置中的配色行隐藏列头后，应显示各字段标签，按钮也允许换行。
- 历史 `.page-shell` 与新增共享壳层遵守相同的可收缩约束。档案袋可以有业务特有布局，但不能无界加载整案原图。

## Elevation & Depth

表面通过细边框、中性色与轻阴影区分；阴影复用 `--mrr-shadow-xs/sm/md/lg`，不在页面叠加新的阴影系统。常规内容卡使用既有组件默认值，弹窗、菜单与抽屉保留明确的前后层级。

[glass-surface.css](src/assets/styles/components/glass-surface.css) 的增强效果按现有入口使用，不把玻璃和模糊扩展为所有业务表面的要求。Chrome 86 或不支持增强的环境中，应有实色底和可识别边界。

普通只读指标卡保持稳定，不通过悬浮位移暗示可点击。链接和真正的操作控件保留悬停、选中与焦点反馈。

### 动效边界

沿用 [motion.css](src/assets/styles/motion.css) 与现有设置开关，快速/普通过渡令牌分别为 120/180ms。当前全局动态效果默认开启，由应用设置写入 `data-motion`；关闭时停用相应动画。不能把操作系统的 `prefers-reduced-motion` 推断为全站已经关闭动态效果；登录滑块等组件有独立局部规则，需要分别核对。

菜单展开、页签和页面切换应保留状态识别，不增加涟漪或不必要的离场叠加。主线侧栏子目录点击不播放涟漪。路由加载沿用顶部 2px 进度条，延迟 200ms 显示以避免快速请求闪现，不显示新的全局转圈反馈。改变全局动效策略应单独评估，不通过文档更新静默修改产品行为。

## Shapes

全局圆角只有一个设置来源：`--radius`。当前 [默认设置](src/settings.default.ts) 为 0.75rem（通常 12px）；`tokens.css` 中未应用设置时的 CSS 回退为 0.5rem，两者不能混作同一个默认值。

| 令牌 | 应用设置为 12px 基准时 | 用途 |
| --- | --- | --- |
| `--mrr-radius-sm` | 6px | 微型控件、标签 |
| `--mrr-radius-md` | 8px | 常规控件、菜单项 |
| `--mrr-radius-lg` | 10px | 小型内嵌容器 |
| `--mrr-radius-card` | 12px | 页头、分区卡、指标卡等页面级表面 |
| `--mrr-radius-xl` | 14px | 已有内层分组、指标图标等内嵌区域 |
| `--mrr-radius-dialog` | 12px | 对话框 |
| `--mrr-radius-2xl` | 18px | 既有全页或登录外壳 |
| `--mrr-radius-pill` | 999px | 胶囊和状态标签 |

这些是指定设置下的派生值；实际公式及上下限见 [tokens.css](src/assets/styles/tokens.css)。不得把表格数值复制成新的 CSS 常量。圆形使用 50%，不以非等比例缩放改变方形图标或按钮的几何形状。

单行输入、选择、日期范围和数字输入使用 `--mrr-input-height`（33px），普通/紧凑/大型按钮共用 `--mrr-control-height`，图标按钮保持同高同宽。登录注册区域沿用局部 `--auth-input-height`（38px）；多行输入和多选标签允许按内容增高。以 [form-controls.css](src/assets/styles/components/form-controls.css) 及组件实际尺寸为准。

## Components

### 组合与职责

| 组件/样式 | 职责与使用规则 |
| --- | --- |
| [MrrPageHeader](src/components/MrrPageHeader/index.vue) | 主标题、说明、元信息与操作区；plain/card 模式的排版由全局 `page-header.css` 统一负责 |
| [MrrMetricCard](src/components/MrrMetricCard/index.vue) | 核心指标统一使用 label/value/note/icon/tone/valueTone/compactValue 与 extra 插槽；语义色和数值色取自[公共类型](src/components/MrrMetricCard/types.ts)，不复制同类摘要卡 CSS |
| [MrrSectionCard](src/components/MrrSectionCard/index.vue) | 分区标题、说明、操作区与 bodyPadding；计数不附加装饰状态点 |
| [MrrDataTablePanel](src/components/MrrDataTablePanel/index.vue) | 组合 filters/toolbar/content/pagination，统一数量和分页区域 |
| [MrrFilterBar](src/components/MrrFilterBar/index.vue) | 组合筛选条件与 actions；embedded 变体用于已有数据面板，不再套第二层卡片 |
| [MrrTableActions](src/components/MrrTableActions/index.vue) | 行内/更多操作、禁用及可访问名称；`placement` 当前表示排序优先级，空间足够时低优先级操作也可直显，不能当作强制隐藏授权规则 |
| [MrrStatusTag](src/components/MrrStatusTag/index.vue) | 复用状态文案和语义色，避免只有颜色没有含义 |
| [charts](src/components/charts/index.ts) | 复用图表卡与图表适配层；页面不自行创建第二套实例、主题或尺寸轮询 |
| [AppLoading](src/components/AppLoading/index.vue)、[AppEmpty](src/components/AppEmpty/index.vue)、[AppError](src/components/AppError/index.vue) | 复用加载、无结果和失败/重试状态；页面负责正确选择状态，不能用空态掩盖错误 |

图表卡片复用 `MrrSectionCard` 的表面、标题和操作区；趋势说明完整换行，不能截掉时间范围或图表操作提示。图表区通过 `aria-busy` 表达加载状态。

指标卡通过图标语义色和轻色调背景辅助辨认，默认数值使用主文本色，异常数值可使用既有 `valueTone`。当前实现没有旧规范中的顶部 3px 色条，不应仅根据历史文字重新添加。指标图标当前桌面为 52×52px，600px 以下为 46×46px；保持正方形。无图标卡在桌面和移动端都只保留正文列。页头卡片与指标卡使用 110px 的共享最小高度，长内容按需增高，不使用固定高度裁掉信息。指标网格的 compact/three/four/wide 等变体沿用 [metric-card.css](src/assets/styles/components/metric-card.css)，不单凭指标数量在页面复制布局。

### 导航与品牌入口

[Logo 组件](src/layouts/components/Logo/index.vue) 的品牌与侧栏控制图标为 17px；桌面单侧栏的控制按钮保持 33px 正方形。悬停或键盘聚焦时显示收起图标，收起后显示展开图标；标题链接继续返回首页，其他导航模式保留原品牌链接。子目录点击不播放涟漪，但保留悬停、选中、焦点、导航和展开行为。

### 表单、按钮与可访问性

新增或改动表单应使用可见标签和真实控件的关联；只用 placeholder 不能替代字段名称。选择后仍应能辨认字段用途。装饰图标隐藏于辅助技术，纯图标按钮提供明确名称；沿用本地 Lucide 图标，旧组件迁移按页面范围处理。

操作区清楚区分查询/保存等主要任务与刷新/重置等辅助操作，不机械重排既有业务优先级。危险操作沿用现有权限、确认与失败处理，不为视觉统一改变业务授权。

键盘焦点使用独立的 2px 轮廓，危险操作沿用危险色；不能因按钮阴影、变体或浏览器降级而失去焦点。验证 Tab 顺序、标签点击聚焦、下拉键盘操作及浮层关闭后焦点恢复。可访问名称测试通过不等于完成了屏幕阅读器人工验收。

### 交互状态契约

以下是新增和修改页面的设计与验收要求；历史页面是否满足需逐页核对，不能据此宣称已经全覆盖。

| 状态 | 用户应能判断 | 必须保持的边界 |
| --- | --- | --- |
| 首次加载 | 哪个区域正在加载 | 区域稳定，适当表达 busy；复用现有加载组件，不新增全局反馈机制 |
| 刷新/再次查询 | 当前提交的条件和结果归属 | 迟到响应不能覆盖新结果；现有代次隔离用于对应调用链，不能假定所有页面已迁移 |
| 成功但为空 | 本次查询确实没有结果 | 区分未查询、无记录与错误，按当前权限边界展示 |
| 失败/超时 | 安全原因、可重试操作或 Request ID | 不伪装为空结果，不显示底层路径、密钥或患者原文 |
| 拒绝/会话失效 | 需要重新登录或缺少权限 | 后端仍是授权边界；退出或切换账号后不残留另一账号结果 |
| 禁用/处理中 | 操作为何不可用 | 不只降低透明度；原因与状态可读，防止重复操作沿用原业务逻辑 |
| 导出已生成但下载失败 | 任务完成和下载失败是不同结果 | 保留任务重下入口；超限要求分批，不静默丢页 |

主线已合入每文件 128MiB 导出预算和一次 50 张打印限制，见 [预算模块](src/utils/archive-export-budget.ts) 与 [整合记录](../docs/engineering/export-browser-budget.md)。这些是待真实终端验收的限制，不能当作内存安全保证。完整多 GB 下载仍属后续专项；图片请求并发、离页取消、Blob URL 和旧响应隔离沿用已有档案袋机制。

### 当前差异与后续迁移

| 项目 | 本文基线的状态 | 文档使用方式 |
| --- | --- | --- |
| 共享页头/工作台、无图标指标、侧栏无涟漪 | 已由 #534/#548 等合入主线 | 作为新增同类页面的复用基线 |
| 图表卡片复用分区卡与完整说明 | [PR #551](https://github.com/weepwood/MRR/pull/551) 待合并 | 现主线 `MrrChartCard` 仍有独立卡片样式；不得把候选当作当前实现 |
| 用户筛选可见标签及关联 | [PR #553](https://github.com/weepwood/MRR/pull/553) 待合并 | 作为已验证候选，合并后再刷新实施状态 |
| 其他旧页的重复容器、缺少标签或请求隔离 | 未逐页完成核验 | 有证据后按单一问题开任务，不做全站顺手替换 |

## Do's and Don'ts

- 先核对原组件、调用链、设计令牌和已有测试，再决定最小修改范围；工程代码组织按 [ENGINEERING.md](ENGINEERING.md)。
- 用中文、明暗主题、长说明和宽窄屏验证真实内容；不以隐藏内容、裁切操作或缩小字体代替布局修复。
- 不为页面视觉引入第二套请求、认证、Store、主题、状态机或缓存；公共 UI 不依赖具体病案业务。
- 不让病案号、姓名、身份证、Token、密码、密钥进入截图、日志、Issue、PR 或 Artifact；仅使用合成验证数据。
- 不将本文列出的目标、候选 PR、Mock、静态构建或现代 Chromium 结果写成医院验收通过。

### 验证与维护

改动 UI 时记录风险、受影响页面、当前 SHA、验证场景、独立审查与撤销步骤。优先本地定向检查，完整业务套件依既有分层 CI 执行；不因纯 UI 启动全套后端环境。

在 `frontend-fantastic-admin` 中按改动执行 `pnpm lint:tsc`、定向 ESLint/Stylelint、对应 Vitest/Playwright；构建或兼容变化执行 `pnpm build` 与 `pnpm test:compat:chrome86`。浏览器测试使用仓库已有 Mock 和认证夹具，不另建绕过登录的展示链路。

共享布局参考 [shared-ui-layout.spec.ts](e2e/shared-ui-layout.spec.ts)、[control-layout.spec.ts](e2e/control-layout.spec.ts) 和 [workbench-ui-layout.spec.ts](e2e/workbench-ui-layout.spec.ts)。常用核对视口为 375/768/1024/1366/1440/1920px，按实际改动取必要组合，并同时核对 plain/card、明暗、动效开关和缩放后的可用空间；不是每次都运行全部矩阵。

共享组件、令牌和核心交互变更时，同一 PR 更新本文件的对应规则与证据链接。纯文档刷新只做文档与源码一致性检查，不虚构新的渲染证据。医院 Chrome 86、Windows/Nginx/真实存储及人工验收继续复用 [医院副本验收包](../docs/engineering/hospital-replica-acceptance.md)。
