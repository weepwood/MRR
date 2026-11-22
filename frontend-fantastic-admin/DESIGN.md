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

单页测量不能证明全站达标；新增配色需按同样方法复核明暗两套。业务配色复用语义色，不用随机色映射科室、分类或状态。

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
| 页面标题 | `--mrr-font-size-title` / `-title-compact` | 26px / 22px（≤760px），700 | [page-header.css](src/assets/styles/components/page-header.css) |
| 页头说明 | `--mrr-font-size-md` / `-sm` | plain 14px；card 13px | 同上 |
| 分区标题 | `--mrr-font-size-md` | 14px，650 | [MrrSectionCard](src/components/MrrSectionCard/index.vue) |
| 正文 / 表格 | `--mrr-font-size-md` / `-sm` | 14px / 13px | base.css、Element Plus 桥接 |
| 标签、说明、表头 | `--mrr-font-size-xs` | 12px | metric-card.css 等 |
| 指标主值 | — | 26px，680，等宽数字 | [metric-card.css](src/assets/styles/components/metric-card.css) |

新样式从字号令牌取值；存量页面随迁移逐步替换。说明文字围绕数据口径、时间范围或操作方法；关键提示不能只靠省略号截断。

### 2.5 间距与卡片留白

- 间距令牌按 4px 递进：`--mrr-space-1/2/3/4/5/6/8` = 4/8/12/16/20/24/32px。
- **块间距**：页面内纵向块之间用 `--mrr-page-gap`（24px），≤640px 时 16px。由 `MrrPageShell` 或全局 `.page-shell` 提供，页面不再各写 16/18/20px。
- **卡片留白**：`--mrr-card-inset`（20px）/ `--mrr-card-inset-compact`（12px，≤760px）。页头卡、分区卡、表格面板（筛选区、工具条、表体、分页）、筛选栏、指标卡、旧页 `el-card` 共用这一条左右留白，纵向堆叠时标题、字段、表格外沿、分页落在**同一条竖线**上。
- **工作区留白**：`clamp(16px, 2vw, 24px)`，≤760px 为 16px。
- **允许保留的非网格值**（不要"顺手"改成令牌）：
  - **描边补偿**：输入框、选择器、文本域、分页尺寸选择的 `11px` / `9px` 横向内边距加上 1px 描边，正好让文字落在 12 / 10px 竖线上。
  - **由高度令牌决定的密度**：表格单元格 `padding-block: 6 / 7px`（行高由 `--mrr-table-header-height` 等决定）、次侧栏菜单项纵向 `6px`（配合 `min-height: 34px`；改成 8px 每项会高 4px）。
  - **文字级微距**：标签到控件（`el-form-item__label`、筛选栏网格标签 6px）、标题到说明（页头说明 7px、指标卡说明 6px）、分组眉题、状态点与文字基线对齐。
  - **按钮图标间距 6px**：`el-button` 与 `FaButton` 两族刻意取同一值，要改必须两边一起改。
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

**G2 连续圆角**（`app.radiusShape = squircle`，`corner-shape: squircle`）只改圆角内侧曲率，不改大小，Chrome/Edge 139+ 之外自动降级为圆弧。它只适用于圆角矩形。开关、滑块、正圆、胶囊，以及由正圆零件组成的复合控件，通过 `--mrr-corner: round` 连同子树退出。新增同类元素沿用 `*-dot` / `*avatar*` / `*swatch` / `*spinner` / `*-marker` 命名，或加 `data-corner="round"`。两套控件（`.el-switch/.el-slider` 与 `.fa-switch/.fa-slider`）都需登记。详见 [corner-shape.css](src/assets/styles/components/corner-shape.css)。

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
| `narrow` | 1120px | 设置、表单 |

`MrrPageShell` 负责单列可收缩网格与块间距，所有业务页面共用，切换页面时内容宽度与节奏不跳变。布局按扣除侧栏、留白、滚动条后的实际宽度自适应，不按窗口宽度假设列数。嵌套使用时（如监控聚合页的外层壳 + 内嵌监控页）两层宽度上限相同，不会产生双重收窄。

### 3.3 页头

`MrrPageHeader` 有 title / description / eyebrow / icon，以及 badge / meta / actions 插槽，有两种模式（`app.pageTitleStyle`，默认 card）：

- **card**：实色细边框卡片，最小高度与指标卡一致（110px），隐藏英文 eyebrow，说明 13px。
- **plain**：标题位于内容层级，显示 eyebrow，底部留白只有 4px，与下一块的距离交给壳层间距。

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

**系统设置**沿用共享页头，标题层级与业务页对齐：页面 `h1`（26px）→ 分区 `h2`（16px / 650，32px 图标）→ 分组卡标题（14px / 650，与 `MrrSectionCard` 同档）→ 设置项标题（13px / 600）→ 说明（12px）。分组卡圆角用 `--mrr-radius-card`、内边距 `--mrr-card-inset`；分组标题样式只在 [UnifiedSettingsPage](src/views/settings/components/UnifiedSettingsPage.vue) 定义一次，各分区不再各写一份。左侧目录只显示图标和分类名；右侧逐组卡片自然滚动。保存入口固定在页头，顺序为"重新加载 → 恢复默认 → 保存设置"。保存前在弹窗中列出"旧值 → 新值"。离开时如有未保存修改，提示"保存并离开 / 放弃修改 / 取消"。界面外观与顶部抽屉共用分组标签页；全站外观同步只推送白名单键，内容未变化时不重复写服务端。

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
| [MrrDataTablePanel](src/components/MrrDataTablePanel/index.vue) | filters / toolbar / content / pagination；`count` 只在服务端给出可信总数时传 |
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

