# MRR 前端设计规范

本文是 MRR 管理端的视觉、交互、兼容与性能规范，配合 [工程规范](ENGINEERING.md) 和 [项目设计导航](../docs/design/README.md) 使用。旧 [Console 设计文档](CONSOLE-DESIGN.md) 只保留为兼容入口，不再定义数值。

- **事实来源**：数值以 [tokens.css](src/assets/styles/tokens.css)、主题文件和组件源码为准；本文写"用途、约束和原因"，不复制第二套数值。两者冲突时先核对源码与实际渲染，再修正本文或单独提变更。
- **状态说明**：本文同时包含"已实施规则"和"新增/改动页面的验收要求"。标注为"目标"或列在 [§8 迁移状态](#8-新旧页面统一) 中未完成的内容，不表示存量页面已经满足。
- **核对日期**：2026-11-22。本次修订的变更见文末[修订记录](#修订记录)。旧版英文章节锚点（`#layout`、`#components` 等）保留为兼容入口。

---

## 0. 速查：新增或改动页面必须满足的 12 条

1. 页面用 `MrrPageShell` + `MrrPageHeader` 组合，不再手写 `.page-shell` / `.page-header`（[§3](#3-布局)；业务页面已全部迁移）。
2. 列表用 `MrrDataTablePanel` + `MrrFilterBar embedded`，有可见标签时加 `labelled`（[§4.3](#43-筛选栏)）。
3. 卡片左右留白统一走 `--mrr-card-inset`，块间距走 `--mrr-page-gap`，不写 15/18/26px 这类网格外数值（[§2.5](#25-间距与卡片留白)）。
4. 颜色、阴影、圆角、层级、字号只引用令牌；页面里不出现裸十六进制色与 `z-index: 9999`（[§2](#2-设计令牌)）。
5. 过渡与动画只引用 `--mrr-motion-*` 与 `--mrr-ease-*`，只过渡 transform / opacity / 颜色类属性，不写 `transition: all`（[§2.9](#29-动效)）。
6. 装饰性位移、缩放写成 `transform: var(--mrr-motion-decor, <原值>)`；承载状态的变换不套变量（[§2.9](#29-动效)）。
7. 悬停、选中、焦点三种状态视觉上可区分；外环（3px 光晕）只属于选中态，焦点只用 `outline`（[§5.2](#52-悬停选中与焦点)）。
8. 空态、加载、失败用 `AppEmpty` / `AppLoading` / `AppError`，失败不伪装成空结果（[§5.1](#51-交互状态契约)）。
9. 复制用 `copyText()`，轮询用 `useVisibleInterval()`，不在页面再写一份（[§7](#7-性能)）。
10. Chrome 86 下核心表面、焦点、状态信号必须仍可辨认；不使用 `:has()`、`:is()`、容器查询作为唯一实现（[§6.1](#61-浏览器基线chrome-86)）。
11. 所有可点击元素可用键盘到达并有可访问名称；`div @click` 改为 `button` 或补 `role` + `tabindex` + 键盘事件（[§5.3](#53-键盘与可访问性)）。
12. 在明暗主题、暖色调色板、动效关闭、1440 / 1100 / 760 / 390px 四档宽度下自查一遍（[§10](#10-验证与维护)）。

---

<a id="overview"></a>

## 1. 设计原则

MRR 服务于医院内网的病案查询、影像调阅、数据管理和运维。界面首先让操作者辨认**当前对象、筛选条件、数据状态与可执行操作**。

- **工具而非展示**：中性工作区、主色操作、细边框实色表面、紧凑控件。不套用营销页的巨型标题、宣传卡片、渐变光斑或 CTA 布局。
- **状态由静态通道承载**：形状、文字、颜色、图标、位置。动效只表达"状态在变化"；关闭动效后截图仍能判断状态。
- **一致优先于局部最优**：同一角色只有一种实现。新页面复用共享组件；旧页面按 [§8](#8-新旧页面统一) 逐页迁移，不做全站机械替换。
- **降级可用**：Chrome 86、内网 HTTP、强制颜色模式下可以少装饰，不能少功能、少状态。
- **不泄露**：病案号、姓名、身份证、Token、内部路径不进入截图、日志、Issue、PR 或 Artifact；验证只用合成数据。

---

<a id="colors"></a>

## 2. 设计令牌

### 2.1 令牌来源与依赖

```
tokens.css（语义令牌，唯一数值源）
 ├─ themes/index.ts          shadcn HSL 首帧默认值（亮 / 暗）
 ├─ utils/theme-color.ts     运行时按用户主色推导 --color-primary / --primary / --el-color-primary-*
 ├─ utils/theme-palette.ts   暖色调色板（gr8r）整套覆盖，挂在 html[data-mrr-palette]
 ├─ browser-compatibility.css  Chrome 86 的 @supports not 变量级回退
 └─ element-plus-overrides.css  Element Plus 桥接（按需样式晚注入，选择器前置 body 提权）
```

- `tokens.css` 到 shadcn 变量是 1:1 桥接，不在桥接层二次取色，否则改主题不生效。
- 主色色阶只从一个十六进制输入推导（[theme-color.ts](src/utils/theme-color.ts)），在 OKLab/OKLCH 中混合。亮色下 `hover/active/deep` 依次压暗、`light-3..9` 向白混合；暗色下反过来，`light-3..9` 向 `#141414` 混合。**切换明暗必须重新推导**。
- 预设主色同时满足：白字对比度 ≥ 4.5，OKLCH 亮度 0.52–0.60，两两 OKLab 色差 ≥ 0.12。
- `themes/index.ts` 的 `--primary` / `--ring` 是 JS 接管前的首帧值，必须与默认主色 `#2563EB` 一致（亮暗相同），否则首帧会闪色相。
- 新增令牌时：若可能被 Chrome 86 回退分支引用（如 `--mrr-border-strong`、导航色），必须写静态色值，不能写 `color-mix()`。`token-references.test.ts` 会拦截未定义的 `var(--mrr-*)`。

### 2.2 颜色与语义

| 语义 | 令牌 | 约束 |
| --- | --- | --- |
| 主操作与链接 | `--mrr-primary` / `--color-primary` | 默认 `#2563EB`，允许用户设置；页面不写死品牌色 |
| 工作区 / 卡面 / 浮层 | `--mrr-workspace-bg` / `--mrr-card` / `--mrr-popover` | 见 [§2.3](#23-表面层级) 的亮度递增约束 |
| 正文 / 次要文字 | `--mrr-foreground` / `--mrr-muted-foreground` | 次要文字在卡面和工作区上都需 ≥ 4.5:1 |
| 边界与分隔 | `--mrr-border` / `--mrr-border-strong` / `--mrr-shell-divider` | 强描边为静态色值，供回退分支引用 |
| 焦点 | `--mrr-ring` | 只用于 `outline`，见 [§5.2](#52-悬停选中与焦点) |
| 危险与语义状态 | `--mrr-destructive`、`--mrr-tone-*`、Element Plus 语义色 | 状态同时给出文字或图标；不改变既有业务配色含义 |
| 导航 | `--mrr-navigation-*`、`--mrr-navigation-rail-*` | 主导航窄栏自带灰底，悬停用 `rail-hover`（比底色深一档） |

**对比度基线（默认主题，实测）**

| 组合 | 亮色 | 暗色 |
| --- | --- | --- |
| 次要文字 / 卡面 | 5.34:1（`215.4 19% 44%`） | 7.8:1 |
| 次要文字 / 工作区 | 4.87:1 | — |
| 表单错误文字 `--destructive` / 卡面 | 4.75:1（`#D13C3C`） | 5.7:1（`0 80% 68%`） |
| 白字 / 主色按钮 | 5.17:1 | 5.17:1 |
| 占位符 / 顶栏提示 / 字数计数 / 提示条说明（一律 muted，不叠透明度） | ≥ 4.87:1 | ≥ 7.8:1 |
| 徽标类文字（如首页「置顶」） | 用前景色，不把主色字压在主色淡底上 | 同左 |
| 白字 / 兼容拦截浮层 `#c2410c`（hover `#9a3412`） | 5.18:1（7.31:1） | 静态浮层，无明暗 |

单页测量不能证明全站达标；新增配色需按同样方法复核明暗两套。业务配色复用语义色，不用随机色映射科室、分类或状态。基线组合由 [contrast-baseline.test.ts](src/assets/styles/contrast-baseline.test.ts) 锁定：占位符与提示文字不得在 muted 之上叠加透明度（muted × 0.78 在输入框上实测 1.8:1），顶栏图标按钮必须有可访问名称。

### 2.3 表面层级

| 表面 | 亮色 | 暗色 | 用途 |
| --- | --- | --- | --- |
| 工作区 | `#F1F5F9` | `#11141D` | 主内容区底，卡片坐在它上面 |
| 页面底 | `#F8FAFC` | 同工作区 | 导航、顶栏、标签栏 |
| 卡面 | `#FFFFFF` | `#181C26` | 页头、分区卡、指标卡、表格容器 |
| 浮层 | 同卡面 | `#1C212C` | 菜单、下拉、抽屉、对话框 |

三条约束（[surfaces.test.ts](src/assets/styles/surfaces.test.ts) 锁定）：**亮度只能递增**（页面底 ≤ 卡面 ≤ 浮层）；**卡面比工作区至少高 2 个亮度百分点**；**桥接 1:1**。亮色浮层与卡面同色，靠阴影与描边分层。

表面使用**实色**：页头、页头图标、指标卡、卡头不叠加渐变或网格底纹。低色深显示器和投屏上，3% 的渐变会出现色带，而且语义已经由图标与数值色承担。[glass-surface.css](src/assets/styles/components/glass-surface.css) 的模糊效果仅限既有入口（全屏看图），不扩展到业务表面。

<a id="typography"></a>

### 2.4 字体与字号

正文字体栈：`Inter, "HarmonyOS Sans SC", -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif`。等宽统一用 `--mrr-font-mono`。它显式点名 Consolas，并在其后排中文字体；Windows 上通用 `monospace` 会落到新宋体。**页面不再手写等宽字体栈。**

字体从本地 `/fonts/` 加载，按 `unicode-range` 分两层：

- **界面子集**（约 130KB/字重）：覆盖界面常用字。`index.html` 预加载常规与中粗两档，避免首屏字体到达后整体重排。
- **完整字库**（约 4.3MB/字重）：只在出现子集外字符（如生僻姓名）时懒加载。后续可按 Google Fonts 方式切成约 100 个 unicode-range 分片，降低内网首次加载量（[§7](#7-性能) 待办）。

不引入外部字体 CDN。

| 层级 | 令牌 | 当前值 | 来源 |
| --- | --- | --- | --- |
| 页面标题 | `--mrr-font-size-title-compact` | 22px，700 | [page-header.css](src/assets/styles/components/page-header.css) |
| 页头说明 | `--mrr-font-size-sm` | 13px | 同上 |
| 分区标题 | `--mrr-font-size-md` | 14px，650 | [MrrSectionCard](src/components/MrrSectionCard/index.vue) |
| 正文 / 表格 | `--mrr-font-size-md` / `-sm` | 14px / 13px | base.css、Element Plus 桥接 |
| 标签、说明、表头 | `--mrr-font-size-xs` | 12px | metric-card.css 等 |
| 指标主值 | — | 26px，680，等宽数字 | [metric-card.css](src/assets/styles/components/metric-card.css) |
| 对话框标题 | `--mrr-font-size-lg` | 16px，650 | [element-plus-overrides.css](src/assets/styles/element-plus-overrides.css) |
| 表头 | `--mrr-font-size-xs` | 12px，600，表格整体等宽数字 | 同上 |

新样式从字号令牌取值；存量页面随迁移逐步替换。说明文字围绕数据口径、时间范围或操作方法；关键提示不能只靠省略号截断。

**正文最小档是 12px（`--mrr-font-size-xs`）。** 已收口的文件由 [type-scale.test.ts](src/assets/styles/type-scale.test.ts) 锁定，收口一处就把文件加进清单。以下位置有意保留更小字号，不视为违规：登录页「客户端环境」浮层内的两列字段与品牌区徽标（登录外壳有像素级 E2E 约束）、主导航窄栏图标下的标签、计数徽标（菜单错误数、开发者模式、输入位数、类型计数、隐藏组件数）、缩略图遮罩上的页码与类型、系统设置里的登录页微缩预览、公共状态页分钟热力图的小时刻度。

### 2.5 间距与卡片留白

- 间距令牌按 4px 递进：`--mrr-space-1/2/3/4/5/6/8` = 4/8/12/16/20/24/32px。
- **块间距**：页面内纵向块之间用 `--mrr-page-gap`（24px），≤640px 时 16px。由 `MrrPageShell` 或全局 `.page-shell` 提供，页面不再各写 16/18/20px。
- **卡片留白**：`--mrr-card-inset`（20px）/ `--mrr-card-inset-compact`（12px，≤760px）。页头卡、分区卡、表格面板（筛选区、工具条、表体、分页）、筛选栏、指标卡、旧页 `el-card` 共用这一条左右留白，纵向堆叠时标题、字段、表格外沿、分页落在**同一条竖线**上。
- **工作区留白**：`clamp(16px, 2vw, 24px)`，≤760px 为 16px。
- **允许保留的非网格值**（不要"顺手"改成令牌）：
  - **描边补偿**：输入框、选择器、文本域、分页尺寸选择的 `11px` / `9px` 横向内边距加上 1px 描边，正好让文字落在 12 / 10px 竖线上。
  - **由高度令牌决定的密度**：表格单元格 `padding-block: 6 / 7px`（行高由 `--mrr-table-header-height` 等决定）、次侧栏菜单项纵向 `6px`（配合 `min-height: 34px`；改成 8px 每项会高 4px）。
  - **文字级微距**：标签到控件（`el-form-item__label`、筛选栏网格标签 6px）、标题到说明（页头说明 7px、指标卡说明 6px）、分组眉题、状态点与文字基线对齐。
  - **按钮图标间距 6px**：由 Element Plus 公共按钮样式统一定义。
  - **功能性预留**：标签页关闭按钮的 `margin-right: 26px`（给绝对定位的关闭图标让位）。
  - **主题与受测区域**：暖色调色板 `warm-flat.css` 的密度值；登录页与登录表单、档案袋搜索栏的号码输入区（有逐帧 / 截断 E2E 约束）。

<a id="shapes"></a>

### 2.6 圆角与曲线

全局圆角只有一个来源 `--radius`，默认 0.75rem（12px，[settings.default.ts](src/settings.default.ts)），`tokens.css` 的回退值与之相同。

| 令牌 | 12px 基准时 | 用途 |
| --- | --- | --- |
| `--mrr-radius-sm` | 6px | 微型控件、普通状态标签 |
| `--mrr-radius-md` / `--mrr-control-radius` | 8px | 控件、菜单项、表格容器 |
| `--mrr-radius-lg` | 10px | 小型内嵌容器、页头图标 |
| `--mrr-radius-card` / `-dialog` | 12px | 页面级表面、对话框 |
| `--mrr-radius-xl` / `-2xl` | 14px / 18px | 内层分组 / 全页外壳 |
| `--mrr-radius-pill` | 999px | 胶囊与圆头按钮 |

实际公式与上下限见 tokens.css，不复制成新常量。

**G2 连续圆角**（`app.radiusShape = squircle`，`corner-shape: squircle`）只改圆角内侧曲率，不改大小，Chrome/Edge 139+ 之外自动降级为圆弧。它只适用于圆角矩形。开关、滑块、正圆、胶囊，以及由正圆零件组成的复合控件，通过 `--mrr-corner: round` 连同子树退出。新增同类元素沿用 `*-dot` / `*avatar*` / `*swatch` / `*spinner` / `*-marker` 命名，或加 `data-corner="round"`。开关使用唯一 `.el-switch`，迁移期间滑块 `.el-slider/.fa-slider` 均需登记。详见 [corner-shape.css](src/assets/styles/components/corner-shape.css)。

<a id="elevation--depth"></a>

### 2.7 阴影

只用 `--mrr-shadow-xs/sm/md/lg` 与 `--mrr-shadow-dialog`，不在页面叠加新的阴影系统。只读卡片保持静止；可点击卡片悬停时可升一档阴影并描边加深，不靠位移暗示可点。

### 2.8 层级（z-index）

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--mrr-z-sticky` | 1000 | 吸顶块、页内浮动工具条 |
| `--mrr-z-sidebar-mask` / `--mrr-z-sidebar` | 1009 / 1010 | 移动端侧栏遮罩 / 侧栏 |
| `--mrr-z-blocking` | 1900 | 页内阻断遮罩（下载、导出进度） |
| `--mrr-z-header` | 2000 | 顶部导航 |
| （Element Plus / Fa 弹层） | 2000 起递增 | 对话框、抽屉、消息、下拉 |
| `--mrr-z-progress` | 3000 | 路由进度条 |
| `--mrr-z-viewer` | 3000 | 全屏影像查看器；档案袋预览工具条 `+1`、工具条内下拉 `+2` |
| `--mrr-z-ocr-drawer` | 3100 | 档案袋 OCR 抽屉基座 |
| `--mrr-z-action-dialog` | 3200 | `ActionDialog` 确认 / 输入弹窗，须压住沉浸层 |
| `--mrr-z-message` | 3300 | `ElMessage` 提示条，须压住确认弹窗与全屏影像 |
| `--mrr-z-overlay-top` / `--mrr-z-watermark` | 4000 | 全屏看图说明条、跟随鼠标的图表提示 / 档案水印（`pointer-events: none`） |

阻断遮罩必须**低于**弹层基准，否则遮罩期间弹出的失败消息或确认框会被盖住（曾经的 `z-index: 9999` 就是这个问题）。

- **CSS** 只写 `var(--mrr-z-*)` 或 `calc(var(--mrr-z-*) + n)`。档案袋局部叠放（2010 / 2020 / 3001 / 3002）已改为相对值，数值不变。
- **必须传数字**的地方（Element Plus 的 `z-index` prop、`ElConfigProvider`、行内样式、命令式节点）用 [constants/z-index.ts](src/constants/z-index.ts) 的 `Z_INDEX`，不写裸数字；[z-index.test.ts](src/constants/__tests__/z-index.test.ts) 保证它与 `tokens.css` 逐项一致。
- 组件内部、只在自身层叠上下文里生效的小值（0–20，如缩略图角标、工具条叠放）可以保留字面量。Fa* 弹层组件默认值 2000 即弹层基准，不另设令牌。

<a id="动效边界"></a>

### 2.9 动效

时长按**语义**分档，曲线区分进出：

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--mrr-motion-press` | 100ms | 仅几何属性：按压、悬停位移 |
| `--mrr-motion-feedback` | 120ms | 颜色、透明度、边框、阴影；颜色类不得更短 |
| `--mrr-motion-move` | 180ms | 位移、展开收起、内容入场、重排、**离场** |
| `--mrr-motion-overlay` | 220ms | 浮层入场（抽屉、对话框、菜单、移动端侧栏），上限 |
| `--mrr-motion-loop` / `-loop-soft` | 1s / 1.5s | spinner / shimmer |
| `--mrr-motion-stagger` | 30ms | 兄弟元素依次入场的间隔（页面区块、骨架行），最多叠 3 档 |
| `--mrr-motion-attention` | 900ms | 数据已更新的提示淡出（指标数值），只表达「刚变过」 |
| `--mrr-ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` | 入场、状态变化 |
| `--mrr-ease-in` | `cubic-bezier(0.55, 0, 0.75, 0.2)` | 离场、收起 |

**进出不对称**：入场用 overlay（或 move）+ ease-out，离场用 move + ease-in，比入场短一档。Element Plus 的 `--el-transition-duration` 已映射到 overlay，`-fast` 映射到 feedback，与自研抽屉同节奏。UnoCSS 过渡类用 [uno.config.ts](uno.config.ts) 里的令牌别名：`duration-mrr-press / -feedback / -move / -overlay`、`ease-mrr-out / -in`，直接读 `tokens.css`；`transition`、`transition-colors` 等工具类的默认时长与曲线也已指向 feedback + ease-out。`<Transition>` 的 `enterActiveClass` / `leaveActiveClass` 不写 `duration-300`、`ease-in-out` 这类字面量，也不用 `transition-all`。presetAnimations 的 `animate-duration-*` 仍读不到变量，用到时数值须与令牌一致。

**字面量**：样式里的 `transition` / `animation` 只写 `var(--mrr-motion-*)` 与 `var(--mrr-ease-*)`；`linear`、无限循环的 `ease-in-out`、关闭信号（0 与 ≤1ms）放行，其余例外登记在 motion.test.ts 的白名单并写明理由。

**布局属性过渡的唯一例外：应用壳层侧栏收起 / 展开。** 其余地方只过渡 `transform`、`opacity` 与颜色类属性；壳层侧栏允许对 `width`（侧栏容器、次侧栏、顶部固定块）、`margin-left`（主内容区）、`inset-inline-start`（次侧栏）、`top` / `padding-top`（顶栏显隐）做 move 档过渡。原因：
- 主内容区的可用宽度确实改变，页面必须按新宽度重排，`transform` 只能移动画面，替代不了这次重排；
- 标签栏 / 工具栏（`FaSmartFixedBlock`）与页面里的 `position: fixed` 浮层（档案袋设置与工具条、影像查询遮罩、热力图提示、存储浏览器最大化）都位于主内容区之内，给主内容区加 `transform` 会让它们在动画期间改为相对主内容区定位而跳位；
- 代价有界：约 180ms、十来次布局；图表尺寸跟随有 80ms 防抖，不会逐帧重绘。
关闭动效（`data-motion="off"`）与系统「减少动效」已由 motion.css 全局清零过渡，这里不另做处理。新增壳层元素若需随侧栏同步，沿用同一 move 档与 `--mrr-ease-out`，不要引入第二种节奏。

**三条边界**（[motion.test.ts](src/assets/styles/motion.test.ts) 扫全仓）：

1. **状态只由静态通道承载**。`reduced` 与 `off` 的区别是"先降级装饰、状态保留到最后"。
2. **关闭动效只清 `transition` 与 `animation-duration`，禁止写全局 `transform: none`**。装饰性位移写成 `transform: var(--mrr-motion-decor, <原值>)`；成形或承载状态的变换（展开箭头的 `rotate(±45deg)`、"已切换"的 `rotate(180deg)`、叠层纸的错位）原样保留。判断口径：清零后截图还能不能读出当前状态。
3. **焦点用 `outline`，投影用 `box-shadow`，两者不兼任**。

**交互语义规则**

| 场景 | 应该 | 不应该 |
| --- | --- | --- |
| 选中确认 | 颜色、描边、字重过渡 | 0.96→1.02→1 弹跳，或超过最终尺寸的缩放 |
| 列表增删（标签页、小组件） | 宽度收拢 / FLIP 位移，方向与排列方向一致 | 离场设 `position: absolute` 跳位，或横向列表用纵向位移 |
| 遮罩 + 抽屉 | 同节奏淡出，`visibility` 延迟到淡出结束 | 遮罩 `visibility` 立即切换导致瞬间消失 |
| 层级导航（面包屑） | 只让新增层级轻量淡入 | key 带 index 导致整条重播 |
| 影像悬停 | 卡片描边与阴影反馈 | 缩放影像本身（文字重采样会发虚） |
| 吸顶隐藏 | 带方向阈值（≥6px） | 1–2px 惯性抖动也触发显隐 |
| 反馈性动画 | 点击后 ≤100ms 开始，总长 ≤ 350ms | 先等 300ms 再出效果 |

**共享组件内置的状态动效**（页面不需要再写）：

- `MrrPageShell` 的直接子区块入场：opacity + 4px 上移，stagger 依次播放，`fill-mode: backwards`，结束后不残留 transform；KeepAlive 重新激活时自动重播；嵌套壳层只播外层。
- `MrrStatusTag` 的「进行中」光圈：识别为运行中 / 处理中的状态自动开启（显式 `tone` 时用 `live` 控制）；状态仍由文字与语义色表达，强制颜色模式下隐藏。
- `MrrMetricCard` 的数值更新提示：首屏不播，`value` 变化后主色浅底从数字背后淡出一次。
- `AppLoading` 骨架：表头行 + 行 / 卡依次淡入，尺寸取 `--mrr-table-header-height` / `--mrr-table-row-height`，结果到达时不跳版。

动效默认开启，由设置写入 `data-motion`，`index.html` 首帧按本地缓存预置。系统 `prefers-reduced-motion` 缩短 CSS 动效；JS 空间动效（登录、主题切换、`scrollTo` / `scrollIntoView` 平滑滚动、WAAPI）必须先调用 `isMotionEnabled()`。关闭路径保留 1ms 的动画结束信号，以完成浮层生命周期。路由加载沿用顶部 2px 进度条，延迟 200ms 显示；主内容不做离场过渡。

---

<a id="layout"></a>

## 3. 布局

### 3.1 应用壳层

主侧栏 72px，次侧栏 232px（收起 64px），顶栏 / 标签栏 / 工具栏使用 `--g-*` 布局变量（[tokens.css](src/assets/styles/tokens.css)、[layouts/index.vue](src/layouts/index.vue)）。不同导航模式可以隐藏或组合侧栏。启用移动适配时，宽度 < 1024px 进入 mobile 模式。页面另有 1100 / 760 / 640 / 600px 局部断点，共享组件统一在 **760px** 收窄留白。

### 3.2 页面壳层与有效宽度

| 宽度模式 | 最大宽度 | 场景 |
| --- | --- | --- |
| `fluid`（默认） | 1720px | 列表、统计、监控 |
| `standard` | 1440px | 详情、双栏 |
| `narrow` | 1120px | 独立表单（不用于后台业务页头） |

`MrrPageShell` 负责单列可收缩网格与块间距，所有业务页面共用，切换页面时内容宽度与节奏不跳变。布局按扣除侧栏、留白、滚动条后的实际宽度自适应，不按窗口宽度假设列数。嵌套使用时（如监控聚合页的外层壳 + 内嵌监控页）两层宽度上限相同，不会产生双重收窄。

### 3.3 页头

`MrrPageHeader` 有 title / description / eyebrow / icon，以及 badge / meta / actions 插槽，有两种模式（`app.pageTitleStyle`，默认 card）：

- **card**：实色细边框卡片，按内容自然撑开，隐藏英文 eyebrow，说明 13px。
- **plain**：标题位于内容层级，显示 eyebrow，底部留白只有 4px，与下一块的距离交给壳层间距。

**唯一尺寸**：所有后台业务页使用 22px / 700 标题、13px 说明、7px 标题说明间距、32px 可选图标。card 模式上下 16px（≤760px 为12px），左右使用公共卡片 inset；取消固定最小高度及 size 参数。plain 保留无卡面和可选英文眉题，字号与 card 相同。业务页壳层统一 fluid（1720px 上限），设置和帮助页同样使用 MrrPageShell；内部可按内容另设宽度。状态、长说明或操作换行允许自然增高，不裁切。禁止页面单独覆盖页头几何。

页面标题使用 `<h1>`。辅助信息（自动刷新时间、数据范围）放 `#meta`，提示图标放 `#badge`，操作放 `#actions`。页头图标为实色浅底，不用渐变。

### 3.4 页面蓝图

| 页面类型 | 信息顺序与组合 | 设计时必须确认 |
| --- | --- | --- |
| 查询列表 | 页头 → 可选摘要 → `MrrDataTablePanel`（filters / toolbar / 结果 / pagination）；参考 [用户管理](src/views/users/index.vue) | 条件何时提交、结果对应哪次条件、选择和分页何时失效、操作是否符合授权 |
| 游标大表 | 领域查询入口 + 当前批次 + 前后翻批；参考 [扫描记录](src/views/records/index.vue) | 不改成依赖全量总数的页码分页，不把本批数量当全库总数 |
| 分析与监控 | 页头 / 时间范围 → 有口径的摘要 → 图表 → 明细；参考 [扫描统计](src/views/statistics/index.vue) | 指标的范围、单位、分母、更新状态与失败语义；图表与数据同一筛选条件 |
| 关系工作台 / 详情 | 查询上下文 → 摘要与关联列表 → 当前对象详情；参考 [数据关系](src/views/data-relations/index.vue) | 切换对象时哪部分更新、旧预览如何失效；窄屏不丢失对象身份 |
| 配置表单 | 分组导航 → 当前值与说明 → 编辑 / 校验 → 提交结果；参考 [系统设置](src/views/settings/index.vue) | 生效范围、配置来源、未保存状态、失败后的值 |
| 影像档案袋 | 病案确认 → 分类与选页 → 缩略 / 主预览 → 按权限操作；参考 [档案袋](src/views/statistics/archive.vue) | 当前病案与页序、来源失败、选择范围、生成 / 下载状态与资源预算 |

病案号、上架号按原字符串呈现，保留前导零，不加千分位。窄屏重排先定义"识别对象 → 判断状态 → 执行操作"的阅读顺序。

**系统设置**沿用共享页头，标题层级与业务页对齐：页面 `h1`（22px）→ 分区 `h2`（16px / 650，32px 图标）→ 分组卡标题（14px / 650，与 `MrrSectionCard` 同档）→ 设置项标题（13px / 600）→ 说明（12px）。分组卡圆角用 `--mrr-radius-card`、内边距 `--mrr-card-inset`；分组标题样式只在 [UnifiedSettingsPage](src/views/settings/components/UnifiedSettingsPage.vue) 定义一次，各分区不再各写一份。左侧目录只显示图标和分类名；右侧逐组卡片自然滚动。保存入口固定在页头，顺序为"重新加载 → 恢复默认 → 保存设置"。保存前在弹窗中列出"旧值 → 新值"。离开时如有未保存修改，提示"保存并离开 / 放弃修改 / 取消"。界面外观与顶部抽屉共用分组标签页；全站外观同步只推送白名单键，内容未变化时不重复写服务端。

### 3.5 换行、滚动与窄屏

- 页头文案、操作组、分区标题允许换行；用 `min-width: 0` 与 `minmax(0, 1fr)` 让内容可收缩，不靠裁切隐藏操作。
- 表格在自身区域横向滚动，页面主体不被表格撑开。
- 分页在窄屏分别安排总数 / 条数、页码导航与跳页；前后翻页与页码同组。
- 显式 `gap` 的操作组不叠加按钮左边距（`.el-button + .el-button { margin-left: 0 }`）。
- 日期弹层复用 [picker-options.ts](src/ui/provider/picker-options.ts)；抽屉与日期范围按实际容器约束宽度。

---

<a id="components"></a>

## 4. 组件

### 4.1 组件职责

| 组件 / 工具 | 职责 |
| --- | --- |
| [MrrPageShell](src/components/MrrPageShell/index.vue) / [MrrPageHeader](src/components/MrrPageHeader/index.vue) | 页面壳层与页头；见 [§3](#3-布局) |
| [MrrSectionCard](src/components/MrrSectionCard/index.vue) | 分区标题、说明、操作区与 bodyPadding；卡头浅底分隔 |
| [MrrDataTablePanel](src/components/MrrDataTablePanel/index.vue) | filters / toolbar / content / pagination；`count` 只在服务端给出可信总数时传；`loading` 显示刷新进度条，`result-key` 让新结果逐行淡入（见 §5.1） |
| [MrrFilterBar](src/components/MrrFilterBar/index.vue) | 筛选字段与 actions；`embedded` / `labelled` / `layout="grid"` |
| [MrrMetricCard](src/components/MrrMetricCard/index.vue) | label / value / note / icon / tone / valueTone / compactValue / extra |
| [MrrTableActions](src/components/MrrTableActions/index.vue) | 行内 / 更多操作；规则见 [TABLE_ACTIONS.md](TABLE_ACTIONS.md) |
| [MrrStatusTag](src/components/MrrStatusTag/index.vue) | 只读业务状态（文案 + 语义色）；`el-tag` 继续承载分类、计数、可关闭项 |
| [MrrStorageBrowser](src/components/MrrStorageBrowser/index.vue) | OSS / Nginx 只读浏览工作区 |
| [charts](src/components/charts/index.ts) / [useMrrChart](src/composables/useMrrChart.ts) | 图表卡与实例管理；页面不创建第二套实例、主题或尺寸轮询 |
| `AppLoading` / `AppEmpty` / `AppError` | 区域加载、无结果、失败重试 |
| [copyText](src/utils/copy-text.ts) | 复制 + 统一反馈；空串静默，失败给出 Ctrl+C 指引 |
| [useVisibleInterval](src/utils/composables/useVisibleInterval.ts) | 可见性感知的轮询；见 [§7](#7-性能) |
| `actionDialog` | 简短确认与单字段输入 |

### 4.2 按钮：单一实现与语义规格

基础按钮统一使用 Element Plus `el-button` / `el-button-group`，尺寸、状态与配色由公共令牌及 [element-plus-overrides.css](src/assets/styles/element-plus-overrides.css) 管理。布局和内建变体未覆盖的语义规格使用 [form-controls.css](src/assets/styles/components/form-controls.css) 的共享修饰类，不创建第二套按钮实现。

| 语义 | 唯一实现 |
| --- | --- |
| 主要 / 次要 | `type="primary"` / 无 type |
| 轻量 / 文字 | `text` / `link type="primary"` |
| 危险 | `type="danger"` |
| 辅助 | `text` + `mrr-button-secondary` |
| 紧凑 / 大型 | `size="small"` / `"large"` |
| 图标 | `mrr-button-square` 或工具栏共享规格；圆形入口用 `circle` |
| 拼接组 | `el-button-group` + `mrr-joined-buttons` |

使用 `native-type="submit"` 表达表单提交，不将视觉 type 当作原生 type。加载态保持配色并阻止操作，必要时显式 aria-busy；禁用态需清楚区分。守护测试：[button-consistency](src/assets/styles/button-consistency.test.ts)、[button-parity](e2e/button-parity.spec.ts)、[button-migration-visual](e2e/button-migration-visual.spec.ts)。

### 4.2.0 控件唯一实现（V1.2）

Element Plus 是同类基础业务控件的唯一目标，禁止新增重复 Fa 实现或调用；历史 Fa 按组件族迁移，保留有独特职责的 FaIcon、FaKbd 等及 MRR 组合组件。源码清单、迁移与验收见[控件唯一实现与源码使用清单](../docs/design/component-ownership.md)。开关、复选框、滑块、选择器、标签页、按钮/按钮组、输入/文本域、提示/气泡、对话框/抽屉、账户与右键菜单及工具栏已集成；本批头像、进度、分隔线迁移并删除无调用旧卡片、折叠、通知与上传预览链，最终以PR门禁为准。基础卡片使用ElCard，通知使用ElMessage（优先notify公共入口）。保留PIN、滚动区等专用组件及其必要依赖；不为形式统一改写病案查看器。

迁移与视觉重设计分离：默认保留现有页面几何、主题色、字号、字重、布局与交互状态。同环境基线截图/计算样式对照，几何偏差≤1 CSS px；对实际不一致先定位组件默认行为并在恰当归属处修复。测试与记录见归属清单，不宣称跨平台像素完全一致。

### 4.2.0.1 Element Plus 样式归属与级联

`element-plus-overrides.css` 是统一入口，按原声明次序加载 `element-plus/` 下八个职责模块：基础令牌及按钮、字段、轻量浮层及反馈、数据展示、表单几何及查看器、对话框及抽屉外壳、菜单、辅助控件。模块化不改变选择器优先级；不得为形式上的去重跨模块移动存在级联覆盖的规则。公共业务组件样式保持在 `components/`，暖色主题仍最后加载；主题优先修改语义令牌，而不是复制控件规则。新增通用控件状态应进入对应模块，避免在业务页面用更高权重重复覆盖。由 `element-plus-cascade.test.ts` 和已有样式测试锁定实际导入顺序。

公共临时通知由 ElMessage 渲染，新代码优先 `notify.success/warning/info/error`，默认 5000ms、重复分组、无关闭按钮；明确的常驻、关闭、偏移及不分组策略由调用方传参。已有直接 ElMessage 调用不强制改时长，不把持久任务结果或需确认的警告降为短暂提示。保持影像沉浸层上方的消息层级和请求错误去重合同。

本地残余控件审查见[归属审计](../docs/design/local-ui-ownership-audit.md)。原生文件输入、内容型交互按钮（缩略图、可选卡片、导航等）不按标签机械替换；普通行内命令采用 ElButton + `mrr-inline-action`，不得引入工具栏固定高度。图标优先使用 Lucide 语义，FaIcon 仍为统一资源入口（`mrr-icon`）；普通控件图标尺寸及按钮间距分别取 `--mrr-control-icon-size` / `--mrr-button-icon-gap`，不强制覆盖独立空态、指标及 EP 内建图标。

共享布局由对应公共组件或共享样式承担。设置滑块共用 `mrr-settings-slider-control`；局部字段单位宽度、复合字段及导航几何可以保留，需记录理由。服务端主题偏好优先级不因视觉测试修改；指定本地视觉状态的 Mock 用例须隔离共享 UI 配置，真实同步用例仍验证服务端行为。

复合字段内部的小型命令采用 ElButton text + `mrr-embedded-icon-action`，以 `--mrr-embedded-action-size` 和 `--mrr-embedded-action-icon-size` 明确局部尺寸，保留可访问名称和外层字段焦点。普通业务图标优先经 FaIcon 使用 Lucide 名称，不再新增相同含义的独立导入；Element Plus 原生加载与功能图标仍由组件自身管理。

兼容旧浏览器时，危险操作悬停文字、主次操作描边及当前选择状态必须具有独立回退；优先复用语义令牌，现代效果由 @supports 增强或在 @supports not 中明确降级。装饰性效果可降级，但不能导致边界或文字消失。

专用滚动区域统一使用 FaScrollArea 的 `viewport` / `scrollTo` 公开合同，不依赖内部 Reka 组件引用。遮罩跟随实际内容与视口尺寸；横向滚轮映射仅在存在可滚动距离时消费事件，保留原生横向手势、缩放和边界滚动。PIN 组件保持部分输入与外部字符串同步，完成事件仅表示底层完整输入；保留前导零，不将验证码转数字。返回顶部使用 ElButton，并保持键盘聚焦可见、离开后自动隐藏、卸载时清理计时器。

### 4.2.1 字段控件的统一视觉契约

- `--mrr-field-font-size`、`--mrr-field-padding-x(-sm/-lg)`、`--mrr-textarea-padding-y`、`--mrr-textarea-line-height`、`--mrr-textarea-min-height`、`--mrr-field-focus-bg`、`--mrr-field-shadow` 与既有输入高度/圆角/颜色令牌同源。Element Plus 在 element-plus-overrides.css 引用；主题仅改令牌，不重写同一字段选择器。默认文本域最小高度为未明确行数时的基准，显式 rows 尊重组件自身测量。
- 只读仍可聚焦/复制，不等于禁用；错误样式使用 `aria-invalid="true"` 与共享校验样式类或 ElFormItem 错误状态，错误说明需关联真实字段，不能只用红色。保持既有权限、提交和校验行为。
- 归属与实际视觉守护：[form-control-consistency](src/assets/styles/form-control-consistency.test.ts)、[form-control-parity](e2e/form-control-parity.spec.ts)，经典/暖色与明暗均验；现代浏览器证据不替代医院 Chrome86。

### 4.3 筛选栏

- 字段标签说明"这是什么条件"，placeholder 只补充格式或示例；标签关联到真实可聚焦的控件。
- 有可见标签时使用 `<MrrFilterBar embedded labelled>`，字段包在 `.mrr-filter-field` 里。组件提供标签排版与"操作区与控件底边对齐"，页面只定义列宽（flex）。
- 沿用页面既有的显式查询或即时筛选，不为视觉优化增加自动提交。字段清空 ≠ 已重新查询；查询失败 ≠ 没有记录。工具条应显示"已提交条件"，修改未提交时给出提示。
- 复杂筛选使用共享 grid 布局，按实际字段容器宽度（760px / 480px）切换 3 / 2 / 1 列，保留旧浏览器媒体查询回退。高级条件使用 advanced 插槽，折叠不清值、不提交；已填写数量明确区别于已提交条件，默认展开以保留原工作流。窄屏操作区移到下方。

### 4.4 表格与行操作

| 信息类型 | 要求 |
| --- | --- |
| 编号 / 资源标识 | 保留原始编号；长值提供完整读取方式，不泄露内部路径 |
| 数量、比例、耗时 | 单位与精度按契约；用等宽数字 |
| 日期时间 | 复用现有格式化，时区歧义先确认 |
| 状态 | `MrrStatusTag` / 既有映射；未知状态不默认写成成功 |
| 行操作 | `MrrTableActions` + 原权限检查器；图标有名称，禁用原因可读 |
| 批量选择 | 区分本页、本批、已选与全量；确认范围与提交对象一致 |

列宽优先用 `min-width`，固定操作列通过 `useTableActionLayout` 计算宽度及可见按钮数量。选中行使用公共语义色，固定单元格与 hover 保持一致。行内提示浮层不拦截按钮的 Enter / Space 默认激活。行点击、复选、单行按钮、"更多操作"职责分离，点击按钮不会同时切换选中行。

- Select 浮层关闭须严格匹配控件展开状态；快速开合及窄屏重排后不得留有可见浮层。公共桥接不使用 Select 浮层缩放过渡，保留选项反馈、键盘及其他浮层动效。

游标列表（如授权数据表浏览）同样使用 MrrDataTablePanel 与 MrrFilterBar，但不以 ElPagination 的总页数替代后端游标。首屏没有结果时显示骨架；已有结果的再次查询用 loading / result-key，保留上次成功页码与行，提交成功才更新。未知总行数继续标为未统计；请求失败不得伪造空结果。局部分页只负责前后导航及真实状态文案。

### 4.5 指标卡

所有后台指标使用唯一 MrrMetricCard 视觉规格：实色卡面、48px 语义图标块（28px 字形，约58%占比）、16px 图文间距、26px 数值、12px 上下留白。普通指标仅展示名称和数值；最小高度由公共令牌计算为约90.39px，与常规桌面卡片页头一致。普通指标仅保留两行信息。必要说明通过标题行右侧 MrrInfoTip（i 图标）展示，悬停、焦点、点击或触屏可用；主操作使用独立整卡按钮/锚点，与说明触发器互不嵌套。长标题或复杂数值仍可自然换行，不截断。icon 必传，不再提供无图标模式、重复 scope 徽标或独立 dense 字号/高度。相同口径在组级可见文字说明（如用户状态按当前页统计），不同口径写入标签或必要说明，不能把分页结果误作全库总数。网格变体只控制列数及间距，窄屏保留完整内容。 普通工作台的顶部摘要优先一排、最多四张核心指标；次级状态通过 i 信息提示或分区明细承载，不对重叠状态求和。独立诊断或分析指标不得为了减少卡片而删除必要维度。只读卡不做悬停位移；可点击指标保留原生按钮/链接与焦点、操作合同。独立公开服务状态页不是后台指标组件使用场景，本批保留专用摘要。

### 4.6 对话框、抽屉与消息

- 简短确认用 `actionDialog`；表单、预览或进度用页面内的 `el-dialog` 或 [MrrDialog](src/components/MrrDialog/index.vue)；保留列表上下文的详情用 [MrrDrawer](src/components/MrrDrawer/index.vue)。不叠加第二套遮罩、滚动锁或焦点管理。
- 标题说明对象和操作，主按钮用具体动词，确认内容交代对象、范围与结果。
- 关闭按钮、取消、Esc、遮罩点击、提交中关闭的行为要一致；有未保存内容时先确认丢弃策略。
- 打开后焦点进入合适控件，关闭后回到触发入口。窄屏保留关闭与提交入口，正文独立滚动。
- 成功反馈只对应已完成的动作；失败不先关表单再提示成功。

### 4.7 导航

- **主导航**：原生按钮切换分组，用 `aria-pressed` 标明当前分组；悬停色比窄栏底色深一档。
- **次导航**：有子项用按钮 + `aria-expanded`，叶子用链接 + `aria-current="page"`；三种布局统一使用内侧 2px 焦点轮廓。子目录点击不播放涟漪。收起后悬停显示浮层，键盘激活进入首项，Esc 返回触发按钮。
- **移动侧栏**：关闭时移出 Tab 顺序；Esc 把焦点交还工具栏按钮；遮罩与抽屉同节奏。
- **标签栏**：标签可聚焦（Enter 切换），关闭按钮是带名称的 `<button>`，聚焦时可见；关闭 / 新开使用宽度过渡。Alt+←/→、Alt+1..9、Alt+W 快捷键保持不变。
- **面包屑**：稳定 key（路径 + 标题），跳转兄弟页时公共祖先不重播。
- **导航搜索**：只显示当前账号可见菜单，支持标题、URL、面包屑和拼音首字母前缀（如 `yx` → 影像档案袋）。

### 4.8 图表与影像

- 图表提供标题、统计范围、单位与口径；图例颜色不是唯一说明。加载、空、失败用区域状态，不把未获取的数据画成 0。图表区通过 `aria-busy` 表达加载。
- 影像阅读复用档案袋组件与任务机制：失败时定位到当前页或安全的来源类别，不暴露内部地址；重试不切换病案；缩略图与主预览按既有并发与清理机制加载，不同步拉取整案原图。
- 档案袋的病案号、上架号只去首尾空白，保留前导零。分类确认绑定发起时的影像与类型；目标已切换则取消写入。
- 每文件 128MiB 的导出预算与单次打印 50 张上限见 [预算模块](src/utils/archive-export-budget.ts)，属于待真实终端验收的限制，不是内存安全保证。

---

## 5. 交互与状态

<a id="交互状态契约"></a>

### 5.1 交互状态契约

| 状态 | 用户应能判断 | 边界 |
| --- | --- | --- |
| 首次加载 | 哪个区域在加载 | 区域稳定，设置 `aria-busy`；用 `AppLoading` 或图表 loading |
| 刷新 / 再次查询 | 当前提交的条件与结果归属 | 迟到响应不覆盖新结果（请求代次隔离） |
| 成功但为空 | 本次查询确实没有结果 | 区分未查询、无记录、错误；`AppEmpty`（`role="status"`） |
| 失败 / 超时 | 安全原因、重试入口或 Request ID | 不伪装为空；`AppError`（`role="alert"`）；不显示路径、密钥或患者原文 |
| 拒绝 / 会话失效 | 需要重新登录或缺少权限 | 后端仍是授权边界；切换账号后不残留上一账号结果 |
| 禁用 / 处理中 | 为什么不可用 | 不只降透明度；原因可读，防重复提交 |
| 导出完成但下载失败 | 任务完成与下载失败是两回事 | 保留重新下载入口；超限要求分批，不静默丢页 |

**列表的加载与刷新**（共享组件已内置，页面按下面写即可）：

```vue
<MrrDataTablePanel :loading="loading" :result-key="resultVersion">
  <AppLoading v-if="loading && !list.length" type="table" />  <!-- 只在没有结果可保留时用骨架 -->
  <AppError v-else-if="error" … />
  <AppEmpty v-else-if="!list.length" … />
  <el-table v-else :data="list" … />
</MrrDataTablePanel>
```

- `v-if="loading"` 直接整表换成骨架是旧写法：每次翻页表格都消失再出现，页面高度跳动，也丢了「刷新的是哪批结果」。
- `loading`：200ms（`--mrr-motion-busy-delay`）后在表格上方出现 2px 不定进度条，结果变暗到 55% 并暂停交互；内容区 `aria-busy="true"`。快于 200ms 的响应不出现任何反馈。
- `result-key`：用户发起的查询返回后换值，前 6 行依次淡入（只动 opacity）。`useCrudList` 提供 `resultVersion`，静默轮询不递增；自写请求的页面可以直接传结果数组。
- 表格上的 `v-loading` 遮罩已统一为同一外观（顶边进度条 + 浅遮罩，无居中转圈），见 [busy-indicator.css](src/assets/styles/components/busy-indicator.css)。图表、设置分区、档案袋的 `v-loading` 不受影响。

**可写配置页的可保存基线只能来自服务器读取。** 读取失败时，本地缓存与默认值只供只读页面降级使用；配置页显示 `AppError` + 重新加载，读取成功前不算修改、不能保存，也不提供"保存并离开"。已读取成功后再次重新加载失败，保留当前表单与基线并提示。服务器可达但尚无任何设置（全新部署）时以默认值为基线。`loadEffectiveSystemSettings()` 用 `serverReachable` 区分这两种回退。

### 5.2 悬停、选中与焦点

| 状态 | 视觉通道 | 示例 |
| --- | --- | --- |
| 悬停 | 背景加深一档 / 描边加深 / 阴影升一档 / ≤2px 上抬 | 菜单项、可点击卡片 |
| 选中 | 主色描边 + 3px 主色外环，或主色浅底 + 字重 | 档案卡片、预览影像、当前标签 |
| 勾选 | "面"：浅主色填充 | 档案墙批量勾选 |
| 焦点 | `outline: 2px solid var(--mrr-ring)`，`outline-offset` 1–3px | 所有可聚焦元素 |
| 不可放置（拖拽） | 中性描边 + 降低透明度 + `cursor: no-drop` | 首页置顶与普通组件之间 |

悬停态不能使用选中态的外环；勾选（面）与预览选中（线）可以叠加。

### 5.3 键盘与可访问性

- 可点击元素必须可用键盘操作：优先用原生 `button` / `a`；确需用 `div` 时补 `role`、`tabindex="0"`、Enter / Space 处理与可访问名称。
- 纯图标按钮提供 `aria-label`；装饰图标设置 `aria-hidden`。
- 验证 Tab 顺序、标签点击聚焦、下拉键盘操作、浮层关闭后焦点恢复。可访问名称测试通过 ≠ 完成了屏幕阅读器人工验收。

### 5.4 拖拽与排序

- 拖拽时实时区分可放置目标与不可放置目标；不可放置的目标不调用 `preventDefault`，浏览器自动显示禁止光标。
- 位置变化使用 FLIP 位移（`TransitionGroup` 的 `*-move` 类，move 档）。
- 必须提供键盘替代：编辑模式下 Alt + 方向键与相邻项交换，并阻止冒泡，避免触发全局快捷键。
- 触屏设备没有 HTML5 拖拽，键盘 / 菜单式排序就是触屏的替代方案。

---

## 6. 兼容与运行环境

<a id="chrome-86-降级"></a>

### 6.1 浏览器基线：Chrome 86

最低支持 Chrome 86 / Edge 111 / Firefox 114 / Safari 16.4。JS 由 `@vitejs/plugin-legacy`（`modernPolyfills: true`）按实际用法补 polyfill；CSS 由 Lightning CSS 按 `cssTarget` 降级 `inset`、逻辑属性与媒体查询范围语法。[verify-chrome86-build.mjs](scripts/verify-chrome86-build.mjs) 校验产物。

| 特性 | Chrome 86 | 写法 |
| --- | --- | --- |
| `:has()` / `:is()` / `:where()` / `:not(a, b)` | 不支持（整条规则被丢弃） | 禁止，或收进 `@supports selector(...)` |
| `color-mix()` | 不支持 | 见 §6.2 |
| `aspect-ratio` | 88+ | 先写固定高度或 `min-height`，再在 `@supports (aspect-ratio: …)` 中覆盖 |
| `dvh` / 容器查询 | 108+ / 105+ | 先写 `100vh` / 媒体查询回退 |
| `overflow: clip` | 90+ | 先写 `overflow: hidden` |
| `forced-colors` 媒体查询 | 89+ | 渐进增强 |
| `structuredClone` / `Array.at` / `findLast` | 由 polyfill 提供 | 新用到的 API 需加入校验清单 |

构建与静态检查是证据，不能代替医院终端真机验收。

### 6.2 color-mix 回退

装饰性色调可以降级；**数据编码、浮层背景、承载分层的背景、唯一状态信号**必须有实色。回退只认两种写法：

1. **变量级重设**：在 [browser-compatibility.css](src/assets/styles/browser-compatibility.css) 或组件内的 `@supports not (color: color-mix(...))` 块里重设自定义属性或整条声明。
2. 把增强值收进 `@supports (color: color-mix(...))`。

同一规则里"先写静态值、再写含 `var()` 的 `color-mix`"**不是回退**：含 `var()` 的声明在计算值阶段失效为 `unset`，前一条静态值不会恢复。`border`、`box-shadow` 等简写失效时会连带丢掉样式或投影，回退时要重写完整简写。[chrome86-fallback.test.ts](src/assets/styles/chrome86-fallback.test.ts) 会拦截这种伪回退。

### 6.3 国产双核浏览器

360 安全 / 极速、QQ、搜狗、2345 等浏览器访问内网 IP 时，常默认切到 IE 兼容内核。`index.html` 声明 `<meta name="renderer" content="webkit">`、`force-rendering` 与 `X-UA-Compatible`，让它们优先使用 Chromium 内核。仍落在 IE 内核时，会显示"浏览器不受支持"的拦截提示。部署说明里应提示把系统地址加入浏览器的"极速模式"名单。

### 6.4 内网 HTTP（非安全上下文）

医院内网常用 HTTP 部署，以下能力在非安全上下文中不可用：

| 能力 | 处理 |
| --- | --- |
| `navigator.clipboard` | `installClipboardFallback()` 安装 `execCommand('copy')` 降级；页面统一调用 `copyText()` |
| `crypto.randomUUID` / `crypto.subtle` | 调用前做能力检测并提供降级（现有调用点均已检测）；新增调用沿用同一写法 |
| Service Worker / PWA | 仅在生产且 `serviceWorker in navigator` 时注册；HTTP 下自动不注册，不影响主流程 |
| `startViewTransition` | 能力检测，且受动效开关控制 |

### 6.5 Windows 高对比度 / 强制颜色

强制颜色模式会清掉 `background` 与 `box-shadow`，靠浅底或投影表达的选中态会消失。[base.css](src/assets/styles/base.css) 在 `@media (forced-colors: active)` 中用 `Highlight` 描边补回当前菜单、当前标签、选中影像与拖拽目标，并给按钮补 `ButtonText` 边框。新增"只靠背景表达"的选中态时，需加入这份清单。

### 6.6 主题、调色板与设置组合

每次改动共享样式，至少在以下组合下自查：亮 / 暗 × 默认调色板 / 暖色调色板（gr8r）× plain / card 页头 × 动效开 / 关。暖色调色板的规则全部挂在 `html[data-mrr-palette="gr8r"]` 下（[warm-flat.css](src/assets/styles/themes/warm-flat.css)），新增共享组件若有专门的暖色外观，在该文件补充，不在组件里判断调色板。

### 6.7 构建环境

| 模式 | 用途 | 注意 |
| --- | --- | --- |
| `development` | 联调真实后端（/proxy） | 显示应用配置面板 |
| `mock` | 无后端开发与 E2E | `VITE_APP_DEMO_MODE=true` |
| `test` | 测试构建（含 Mock 与 sourcemap） | 未声明 DEMO_MODE，按默认值处理；如需展示模式请显式声明 |
| `production` | 医院正式部署 | 不含 Mock；polyfill 固定开启，不再由环境变量控制 |
| `pages` | GitHub Pages 演示 | base 为 `/MRR/`，CSP 禁止外连，不注册 PWA |

---

## 7. 性能

| 主题 | 规则 |
| --- | --- |
| 轮询 | 统一用 `useVisibleInterval(task, ms)`：页面隐藏时跳过、回到前台补一次、KeepAlive 失活时暂停、卸载时清理。Pinia store 中的轮询需提供 `stop()`，并在布局卸载（退出登录）时调用 |
| 监听 | 不 `deep` 监听 `route`、store 整体或大数组；监听 `route.fullPath` 或具体字段。确需深度监听时，下游要做内容去重 |
| 图表 | 主题变化以"明暗 + 调色板 + 主色 + 卡面色"签名去重，并按帧合并；`<html>` 上无关的 class / style 变化不触发重绘。KeepAlive 失活的图表不渲染 |
| 网络写入 | 自动保存类写操作按内容去重（如全站外观同步），本机状态变化不触发服务端写入 |
| 动画 | 只过渡 transform / opacity / 颜色；不写 `transition: all`，不过渡 `margin` / `width` 等布局属性（标签增删的宽度收拢是唯一例外，且只作用于离场元素） |
| 滚动与尺寸 | 滚动监听加 `{ passive: true }`；尺寸用 `ResizeObserver` 或 `useResizeObserver` 并防抖 |
| 字体 | 预加载界面子集；完整字库懒加载。待办：完整字库切片 |
| 图片 | 缩略图 `loading="lazy"` + 既有并发上限；离页取消请求、回收 Blob URL；不为预览同步加载整案原图 |
| 分包 | 页面与重依赖（ECharts、JSZip、pinyin-pro、导航搜索）保持懒加载；ECharts 只注册用到的图表、组件与 Canvas 渲染器 |

---

## 8. 新旧页面统一

V1.2 当前实施顺序与证据台账见 [全站 UI 规范与组件治理](../docs/design/v1.2-ui-system-roadmap.md)。本文件仍是视觉契约；复用现有组件、按同一相关控件族及页面族集中迁移，不逐页机械换库或为视觉修改业务行为。

### 8.1 迁移状态

| 状态 | 页面 |
| --- | --- |
| 已用 `MrrPageShell` + `MrrPageHeader` | 首页、用户、记录、患者、档案装箱、影像审计、日志、数据关系、OCR 批次、Nginx 浏览、OSS 浏览、UI 组件、接口响应分析、权限管理、运行错误中心、扫描统计、**系统与数据库监控（含监控聚合页）、运维诊断中心、OSS 迁移管理、数据导入导出、病案明细档案袋、认证测试台、数据表浏览**、系统设置、帮助中心 |
| 独立外壳（不迁移） | 登录、强制改密、公共状态页、病案查询 / 影像查询、外部调阅、档案袋独立页 |

业务页面中已没有手写 `.page-shell` / `.page-header` 的使用者，全局 `.page-shell` 兜底样式已删除。

全站后台页头仅使用 `.mrr-page-header`；历史 `.page-header` / `.help-header` 兼容选择器已移除，E2E 使用公共类及语义标题。

仍待逐页处理的是**页面内部**的旧写法，而不是页头：

- `el-card` 分区 → `MrrSectionCard`：**已完成**。监控、OSS 迁移、运维中心、接口响应分析、数据关系、数据交换（含导入面板）、统计明细、认证测试台四个子面板共 39 个自写卡头（`.card-header` / `.panel-heading` / `.panel-header` / `.issue-header`）改为 `title` / `description` / `#actions` / `#title-suffix`，对应页面样式已删除。没有卡头的纯内容 `el-card`（指标格、关系卡、表格容器等）按 §8.2 第 3 步保留。
- `el-empty` → `AppEmpty`：**已完成**（含档案袋、存储浏览器）；弹层、下拉、图片错误插槽等受限空间用 `size="compact"`。存储浏览器的图片读取失败改为 `AppError`。
- `v-loading` → `AppLoading`：按 §5.1 区分首次加载与刷新。**首次加载时区域为空或只有默认值**的改为 `AppLoading` 骨架（权限管理、接口响应分析、数据关系总览、帮助中心）；**刷新**仍用 `v-loading` 叠加在已有结果上，不清空内容。表格、图表卡、影像预览、存储浏览器、档案袋面板属于后者，保留 `v-loading`。
  - 第二批：运行参数、科室档案袋配色、OCR 分类规则、OSS 迁移管理（页头保持可见，内容区骨架）改为首次加载骨架，之后的刷新与操作只叠加遮罩。
  - **保留遮罩的例外**：系统设置的服务器配置分区（系统信息、登录与支持、文档、档案浏览、访问安全、开发者）和 OCR 设置分区里有设置搜索的定位目标（`data-settings-search-target`），首次加载时 DOM 必须在，否则搜索结果会定位到分区标题；OCR 批次页的整页遮罩只在执行操作时出现，用于防重复提交。
  - OCR 设置首次读取失败改为 `AppError` + 重新加载：此前失败后表单显示前端默认值并被判定为「待保存」，保存会用默认值覆盖服务器配置。现在读取成功前不算修改、不能保存。
- 说明图标 → `MrrInfoTip`：**已完成**。权限管理、数据交换、认证测试台、数据导入面板的 6 处 `button.info-trigger` / `.import-safety-trigger` 统一为共享组件（原生按钮 + `aria-label` + 键盘可达 + `data-corner="round"`）。注册表单的 `RegisterFieldLabel` 仍用独立的 `.field-info-trigger`。
- z-index：**已收口**，见 §2.8。
- 页内网格间距 `gap: 12/14/16/18px` 仍是页面局部值，随各页改造时统一到 `--mrr-space-*`。

### 8.2 迁移步骤（按相关组件族集中 PR）

1. 外层 `div.page-shell` → `MrrPageShell`；`header.page-header` → `MrrPageHeader`，`h2` 升为组件内的 `h1`；显式导入组件（单测环境不自动注册）。
2. 删除页面里重复的 `.page-header` / `.eyebrow` / `.subtitle` / `.page-shell` 样式。
3. `el-card` 分区 → `MrrSectionCard`（或保持 `el-card`，删除自定义卡头留白）；`el-empty` → `AppEmpty`；失败面板 → `AppError`。
4. 手写复制 / 轮询 / 等宽字体 → `copyText` / `useVisibleInterval` / `--mrr-font-mono`。
5. 检查单测与 E2E 选择器：E2E 依赖 `.subtitle` 的页面（监控）应先改用 `getByRole` 或组件类名，再迁移模板。
6. 在 [§10](#10-验证与维护) 的组合下截图对比，迁移前后布局不应有非预期变化。

---

<a id="dos-and-donts"></a>

## 9. Do / Don't

| Do | Don't |
| --- | --- |
| 先核对原组件、调用链、令牌与已有测试，再决定最小改动范围 | 不为页面视觉引入第二套请求、认证、Store、主题或状态机 |
| 用真实中文、长说明、原始编号、明暗主题与宽窄屏验证 | 不用隐藏内容、裁切操作或缩小字号代替布局修复 |
| 新样式引用令牌，新组件复用共享组件 | 不在页面写裸色值、裸时长、`z-index: 9999`、自定义等宽字体栈 |
| 动效表达"状态在变化"，并尊重关闭开关 | 不用弹跳、超尺寸缩放、渐变光斑做装饰 |
| 公共 UI 与病案业务解耦 | 不让病案号、姓名、身份证、Token 进入截图、日志、Issue 或 PR |
| 把 Mock / 构建结果写成"静态证据" | 不把 Mock、构建或现代 Chromium 结果写成"医院验收通过" |

---

## 10. 验证与维护

在 `frontend-fantastic-admin` 中按改动执行：`pnpm lint:tsc`、定向 ESLint / Stylelint、对应 Vitest / Playwright；涉及构建或兼容时执行 `pnpm build && pnpm test:compat:chrome86`。浏览器测试使用仓库已有的 Mock 与认证夹具。

常用视口为 375 / 768 / 1024 / 1366 / 1440 / 1920px，按改动选取必要组合，并核对 plain/card、明暗、动效开关、暖色调色板与浏览器缩放。共享布局回归：[shared-ui-layout](e2e/shared-ui-layout.spec.ts)、[control-layout](e2e/control-layout.spec.ts)、[workbench-ui-layout](e2e/workbench-ui-layout.spec.ts)。

| 验证面 | 需要检查的结果 | 证据 |
| --- | --- | --- |
| 任务主路径 | 查询、查看、保存、导出的对象与范围正确，反馈与结果一致 | 定向交互 + API 结果（合成数据） |
| 状态与恢复 | 首次加载、空、失败、拒绝、处理中、重试 / 取消可区分 | 定向状态用例 |
| 键盘与字段 | 标签关联、Tab 顺序、焦点可见、浮层退出 | 键盘操作 + 可访问树 |
| 长内容与缩放 | 长中文、原始编号、说明与操作完整可读 | 记录窗口、缩放与有效宽度 |
| 主题与布局 | 明暗、调色板、页头模式、侧栏与窄屏下层级与操作可达 | 对应截图 |
| 动效 | 进出节奏、方向与语义符合 §2.9；关闭后状态仍可判断 | 动效开 / 关两组截图或录屏 |
| 兼容与环境 | Chrome 86、内网 HTTP、强制颜色下可用 | 静态构建 + 定向用例；真机另行验收 |
| 性能 | 隐藏标签页无轮询请求；主题切换时每张图只重绘一次 | Network / Performance 面板记录 |
| 隐私与权限 | 拒绝状态不泄露，账号切换后状态隔离 | 拒绝 / 会话用例 |

共享组件、令牌或核心交互变更时，在同一 PR 中更新本文件对应规则与证据链接。医院 Chrome 86、Windows / Nginx / 真实存储与人工验收使用 [医院副本验收包](../docs/engineering/hospital-replica-acceptance.md)。

---

## 修订记录

**2026-11-22**

- 结构：按"速查 → 原则 → 令牌 → 布局 → 组件 → 交互 → 兼容 → 性能 → 迁移 → 验证"重排；新增速查清单、对比度基线、z-index、字号阶梯、卡片留白、进出动效语义、兼容与运行环境、性能、新旧页面迁移状态各节；历史叙述与重复段落压缩。
- 事实更正：card 模式页头说明为 13px（原文写 14px）；指标卡已改为实色卡面（原文写"轻色调背景"）；`tokens.css` 圆角回退值已与默认设置对齐为 0.75rem。
- 新增令牌：`--mrr-card-inset(-compact)`、`--mrr-ease-in`、`--mrr-navigation-rail-bg/-hover`、`--mrr-z-*`、`--mrr-font-size-*`。
- 新增工具：`copyText()`、`useVisibleInterval()`。

**2026-11-22（第二次修订）**

- §8 迁移状态更新：系统与数据库监控（及聚合页）、运维诊断中心、OSS 迁移管理、数据导入导出、病案明细档案袋、认证测试台、数据表浏览已迁移到共享壳层与页头；删除不再有使用者的全局 `.page-shell` 样式。
- 数据表浏览原本没有页头，补充了页头；说明文字只陈述页面已有行为（脱敏、大表限行、不缓存）。
- 补充页头 `class="page-header"` 兼容说明，以及页面内部仍待迁移的旧写法清单。

**2026-11-22（第三次修订）**

- 页面内部旧写法迁移：自写卡头 → `MrrSectionCard`；首次加载的 `v-loading` → `AppLoading`；说明图标抽为 `MrrInfoTip`。
- §2.8 新增 `--mrr-z-viewer` / `-ocr-drawer` / `-action-dialog` / `-message` / `-watermark` 令牌和 `constants/z-index.ts`，原先散在 prop 与 JS 里的 3000 / 3100 / 3200 / 3300 / 4000 / 1000 / 1012 全部改为引用，数值不变。

**2026-11-22（第四次修订）**

- §8.1 `v-loading` 迁移第二批与例外说明；OCR 设置读取失败不再显示默认值表单。

**2026-11-22（第五次修订）**

- §2.9：124 处关键字曲线（`ease` / `ease-out`）与 4 处写死时长改为令牌；Fa* 弹层、抽屉、回到顶部、菜单的过渡类改用 `duration-mrr-*` / `ease-mrr-*`（弹层 300ms → overlay 220ms 入场 / move 180ms 离场，菜单 150 / 160ms → move，离场 → feedback）；12 处 `transition-all` 改为具体属性；图片预览缩略图不再悬停放大，改为描边与阴影反馈（§2.9 交互语义「影像悬停」）。

**2026-11-22（第六次修订）**

- §2.5 间距收尾：页面内部 258 处网格外间距改为 `--mrr-space-*`；壳层（顶栏、标签栏、工具栏、次侧栏）与共享组件（分区卡标题行、图表卡摘要、表格行操作菜单、指标卡附加区、确认弹窗变更清单）共 22 处对齐网格。新增「允许保留的非网格值」清单。
- §2.9：写明应用壳层侧栏收起是布局属性过渡的唯一例外及理由；评估过改用 `transform` 的方案，因会破坏主内容区内的 fixed 定位而不采用。

**2026-11-22（第七次修订）**

- §5.1：可写配置页的可保存基线只能来自服务器读取；系统设置读取失败不再静默回退并允许整批保存。

**2026-11-23**

- 暖色调色板（gr8r）优化，仅在 `data-mrr-palette="gr8r"` 下生效，MRR 经典不受影响：
  - 暗色浮层改为 `mutedSurface`，比卡面亮一档（§2.3 层级规则）；对话框底栏跟随浮层色。
  - 暗色状态浅底 10% → 16%、状态描边 22% → 34%，标签在暗色卡面上可辨认；文字对比度仍 ≥ 4.5:1。
  - Element Plus 未桥接的中性令牌（`--el-fill-color*`、`--el-border-color-dark*`、`--el-text-color-placeholder/disabled`、`--el-box-shadow*`）改指 MRR 令牌，消除骨架屏、空态图标、开关轨道的冷灰残留；滚动条、顶栏吸顶投影、数据集切换器同步换成暖色。
  - 按钮与表格字号去掉 12.5 / 13.5px 半像素档，统一 13 / 14px；标签圆角改为跟随全局圆角滑块。
  - 分区卡头左右留白改走 `--mrr-card-inset`（原写死 16px，与表格面板 20px 错位），并补齐 ≤760px 收窄档；表格行悬停改用抬高表面，逐行核对时更易跟随。

**2026-11-23（第二次修订）**

- 新增卡片内摘要条 `.mrr-stat-strip` / `.mrr-stat-tile`（[stat-tile.css](src/assets/styles/components/stat-tile.css)）：放在图表卡 `#summary` 或分区卡正文里，只有「标签 + 数值」两行；页面级、带图标的独立指标仍用 `MrrMetricCard`。扫描统计的趋势摘要、数据质量摘要已改用它（原来两处的标签字号、数值字号、圆角和底色各不相同）。
- 扫描统计「病案明细列表」改用 `MrrDataTablePanel` + `MrrFilterBar embedded`：导出放到卡头操作区，分页从居中改为与其他列表页一致的右对齐分页栏，去掉自写的灰底搜索条和 15px 卡头。
- 影像审计的两张排行卡由 `el-card` + 自写卡头改为 `MrrSectionCard`（标题 16px → 14px/650，和相邻图表卡一致）。
- 13 个业务页面样式中的网格内间距与 12/13/14/16px 字号改为 `--mrr-space-*` / `--mrr-font-size-*`（共 101 处间距、44 处字号，数值不变）；网格外数值按 §2.5 保留。权限页删除无效的 `h2` 规则和重复声明；标签类小圆角改为跟随全局圆角。

**2026-11-23（第三次修订）**

- 系统设置标题层级统一（§3.4）：页面标题 `h2` → `h1`、分区标题 `h3` → `h2`，与业务页一致；分区标题 18px / 700 → 16px / 650，图标 36 → 32px。
- 7 个设置分区重复的分组标题样式（15px + 11px 说明）合并为一处，改为 14px / 650 + 12px，与业务页分区卡同档；分组卡圆角 `radius-xl` → `radius-card`，标题到内容间距统一 16px。
- 设置项标题统一 13px / 600；10 / 11px 的说明、提示、元信息全部提到 12px（`--mrr-font-size-xs` 是正文最小档）。登录页预览缩略图保留原比例，不在此列。

**2026-11-23（第四次修订）**

- 细节打磨（全局，两套调色板通用）：
  - 分页当前页由实心主色块改为主色浅底 + 深色字 + 1px 内描边，与导航选中态同一语汇，不再与主按钮抢注意力。
  - 表头字重 500 → 600；表格整体启用等宽数字，数值列逐行对齐。
  - 对话框标题 17px / 600 → 16px / 650（回到字号阶梯）；空态说明 14px → 13px。
  - 指标卡图标块 52 → 44px（字形 26 → 22px，窄屏 46 → 40px），视线先落在数值上。
  - 复选框圆角跟随全局圆角滑块。

**2026-11-23（逐页优化 · 第 1 批）**

- 用户管理：「创建用户」从筛选栏移到列表卡头操作区，筛选栏只保留查询 / 重置；删除 6 条 `!important` 局部覆盖（卡片上下边、卡头与分页分隔线、30px 页码、去焦点环），回到共享面板外观；只读字段改为与输入框同高（33px）居中；临时密码改用等宽字体。
- 权限管理：「角色」区补分区标题与角色数，权限矩阵改用 `MrrSectionCard`（标题、说明、图例放卡头）；「管理员权限说明」提示只在管理员角色卡显示；角色名 16px → 14px / 650，与分区卡标题同档。

**2026-11-23（逐页优化 · 第 2 批）**

- 记录管理：「批量下载」从页头移到表格工具条，紧挨勾选结果并显示「已选 N 条」；三项批次状态改用紧凑指标（`mrr-metric-grid--dense`），结果表上移；批次号等宽显示。
- 患者管理：9 个固定 `width` 列改为 `min-width`（原合计约 1240px，1366 宽屏操作列被挤出视区）；身份证号改等宽字体，掩码位逐行对齐。
- 档案装箱：视图切换与筛选字段之间加分隔线；「未确认」由危险红改为警示色（待办而非错误，与指标卡口径一致）；箱号链接字重 700 → 600。

**2026-11-23（逐页优化 · 第 3 批）**

- 历史影像识别（OCR 批次）：
  - 状态列由原始状态码改为 `MrrStatusTag`（运行中 / 已暂停 / 取消中 / 已取消 / 已完成 / 失败，未登记的码原样中性显示，原始码保留在 `title`）；只改显示，轮询与按钮判断仍用原始码。
  - 计数列右对齐、等宽数字，失败数 > 0 时标红；批次 ID、续跑游标、等待原因改等宽字体。
  - 行操作由最多 5 个默认按钮改为一组小号按钮，取消 / 回退用危险色区分，按钮名称与行为不变。
  - 预览区的「创建此范围的识别批次」靠右并以分隔线与说明分开；任务选择与「更多病案任务」并为一行工具条。
  - 回退预览对话框：前置说明改为警示提示条，操作按钮移到对话框底栏（原来在长表格下方）。
- 病案明细档案袋：7 处写死冷色投影改为 `--mrr-shadow-*`（暖色 / 暗色主题自动适配）；「档案列表」由 `span` 改为 `h2`，16px / 700 → 14px / 650，与上方分区卡标题同档。
- 病案扫描统计：已在本日第二次修订中改为共享表格面板与摘要条，本批未再改动。

**2026-11-23（逐页优化 · 第 4 批）**

- 新增共享字段说明表 `.mrr-field-grid` / `.mrr-field-item`（[field-schema.css](src/assets/styles/components/field-schema.css)）：数据导入导出页、数据集导入面板、患者导入弹窗三份重复样式（字段名列宽 148 / 128 / 112px 各不相同）合并为一份；字段名改等宽 600。三处导入校验结果改用 `.mrr-stat-tile`。
- 数据导入导出：只读字段说明由 `el-card` + `h3` 改为 `MrrSectionCard`（补 `h2` 层级与说明）；22 个只有占位符的筛选控件补 `aria-label`；数据集切换器的冷色悬停 / 投影改为跟随主题的令牌（`warm-flat.css` 中对应的临时覆盖已删除）。
- 数据关系工作台：病案详情的 5 张计数 `el-card` 改为卡内摘要块；当前病案标题改用 `--mrr-font-size-title-compact`，主档编号等宽；当前对象强调条 4px → 3px，与 OSS「当前任务」卡一致。
- OSS 迁移管理：检查项与日志筛选区圆角由 Element Plus 固定 4px 改为 `--mrr-radius-md`（跟随全局圆角）；日志筛选区补描边。
- OSS 文件浏览：页面只有页头 + 共享文件浏览组件，本批未改。

**2026-11-23（逐页优化 · 第 5 批）**

- 日志管理：自动刷新说明由页头操作区移到 `#meta`（与运行错误中心一致）；Request ID、客户端 IP、请求 URI 改等宽；耗时右对齐；详情里的 Query String / Request Body 代码块与运行错误中心同一外观（浅底、等宽、小圆角）。单测桩补渲染 `meta` 插槽。
- 运行错误中心：可点击的三张指标卡补悬停反馈（描边加深 + 升一档阴影），焦点轮廓圆角与卡片一致；模块名筛选按钮补 `:focus-visible`；错误编号、Request ID 改等宽。
- 病历访问审计：删除页头与明细卡头重复的导出按钮（同一导出，两处文案不同），只保留明细卡头的「导出当前明细」；身份证号、IP 改等宽，耗时右对齐。
- 运维诊断中心：全面体检的检查卡左侧状态条按结果着色（通过 / 警告 / 严重，3px）；图片来源诊断的条件区由无标题 `el-card` 改为 `MrrSectionCard`「诊断目标」，表单项底边对齐。

**2026-11-23（逐页优化 · 第 6 批）**

- 系统监控：数据质量摘要已在本日第二次修订改为 `.mrr-stat-tile`；监控聚合页的「服务可用性状态」由自写卡头 `el-card` 改为 `MrrSectionCard`（标题、说明、操作在卡头）。
- 接口响应分析：慢接口表数值列右对齐，P95（排序依据）加粗，方法与接口模板等宽；趋势图概览的 10 / 11px 小字提到 12px，概览数值等宽。
- 数据表浏览：两张无标题 `el-card` 改为 `MrrSectionCard`「选择数据表」「查询结果」；字段英文名等宽 12px；翻页栏上方加分隔线、统计说明等宽。
- 帮助中心：删除从未生效的页头局部样式（被 page-header.css 更高权重覆盖）；文档卡不再悬停上浮（卡片本身不可点击，§2.7），阴影改令牌；图标块 52 → 44px、标题 19px → 16px / 650，留白改 `--mrr-card-inset`。

**2026-11-23（逐页优化 · 第 7 批：独立页面）**

本批只做不改变结构与尺寸的样式收口；登录页与影像档案袋有大量逐像素 E2E 约束（login-layout / login-switch-spacing / archive-* 共 100+ 处计算样式断言），本批不改动，另立任务。

- 首页工作台：组件固定标签 9px、组件底栏与组件管理说明 10px 提到 12px；隐藏组件计数 10 → 11px（18px 徽标内）并等宽。
- 公共状态页：两处冷色投影改 `--mrr-shadow-xs`；摘要卡圆角 `2xl` → `card`、留白改 `--mrr-card-inset`；眉题、检查时间、图例、「进行中」标签 10 / 11px 提到 12px。分钟热力图的小时刻度 10px 保留（24 行紧凑网格，放大会挤压格子）。
- 身份证查询：写死的蓝色投影改 `--mrr-shadow-md` 并补 1px 描边（暖色 / 暗色主题下不再泛蓝光）；间距改令牌。
- 病案影像查询：页面卡片与下载进度框的黑色投影改阴影令牌；4 处写死圆角改为跟随全局圆角。浮在深色看图区上的类型选择浮层与图片说明条保留原黑色投影。
- 强制改密：眉题与密码强度提示 11px 提到 12px，间距改令牌。

**2026-11-23（逐页优化 · 第 8 批：登录页与影像档案袋）**

先对照 E2E 约束清单再动：登录外壳高度 628px、输入框 38px、标签距 8px、字段组距 16px、表单区上下 60px、切换动画逐帧位置，以及档案袋头部编号输入、类型浮层、全屏看图等均有像素级断言。本批只改不在这些断言路径上的元素。

- 登录页：仅改「客户端环境」浮层内的两类分组小标题 10px → 12px（浮层在登录外壳之外，不影响外壳高度与表单间距；浮层内两列字段的字号未动，宽度断言不受影响）。品牌区、表单区、底部版本徽标未改——已全部使用令牌，且处于高度 / 间距断言路径上。
- 影像档案袋：浮动工具条与空态侧栏的黑色投影改 `--mrr-shadow-md / -sm`；病案列表的编号与副标题、患者卡字段标签、快捷键分组标题 11px → 12px（这些组件不在 E2E 选择器内）。头部编号输入区、类型筛选计数、缩略图遮罩（深色半透明，用于图片上的文字可读性）保留原值。

**2026-11-23（共享组件动效与配色收口）**

- §2.9 新增 `--mrr-motion-stagger`（30ms）与 `--mrr-motion-attention`（900ms）；新增 `mrr-page-enter` / `mrr-live-pulse` / `mrr-value-update` 三个关键帧，只在共享组件内使用。
- `MrrPageShell` 区块依次入场；`MrrStatusTag` 进行中光圈；`MrrMetricCard` 数值更新提示；`AppLoading` 骨架改为令牌尺寸并补表头行。均遵守「只入场、不离场」「关闭动效后状态仍可读」。
- 图标块与状态胶囊配色收口到 tones.css：页头图标、分区卡图标、`MrrStatusTag` 改读 `--mrr-tone-*-soft / -border`，指标卡图标改为同一配方（10% / 22%）。此前四处分别写 8/9/16/18%，调色板对浅底与描边强度的调整（暗色 16% / 34%）到不了这些组件。对应的 `@supports not` 分支随之删除（令牌自带静态回退）。
- `MrrPageHeader` 新增 `size="compact"`（§3.3），13 个查询 / 列表 / 浏览页改用紧凑页头：card 模式下页头从 110px 降到约 64px（省出 46px，正好一行表格行高）。plain 模式、标题层级与插槽结构不变。
- 首页工作台小组件（§5.4 所在页面）：删除底栏「进入模块 → / 拖动调整位置」，入口暗示统一由头部箭头承担，悬停时箭头右移、图标块描边加深；标准卡恢复显示两行说明（原先为放下底栏而隐藏）。网格行高 132 → 116px，窄屏最小高度同步。拖拽、键盘排序、置顶与编辑逻辑不变。
- 表格刷新反馈（§5.1）：新增 `--mrr-motion-busy-delay`、`busy-indicator.css`、`MrrDataTablePanel` 的 `loading` / `result-key`、`useCrudList` 的 `resultVersion`。日志、运行错误中心、病历访问审计、患者、档案装箱五页由「每次查询整表换成骨架」改为「首次骨架、之后保留结果叠加进度条」；用户管理补上刷新反馈。表格 `v-loading` 遮罩去掉居中转圈，改为顶边进度条。
- UI 组件页新增「页头尺寸」（基础与导航）与「动效与刷新反馈」（反馈与浮层）两组示例：默认 / 紧凑页头实时高度对照与长说明折行；进行中光圈、数值更新提示、表格慢查询 / 快查询 / 首次加载三种刷新路径、区块入场重播。示例页头对读屏隐藏（每页只有一个 `h1`），E2E 补两条用例。

**2026-11-23（逐页优化 · 第 9 批：字号下限与键盘可达）**

- §2.4 新增「正文最小档 12px」与有意保留的小字清单，并以 `type-scale.test.ts` 锁定已收口文件。
- 查询诊断（档案袋查询失败弹窗）：18 处写死字号改为字号令牌，其中 5 处 11px（追踪标签、步骤编码、状态胶囊、耗时、样本说明）提到 12px；网格外间距改 `--mrr-space-*`；耗时等宽。
- 每日请求热力图：月份 / 星期刻度 10 / 9px、图例、摘要、浮层与空态 11px 统一提到 12px；摘要与浮层主值 17 / 18px 回到 16px 档并等宽；浮层写死的冷色投影改 `--mrr-shadow-md`（暗色 / 暖色主题不再发蓝灰）。
- 病案明细档案卡：编号、「已选中」、字段标签、页数单位 10 / 11px 提到 12px（含 `statistics-detail-typography.css` 的 `--folder-meta-font-size`）。
- 系统设置「应用配置」面板：调色板说明、强调色说明等 3 处 11px 提到 12px，其余 12 处 12 / 13px 改用字号令牌（数值不变）。
- 顶栏面包屑：可跳转层级由带 `@click` 的 `span` 改为 `RouterLink`（键盘 Tab 可达、Enter 跳转、支持中键新标签页），补 `:focus-visible` 轮廓；当前层级标 `aria-current="page"`，分隔符对读屏隐藏，外层改为带名称的 `nav`。

**2026-11-23（逐页优化 · 第 10 批：病案明细档案卡与扫描统计）**

- 病案明细档案卡改版（§1「工具而非展示」、§2.3 实色表面）：
  - 去掉卡面渐变、右下径向光斑和 5 个各自带底色、描边、图标的字段格子；卡面改为实色，叠层纸保留但改为静态错位，悬停只抬起卡面。卡高由固定 412px 改为随内容（约 270px）。
  - 信息层级：患者姓名为卡内 `h3`（16px / 650），科室与出院日期并为一行 13px，病案号 / 上架号为等宽 14px / 600 的定义列表，页数等宽。顶部 3px 科室色条改为常显（静态身份信号），颜色仍由科室档案袋配色设置决定。
  - 交互（§5.3）：整卡不再是 `role="button"`。选择由姓名处的真实按钮承担（`::after` 铺满卡面，整卡仍可点击）；病案号、上架号各有独立的复制按钮；与「查看影像」三者并列，不再按钮套按钮，读屏分别读出名称。
  - 去掉按病案号哈希随机分配的 5 种卡片色（违反「不用随机色映射」）；首帧用主色，科室配色加载后覆盖。科室配色改读卡片的 `data-department-name`，不再按「住院科室」标签文字反查 DOM。
  - 修正：每页条数计算用的网格间距写的是 18px，样式实际是 16px，列数临界点前 4px 内会算少一列、非末页出现残缺行；常量改为 16 并用测试锁定与样式一致。
- 指标卡色调只在有含义时使用（§2.2）：病案扫描统计、接口响应分析、影像访问审计去掉装饰性的 rose / green / amber / violet / teal；成功率（绿）、异常告警（红）、待处理（琥珀）等语义色保留。
- 扫描趋势图（`MrrDualAxisChart`，仅此页使用）：90 根逐日柱子 `barMaxWidth` 30 → 20、类目间隙 40%、圆角 6 → 3px，柱与柱不再粘成一片；折线 2.5 → 2px，逐日数据不再样条平滑（相邻两天骤降时样条会画出不存在的弧线）。

**2026-11-23（逐页优化 · 第 11 批：首页工作台）**

- 首页的主任务从“配置小组件”改为“查询病案影像”（§1「工具而非展示」）：
  - 新增 `HomeArchiveLookup`：病案号 / 上架号两个带可见标签的输入框 + 「打开档案袋」，校验与档案袋页共用 `getArchiveLookupValidationMessage`，病案号 ≥ 10000000 且未填上架号时标签显示「（必填）」，提示固定占一行，出现时版面不跳动；跳转到 `/archive/embed?bah=&sjh=`，由档案袋页按路由参数查询，首页不直接请求影像接口。编号输入等宽。
  - 右侧「最近查询」复用档案袋搜索历史（同一账号、服务端保存）：只列成功的查询，收藏在前、按时间倒序最多 6 条，每行为一个按钮（编号等宽、影像张数、时间）；读取中显示 3 行占位，无记录时给出说明而不是空白。只读，删除与收藏仍在档案袋页操作。
  - 无 `record:read` 的账号不显示该区域。
- 页头改为紧凑尺寸（§3.3），去掉英文眉题与「N / M 个小组件」徽标；说明改为欢迎语 + 本页用途。
- 小组件区改名「常用模块」，补 `h2` 分区标题与可见数量，「管理模块 / 调整布局 / 恢复默认」从页头移到分区标题右侧（它们只作用于这一区）。小组件本身的尺寸、颜色、拖拽、键盘排序与置顶逻辑不变。

**2026-11-23（逐页优化 · 第 12 批：影像档案袋搜索空态与侧栏操作区）**

先核对 E2E 约束再动：空态只有 `mock-mode.spec.ts` 断言 `.search-card` 圆角 > 0 与「住院病案」标题可见；`archive-code-input.spec.ts` 在查询后的 320px 侧栏里测量三个号码输入框不被截断（字号 ≥ 13px、位数徽标 ≤ 10px），并按 `input[name="archive-*"]` 与 `.search-card` 内的「查询」按钮定位。本批保持这些选择器与侧栏结构不变；全屏看图、分类浮层、平铺模式（`archive-type-select-layer.spec.ts` 等）未改动。

- 搜索空态（尚未查询，`archive-workspace.is-empty`）：
  - `ArchiveSearchBar` 新增 `labelled`：三个输入框上方显示可见标签，病案号 ≥ 10000000 且未填身份证号时上架号标签显示「（必填）」，下方一行写明查询规则（填其一即可 / 大病案号须填上架号 / 身份证号优先并列出全部病案），三个输入框以 `aria-describedby` 关联该说明。查询后的侧栏不启用：标签容器为 `display: contents`，网格与号码宽度和改造前一致。
  - 插画空态改为内联「最近查询」（最多 5 条，收藏在前，一次点击即按该编号查询）；右上「全部记录」打开原有的搜索记录对话框。外部访问与兼容模式没有搜索记录，仍显示原空态。
  - 「刷新」在尚未查询时隐藏（没有可刷新的内容）；头部「最近查询」弹层入口在空态隐藏，避免与内联列表重复。
  - 最近查询列表抽为共享组件 `ArchiveRecentLookups`，首页工作台改用它，删除首页自带的一份列表实现；悬停底色由外层通过 `--recent-lookups-hover` 指定。
- 侧栏底部操作区：原为 2×2 四个按钮，三个「选中」操作各自在文案里重复选中数，唯一不依赖选择的「下载病案（整份）」排在最后。现改为两排：上排「已选 N 张」+ ZIP / 打印 / PDF（可见文字收短，完整说明与进度放在 `aria-label` / `title`），下排整宽的整份下载。外部 / 兼容模式按原规则隐藏对应按钮，剩余按钮平分宽度；整个区域在没有任何可用按钮时不再渲染空的分隔线。
- 修正：PDF 按钮文案此前恒为「导出选中 PDF（N）」，而后台任务进行中 / 已完成时点击实际是取消 / 下载；文案现跟随任务状态（取消 PDF（后端生成 x/y）/ 下载已生成的 PDF），平铺模式工具条同步受益。
- 测试：`ArchiveSearchBar`、`ArchiveRecentLookups` 单测；新增 `archive-search-and-actions.spec.ts`（空态标签、必填提示、刷新隐藏、最近查询打开病案；底部操作区计数、禁用态、选择后启用、320px 内不溢出）。首页单测改用共享组件的类名。

**2026-11-23（对比度与可访问名收口）**

- 修复检测器实测的低对比组合（详见 §2.2 基线表新增行与 [contrast-baseline.test.ts](src/assets/styles/contrast-baseline.test.ts)）：
  - 占位符（`element-plus-overrides.css` 的 EP 三伪类与 `form-controls.css` 的 Fa 控件）不再叠 `opacity: 0.78`：muted 直接命中 ≥ 4.87:1，此前实测仅 1.8:1，暖色调色板下同样受益；select 未选态占位（`.el-select__selected-item.is-transparent`）并入同组并提权。
  - 顶栏「搜索」提示文字去掉 `/60` 透明度档；悬停态改前景色（按钮需带 `group` 类，`group-hover-*` 才会生效）。
  - 首页「置顶」徽标：主色自定义下「主色字 + 主色淡底」可跌破 4.5:1（默认主题 4.49:1），文字改前景色，身份仍由淡底承载；Chrome 86 下淡底回退 `--mrr-muted`。
  - `.el-input__count` 改走 muted；`.el-alert__description` 由 EP 按类型逐条设色（`.el-alert--*.is-light`，(0,3,0)），须逐型提权覆写为 muted（muted 在各 tone-soft 淡底 4.61–4.72:1），`.el-alert__title` EP 不设色、走继承，补一条前景色兜底。上述规则用 `body` 前缀抬过 EP 双类选择器，输入框图标（前缀/后缀/清除/下拉箭头）同样提权到 muted（此前清除图标 2.3:1）。
  - 登录页品牌区：kicker 与英文副标题改为 `color-mix(in srgb, var(--mrr-primary) 62%, var(--mrr-foreground))`——亮色下向近黑压暗（≈8:1）、暗色下向白提亮（≈6.7:1），Chrome 86 回退块内退到前景色；品牌区正文与页脚不再使用 muted 直压渐变（实测 4.46:1），改为前景色 0.72（亮 ≈8.1:1 / 暗 ≈7.3:1）。
  - 浏览器兼容拦截浮层（`public/browser_upgrade/index.css`）：橙底 `#ea580c`（白字 3.56:1）压深为 `#c2410c`（5.18:1），hover `#9a3412`（7.31:1）。该文件是静态首帧壳层，必须保留 `top/right/bottom/left` 四向 longhand 写法（`inset` 是 Chrome 87+ 特性，受众正是更老的浏览器），且不过 Lightning CSS 转译。
- 顶栏三枚纯图标按钮（全屏、页面刷新、明暗切换）补 `aria-label` 与 `title`；全屏与明暗切换的文案随当前状态（进入/退出全屏、切换为亮/暗色主题）。
- 顺带收口（有意扩大的一处，向后续维护者说明）：EP 复选框自带的回弹曲线 `cubic-bezier(.71,-.46,.29,1.46)` 违反 §2.9「不用弹跳」，颜色过渡改走 feedback 档令牌；EP 原第三段 `outline` 过渡被有意移除——焦点环瞬时出现，与 §5.2「焦点用 outline 即时可见」一致。

**2026-11-24（危险操作确认与身份证显式复制）**

- 历史未分类影像识别（ocr-batches）：行内「取消」是不可逆操作，此前一次点击立即执行。现与档案袋导出取消同一范式，先经 `actionDialog.confirm` 确认（警示类型，「取消批次 / 保持运行」，文案交代批次号与不可恢复后果），关闭确认框、Esc、遮罩点击均视为放弃。统一 `attempt` 包装从吞掉所有失败改为走仓库统一的 `getRequestErrorMessage` 中文映射（`utils/request-error-message`）：服务端返回中文 `message` 时原样透出，网络/超时等 axios 错误不再露出英文原文，一律映射为可读中文提示——夜班断网场景不再出现「Network Error」这类裸错误。
- 患者管理（patients）：身份证号「查看/隐藏」与「复制」拆成两个显式动作。此前开启复制设置时，点击号码会在切换显示的同时把完整身份证号静默送进剪贴板（可经云剪贴板同步出内网）。现在：点击号码只切换显示；复制由行内独立的图标按钮发起（`aria-label` 含病案号便于读屏区分行），成功反馈「身份证号已复制」；只开启复制、未开启显示时，号码不再渲染为可点击控件；无身份证号的行只显示 `—` 占位，不渲染查看/复制死按钮。
- 新增 E2E：`e2e/ocr-batch-actions.spec.ts`（取消确认两分支不发/发请求、服务端错误信息透出）、`e2e/patient-id-card.spec.ts`（显示与复制职责分离、剪贴板内容断言、仅复制模式渲染形态、仅显示模式不渲染复制入口、空身份证号行占位形态）。

**2026-11-24（档案袋类型筛选：0 计数类型默认收起）**

- 缩略模式类型筛选栏（`TypeFilterBar`）此前把 15 个正式类型全量平铺，未命中的 0 计数类型一律渲染为禁用灰项——高频页上形成「灰项墙」，操作员要在 15 项里视觉搜索，还容易误读为「没权限还是坏了」。
- 现在默认只渲染 `count > 0` 的类型；顶部摘要行显示「N/15 类有影像」说明收纳状态，右侧「显示无影像类型」开关可展开全部（分类整理时核对类型全集的场景仍可达），展开状态不跨病案记忆（组件不随查询重建，通过监听每次查询整体重建的 `typeStats` 引用变化复位，切换病案 / 刷新后回到收敛默认）；当前选中的类型即使计数归 0 也保持可见，不丢操作目标。摘要行仅在存在 0 计数类型时渲染。
- 全景模式（`archive-wall-type-select` 下拉）与勾选批量选择行为不变。
- 测试：新增 `TypeFilterBar.test.ts`（默认收敛、N/15 摘要与展开开关及其 aria-pressed/文案翻转、0 计数禁用态、选中类型恒可见、切病案 typeStats 重建后复位、selectType / toggleTypeSelection 事件透传）；旧实现上先行确认 3 项新行为断言失败（默认收敛、N/15 摘要与展开、选中类型恒可见），「无 0 计数时不渲染开关」在旧实现上恒通过、作为回归护栏；`src/views/statistics` 域 202 用例全绿。

**2026-11-24（用户管理查询反馈：删除读操作成功通知与人为加载延迟）**

- 用户管理每次「查询」此前必弹右上「查询成功：已加载 N 条账号与注册申请记录」toast（3.2s 存活、自研堆叠逻辑），并强制按钮转圈至少 320ms——全站唯一这样做的页面。读操作的成功通知是噪声：频率高了训练用户忽略 ElMessage（包括真正的失败提示），MrrDataTablePanel 的顶边进度条与 result-key 行淡入已构成「新结果到达」的完整反馈通道；320ms 人为下限单纯放慢高频操作。
- 现在删除 showQuerySuccessNotice、通知堆叠（restackQueryNotices/activeQueryNotices 等约 60 行）与 minDataLoadingDuration 补时逻辑；查询失败仍走原有 error 态与 AppError 通道，按钮忙态只反映真实的请求在途时间。
- 新增 E2E `users-query-feedback.spec.ts`：先热身一轮（固定 mock 编译与首次响应抖动，并等旧实现可能弹出的 toast 完全离场）后，忙态周期用页面内 MutationObserver 记录 `aria-busy` 翻转边沿（`performance.now`，测量不含 Playwright 协议往返——CI 慢环境下协议往返可 >100ms，会污染紧上界断言），断言 < 310ms（旧实现 320ms 下限必然超过）；toast 断言按文案收窄为「不出现含『查询成功』的 ElMessage」，从点击起 1.5s 轮询捕获（旧实现的 3.2s toast 在忙态翻回 false 前必已弹出，该窗口是回归检测窗口）。两组断言均先在旧实现上确认 RED（借 ui-polish worktree 的 mock 服务器复用验证，toast 捕获实测成立），实现删除后转 GREEN。


**2026-11-24（逐页优化 · 第 13 批：术语、空态与表面一致性）**

- 术语文案：记录管理页头描述删除实现层词汇「游标读取」，改为「结果分批加载、显示批次位置与已加载数量，不做总数估算」（措辞与页面实况一致：批次号 + 每批条数 + 当前批记录数，无字面「区间」）；UI 组件演示页 PatternExamples 同步改为一致措辞，不再传播旧术语；OCR 批次表「续跑游标」列改为「续跑起点」；统计表「CID」列标注业务含义「设备 ID（CID）」（依据 `vitepress-doc/internal/data-import/mr-statistics.md` 的字段映射，cid = 设备 ID）。
- OCR 批次空表改用共享空态 AppEmpty（compact），不再显示 el-table 默认「暂无数据」（§5.1「成功但为空」契约）；空态文案指向同页上方的创建入口。
- 监控页缓存命中率指标值缺失时不再残留「-%」（指标卡 note 与 PostgreSQL 明细两处），统一走 `formatPercent`：缺失显示「-」，避免被误读为「0% 且格式坏了」。
- 用户管理对话框表面统一：创建/审核注册/编辑此前使用 `mrr-vscode-glass` + `mrr-vscode-overlay`（`--mrr-popover` 表面 + 自定遮罩色），重置密码/禁用/临时凭据为全站默认对话框表面——同页两种质感。glass-surface.css 明确该效果仅限外观设置面板/导航搜索等既有入口、不扩展到业务表面，故移除这三处专属类，全页 6 个对话框回全站统一表面。
- 范围说明：统计页「年份下拉与日期范围双控件合并」「监控『严重数据异常』指标卡跳转异常样本」属于交互结构改动，不在本 P3 批次内，另行立 Issue。

**2026-11-24（逐页优化 · 第 14 批：监控时间线、口径徽标与报障引导核查）**

- 监控页分区最后成功时间：`lastRefresh` 只在全成功时更新，部分失败持续时页头恒显「上次完整刷新 --」，运维无法区分「数据 3 分钟旧」和「从未成功」。现在页头在没有完整刷新记录时显示「尚未完成过一次完整刷新」，有记录时保留原「上次完整刷新 HH:MM:SS」语义；部分失败的告警描述追加分区级时间线——曾成功过的分区显示「X上次取得数据 HH:MM:SS」，从未成功过的分区显示「X本次会话尚未取得过数据」，7 个分区（健康状态/运行时间/内存/系统信息/数据库概览/数据质量摘要/数据质量明细）与请求顺序一一对位，同轮刷新统一取一次时间戳；时间格式化对齐全站规范 `toLocaleTimeString('zh-CN', { hour12: false })`。既有断言（/上次(?:完整)?刷新/、标题「本次监控刷新未完整完成」、12:00:00/12:03:00 时间戳、原句「未取得新数据，相应区域可能显示上次读取的值。」）全部保留；新增 E2E 覆盖「从未完整刷新不再恒显 --」「分区级两分支文案」「恢复后既有语义完整」与用例 1 补「上次取得数据」分支断言。
- 用户管理指标卡口径徽标：`MrrMetricCard` 新增可选 `scope` 口径（类型 `MetricScope` 导出于 `types.ts`），label 行右侧渲染「本页/全库」胶囊徽标（`--mrr-muted-foreground` 在 `--surface-alt` 上明暗主题均 ≥4.5:1——弱化档 `--text-tertiary` 亮色仅 2.86:1 不可用；12px 走 `--mrr-font-size-xs` 令牌，无裸字号）。四卡数据来源核实后全部标注「本页」：「已启用」的计数实为当前页筛选结果，若按原计划标「全库」恰是本功能要防的口径误读（远程复核拦下）；其 note「系统共 N 个账号」在筛选条件下同样不诚实，改为「当前条件共 N 个账号」（服务端按当前条件返回的总数）。`e2e/user-filter-labels` 增哨兵断言：四卡 `data-scope="page"` 计数为 4、「已启用」note 含「当前条件共」（light/dark 双主题）；组件单测覆盖三类形态（page/total/未传不渲染）。真正的全库启用数需要后端统计接口，本批以诚实标注收口，接口需求另行立 Issue。
- 失败路径报障引导核查：全站 RequestId 暴露点逐点核对「展示 + 报障引导」两要素。`ArchiveQueryFailure`（追踪编号 + 复制 + 引导）与 `ScanQueryDiagnostics`（诊断 + 跳转日志）两要素齐备；日志/运行时错误/审计/运维中心页面中 requestId 是管理员与审计视角的检索数据，不属报障对象；通用 `AppError`/`request-error-message` 不渲染 ID，不构成「展示无引导」缺口。唯一缺口是全局兜底 toast（`showGlobalError`）：展示「（请求 ID：xxx）」但无引导，现改为「（请求 ID：xxx，报障时请提供给管理员）」，无 ID 的纯网络错误保持纯消息不加引导；该路径仅在无页面级 catch 的兜底触发，不产生双 toast。新增 `global-error-toast.test.ts` 正向（有 ID 含引导）与负向（无 ID 不含「请求 ID」与引导、退化为纯消息）用例，RED→GREEN。页面级失败面板统一展示+引导属通用错误组件改造，不在本批，记录待后续 Issue。

**2026-11-24（浏览器升级拦截页恢复聚焦轮廓）**

- 浏览器升级拦截页（`public/browser_upgrade/index.css`，零转译静态壳层）此前 `:hover`、`:focus-visible` 一并 `outline: none`，违反 §5.2「焦点用 outline 即时可见」——该页是不满足 Chrome 86 基线用户的最后可视通道，键盘焦点被抹掉即失能。现拆分：hover 仍去轮廓但经 `:not(:focus-visible)` 让位键盘焦点（键盘 Tab 聚焦后指针再悬停同一按钮时环不消失，WAI/GOV.UK 惯例去轮廓必须排除键盘焦点；<86 该条含 `:not(:focus-visible)` 整条丢弃，hover 底色与改前持平、改前该规则组在 <86 同样不生效，无劣化）；`:focus-visible` 恢复 `2px solid #c2410c` 轮廓 + `outline-offset: 2px`（两属性 Chrome/Edge 老内核均支持，无 chrome86 风险）。环色不用 currentcolor 而定死 `#c2410c`：主按钮 `.button-primary` 文字为白（`color: #fff`），环画在按钮盒外落在 `.browser-compat-content` 白底上，currentcolor 会白环白底 1:1 不可见——定色与文件既有 accent 一致、对白底 5.18:1，亦是仓库既有惯例（`RegisterFieldLabel.vue` 定色 outline 而非 currentcolor）。由 `public-static-files.test` 两面锁定（存在非 none/0 轮廓的 `:focus-visible` 规则 + 任何 `:focus-visible` 块无 outline 重置残留，`:not()` 内嵌豁免），与 chrome86 门禁同文件的静态层哨兵，RED→GREEN。兼容事实：`:focus-visible` 伪类本身是 Chrome/Edge 86+、Firefox 85+、Safari 15.4+，不足 86 时未知伪类导致整条规则丢弃、由 UA 默认焦点环兜底（非「最后可视通道」在 <86 由本规则生效——该通道在 <86 由 UA 兜底）；拦截层受众 Edge 86-110 / Firefox 85-113 / Safari 15.4-16.3 均支持，修复对受众大多数真实生效。

### 业务页面同类交互一致性补充（V1.2）

- 常规列表查询/重置使用 MrrFilterActionButton 和中性按钮；写入、导出、批量下载等主要任务按明确主次使用 primary。已建立真实请求反馈模式，使用者不得为高频查询补足人工等待；原有动画反馈模式暂保留兼容。
- 有可见标签的筛选字段（原生标签、EP 网格表单）统一 12px/600、18px 行高、主要文字色、4px 间距；复选框仅保留一个原生可访问名称。紧凑无标签布局仍需可访问名称。
- 同类列表页采用统一 MrrPageHeader 与英文业务眉题；可随说明或元信息换行，不用固定高度裁切。
- 同语义查看操作使用 MrrTableActions 的 eye/primary，窄屏进入更多菜单。不同任务的专用操作布局保留。
- 五项指标用 mrr-metric-grid--five 均衡排布，不影响其他数量的指标；网格筛选 601–1100px 双列、600px 以下单列，日期范围占整行，不隐藏已生效条件。

### 2026-11-26 · 用户列表信息层级

有完整分页总数的业务列表不重复显示卡头数量；保留单一底栏总数，桌面左侧总数与右侧分页操作分组。单页不显示跳页输入，多页保留。用户身份将用户名与显示名在头像旁分层呈现，搜索框优先分配剩余宽度，枚举筛选按内容尺寸约束。角色为中性信息，全部凭据与账号状态统一使用 MrrStatusTag 浅色背景、细边框及语义点；账号状态独立显示，时间等宽数字。保留固定操作列、权限、申请信息及原查询参数，窄屏使用既有共享布局。先在用户管理验收，不直接全站替换。

### 2026-11-26 · Quiet Enterprise UI：克制型业务表格

本项目自己的可选表格视觉语言，不是第三方设计系统的官方名称。参考 [IBM Carbon Data Table](https://www.carbondesignsystem.com/building-blocks/core/components/data-table/guidelines)、[Atlassian Spacing](https://atlassian.design/foundations/spacing)、[SAP Fiori Responsive Table](https://www.sap.com/design-system/fiori-design-web/v1-108/ui-elements/responsive-table/responsive-table-content-formatting-cheat-sheet)。

- **单一表面**：MrrDataTablePanel 的 appearance="quiet" 统一卡头、表头、分页表面色；表格铺至卡片边界，去掉内层圆角、重复分隔和表格末行重复边线。通过行分隔、字号和留白而非嵌套色块建立结构。
- **共同关键线**：标题、搜索区、首列表头/数据与底栏数量共用 card-inset（窄屏 compact）。表头36px、普通双行身份记录52px，内容复杂自然增高，固定操作列保留。搜索宽度最大480px，枚举筛选144px，操作相邻；创建等主要写操作可独立置于同一工具栏最右侧，与查询/重置同高，宽度不足时自然换行并保持右对齐；避免横向溢出。
- **语义克制**：身份头像中性32px圆角方块；用户名600字重、显示名12px次级字。角色使用普通正文；正常凭据及启用状态使用绿色完整状态标签，异常沿用原语义标签；同类状态保持同一外观结构。仅创建按钮使用实色主色。
- **同一交互语法**：MrrTableActions appearance="quiet" 的普通行内编辑及更多使用同尺寸、无底色图标，悬停/键盘聚焦出现浅底，危险和审核语义仍保留；不替换权限、确认或菜单合同。
- **单一总数与渐进披露**：分页总数在底栏左侧，导航在右侧；仅多页显示跳转。不删除申请信息、拒绝原因、最近登录或任何业务字段。
- 此版仅用户管理启用，其他共享面板及行操作保持原默认外观；需维护者确认并按业务风险验收后再推广。

### 2026-11-26 · 无标题列表与布局稳定性

单一主列表在页面标题已充分说明用途时可省略可见分区标题（MrrDataTablePanel title 可选）；必须保留可访问区域名。quiet 无标题面板的筛选区顶部使用16px、窄屏12px实际安全留白，保留原圆角裁切和焦点环，不通过隐藏焦点或全局 overflow visible 修复。用户管理四张摘要卡始终存在，首次加载/失败显示 —，重新查询时保留上一份成功数值，成功空结果显示0。指标仍仅按当前成功加载页统计，不请求全库聚合。避免基于结果计数隐藏整组卡片或改变工具栏纵向位置；表体可随内容自然变化。其他有标题分区保持原视觉，其他历史零值收纳规则后续按业务验证后再治理。

### 2026-11-26 · 轻量分页与行操作菜单

用户列表分页使用 Element Plus 原有分页合同及 slot，页大小以无边框文本按钮展示，按10/20/50/100循环切换，返回第一页并只发一次查询；可键盘激活，悬停说明下一档。桌面页码及箭头点击区域28px、箭头字形12px，窄屏导航区域32px。quiet 行操作的 teleported 菜单使用公共 menus.css，内容自适应宽度（不设固定最小宽度，左右各约13px留白，最长项决定菜单宽度，视口限制内长文字自然换行）、32px基础行高（窄屏36px）、13px正文、16px图标、统一圆角与浅阴影，无浮层尖角；普通操作中性，危险操作保留红色及语义悬停背景，禁用操作降低对比且不可触发。重置密码的风险由原确认弹窗表达，仍需管理员密码；禁用仍需确认，不改变权限及业务语义。其他默认外观不变。

### 2026-11-26 · 表格长文本渐进披露

用户申请信息仅展示单行摘要，优先申请说明，回退联系方式、拒绝原因、申请时间或注册申请标识；超出单元格可用宽度用省略号，不按字数截断原始数据。摘要为可键盘访问的轻量文本按钮，展开图标保持可见。点击复用 MrrDialog 只读详情，分区展示身份、状态、联系方式、申请与审核时间、完整申请说明和拒绝原因；保留换行、长连续文本换行和弹窗内部滚动。管理员创建且无申请信息的账号继续显示普通文字。不得把查看详情与审批写操作混合，保留原权限及确认流程。

### 2026-11-26 · Quiet 表格行背景连续性

普通列与固定列使用同一行级背景变量，静止、悬停、斑马纹、选中状态遵循同一语义优先级（选中高于悬停）。固定列保持不透明以遮挡横向滚动内容。Quiet 表格取消单元格独立的背景色过渡，避免不同初始背景及绘制层导致过渡时颜色断层；按钮自身悬停与键盘焦点反馈不变。其他默认表格不改变。

### 2026-11-26 · 用户状态一致性与申请排序

数据量增大后恢复全部账号/凭据状态的公共 MrrStatusTag 外观，不局部剥离正常/启用标签。申请摘要按钮及内部文字容器统一左对齐，普通来源文字与摘要共享单元格内容起始线，保留省略和详情入口。Mock 列表按正式 AuthUserMapper 既有契约在分页前排序：待审核优先、申请/创建时间倒序、ID 倒序；前端不重排当前页，不改变后端查询。

### 2026-11-26 · 用户操作列与审核状态转换

所有可管理用户行固定96px操作列、一个铅笔与一个更多入口，窄屏亦保留；铅笔按状态打开审核、恢复待审核或编辑，更多菜单承载相应次级操作。已拒绝申请恢复使用专用 POST registration/reopen，不直接启用；确认后重新进入待审核并刷新第一页，保留申请、上次拒绝原因与凭据，须再次审核。已禁用账号通过普通编辑预选启用并保存。待审核/已拒绝不得通过通用编辑或禁用绕过审核，权限与本人保护不变。公开静态演示继续禁止真实账号审核及恢复写入，浏览器测试使用隔离模拟响应。

### 2026-11-26 · 注册申请弹窗归一

申请详情、审核注册申请、恢复注册申请使用同一个既有 MrrDialog 实例与模式状态，不新增弹窗组件，底层仍为 Element Plus。共享双列 review-summary（用户名、名称、状态、联系方式、申请时间、已有审核时间、申请说明及历史拒绝原因）。仅审核显示授予角色及通过/拒绝，恢复显示风险提示与确认，详情只读。采用公共 overlay-shells.css 的可选 business 几何：540px上限、卡片底色、16px标题、桌面20px内容对齐线、窄屏16px、完整底栏、内容内部滚动；使用既有令牌，其他 MrrDialog 外观不变。确认用主色，拒绝用危险色；提交期间锁定关闭与重复提交，保留原 actionDialog 拒绝原因验证、权限、API 和恢复待审核语义。

### 2026-11-26 · 全站业务弹窗与分页反馈

普通业务 MrrDialog 默认 business 外观、水平/垂直居中，540px 默认宽度（允许业务显式宽度）；直接 ElDialog 使用同一公共外壳与原生 align-center。标题、正文、底栏统一对齐线，长内容在正文滚动，固定标题/操作区。ActionDialog 复用 MrrDialog，保持队列、取消、校验和层级；危险确认可显式使用 danger。用户管理其余弹窗复用已有封装。个人设置、导航搜索保留 custom 外观；导航搜索保留命令面板顶部定位，抽屉不变。
分页页码统一 4px 间距；原生 jumper 采用无边框数字的静止态，点击或键盘聚焦显示输入反馈，保留原生跳页校验；窄屏与页码同一导航区，允许页码换行。返回顶部图标 16px，点击区域及隐藏计时/键盘行为不变。