### 4.2 两套按钮：同一种控件，两种实现

Element Plus `el-button` 与 reka-ui `FaButton`（`.fa-button-control`）**尺寸与状态同源**：`--mrr-button-font-size(-sm/-lg)` 与 `--mrr-button-padding-x(-sm/-lg/-text)` 是唯一数值来源。两侧只引用令牌，交互状态由 [form-controls.css](src/assets/styles/components/form-controls.css) 按 `[data-variant]` 统一定义；主题换密度只改令牌。

| 语义 | FaButton | el-button |
| --- | --- | --- |
| 主要 / 次要 | `default` / `outline` | `type="primary"` / 无 type |
| 轻量 / 文字 | `ghost` / `link` | `text` / `link type="primary"` |
| 危险 | `destructive` | `type="danger"` |
| 紧凑 / 大型 | `size="sm"` / `"lg"` | `size="small"` / `"large"` |
| 图标 | `size="icon"`（方形，工具按钮） | `circle`（正圆，图标入口） |

同一组控件里不混用方、圆两种图标按钮。加载态保留变体配色并阻止点击、设置 `aria-busy`；禁用态不只降低透明度，需可读出原因。守护测试：[button-consistency.test.ts](src/assets/styles/button-consistency.test.ts)、[button-parity.spec.ts](e2e/button-parity.spec.ts)。

### 4.3 筛选栏

- 字段标签说明"这是什么条件"，placeholder 只补充格式或示例；标签关联到真实可聚焦的控件。
- 有可见标签时使用 `<MrrFilterBar embedded labelled>`，字段包在 `.mrr-filter-field` 里。组件提供标签排版与"操作区与控件底边对齐"，页面只定义列宽（flex）。
- 沿用页面既有的显式查询或即时筛选，不为视觉优化增加自动提交。字段清空 ≠ 已重新查询；查询失败 ≠ 没有记录。工具条应显示"已提交条件"，修改未提交时给出提示。
- 窄屏字段整行、操作区移到下方并以分隔线隔开。

### 4.4 表格与行操作

| 信息类型 | 要求 |
| --- | --- |
| 编号 / 资源标识 | 保留原始编号；长值提供完整读取方式，不泄露内部路径 |
| 数量、比例、耗时 | 单位与精度按契约；用等宽数字 |
| 日期时间 | 复用现有格式化，时区歧义先确认 |
| 状态 | `MrrStatusTag` / 既有映射；未知状态不默认写成成功 |
| 行操作 | `MrrTableActions` + 原权限检查器；图标有名称，禁用原因可读 |
| 批量选择 | 区分本页、本批、已选与全量；确认范围与提交对象一致 |

列宽优先用 `min-width`，让操作列在常见视口内不被挤出。行点击、复选、单行按钮、"更多操作"职责分离，点击按钮不会同时切换选中行。

### 4.5 指标卡

实色卡面 + 图标语义色块 + 数值色（`valueTone`），没有顶部色条和斜向渐变。图标桌面 52px、≤600px 时 46px，保持正方形；无图标卡只保留正文列。最小高度 110px，与 card 页头一致。网格变体用 `mrr-metric-grid--compact/three/four/wide/dense`，不在页面复制布局。只读指标卡不做悬停位移；可点击的指标卡（如运行错误中心的"仅看 ERROR"）外层是 `<button>`，焦点用 outline。

### 4.6 对话框、抽屉与消息

- 简短确认用 `actionDialog`；表单、预览或进度用页面内的 `el-dialog` / [FaModal](src/ui/components/FaModal/index.vue)；保留列表上下文的详情用 [FaDrawer](src/ui/components/FaDrawer/index.vue)。不叠加第二套遮罩、滚动锁或焦点管理。
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

### 8.1 迁移状态

| 状态 | 页面 |
| --- | --- |
| 已用 `MrrPageShell` + `MrrPageHeader` | 首页、用户、记录、患者、档案装箱、影像审计、日志、数据关系、OCR 批次、Nginx 浏览、OSS 浏览、UI 组件、接口响应分析、权限管理、运行错误中心、扫描统计、**系统与数据库监控（含监控聚合页）、运维诊断中心、OSS 迁移管理、数据导入导出、病案明细档案袋、认证测试台、数据表浏览** |
| 独立外壳（不迁移） | 登录、强制改密、公共状态页、病案查询 / 影像查询、外部调阅、档案袋独立页、系统设置（`.settings-page`）、帮助中心（`.help-center`） |

业务页面中已没有手写 `.page-shell` / `.page-header` 的使用者，全局 `.page-shell` 兜底样式已删除。

两个页面给 `MrrPageHeader` 额外加了 `class="page-header"`（运维诊断中心、Nginx 浏览），这是为了保留 [page-header-style.spec.ts](e2e/page-header-style.spec.ts) 对 `.page-header h1` 与卡片样式的断言入口；新页面不需要这个类，E2E 优先使用 `getByRole('heading')` 或 `.mrr-page-header`。

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

### 8.2 迁移步骤（每页一个 PR）

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
