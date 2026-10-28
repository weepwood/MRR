# MRR Design System


## 0. 业务指标卡片（Metric Card）

病案扫描数据统计页的顶部卡片是 MRR 的标准业务指标卡。凡是用于展示“总量、比例、耗时、状态、趋势周期”等单一核心指标的卡片，都应复用这一结构，不再在页面内重新设计。

### 0.1 适用范围

- 页面首屏的 3–6 个核心指标。
- 统计总览、系统监控、审计分析、迁移任务等数字摘要。
- 需要通过颜色区分业务语义，但不需要承载复杂交互的卡片。
- 普通表单面板、图表容器、筛选区和档案袋卡片不使用本规范。

### 0.2 标准结构

1. **顶部语义色条**：3px，高度固定，用于快速区分指标类别。
2. **图标容器**：52×52px，14px 圆角；使用语义色的 10% 淡色背景。
3. **标签**：11px、600–650 字重、大写或短标签，单行省略。
4. **数值**：26px、700–750 字重，使用等宽数字特性，避免数据刷新时跳动。
5. **说明**：12px 次级文本，可选；只解释指标口径，不重复标签。

### 0.3 布局与尺寸

- 标准网格：`repeat(auto-fit, minmax(220px, 1fr))`，间距 16px。
- 紧凑网格：最小宽度 180px，适用于 5 个以上并列指标。
- 卡片内容区：水平布局，间距 18px，内边距 16px；与卡片式页头共享 110px 最小高度，长文案自然撑开。
- 标准圆角：14px；边框使用语义色与 `--divider` 混合后的低对比边框。
- 阴影必须克制：静态指标卡悬停时不变色、不抬升，避免产生可点击的误导；卡片内存在可聚焦控件时，仅在 `:focus-within` 提供焦点反馈。
- 指标说明允许完整换行，不省略统计口径，也不额外降低次级文字透明度。
- 600px 以下统一单列，不压缩字号来勉强维持多列。

### 0.4 语义色规则

| Tone | 用途 | 类名 |
| --- | --- | --- |
| Blue | 默认总量、请求量、运行信息 | `mrr-metric-card--blue` 或默认类 |
| Green | 成功、健康、已完成 | `mrr-metric-card--green` |
| Rose / Danger | 异常、失败、风险 | `mrr-metric-card--rose` / `--danger` |
| Amber | 待处理、时间范围、警告 | `mrr-metric-card--amber` |
| Violet | 页数、性能分位、容量指标 | `mrr-metric-card--violet` |
| Teal | 用户、线程、并发等运行指标 | `mrr-metric-card--teal` |
| Slate | 中性耗时、辅助统计 | `mrr-metric-card--slate` |

颜色只表达语义，不按页面随机分配。正文数值保持 `--text-primary`，语义色主要用于顶部色条和图标，避免大面积高饱和文字降低可读性。

### 0.5 实现方式

全局实现位于 `src/assets/styles/globals.css`，页面只组合以下类：

```vue
<section class="mrr-metric-grid">
  <el-card shadow="never" class="mrr-metric-card mrr-metric-card--green">
    <div class="mrr-metric-card__icon">...</div>
    <div class="mrr-metric-card__body">
      <span class="mrr-metric-card__label">已完成</span>
      <strong class="mrr-metric-card__value">1,280</strong>
      <p class="mrr-metric-card__note">已完成归档的病案数量</p>
    </div>
  </el-card>
</section>
```

禁止在业务页面复制 `.stat-card`、`.summary-card`、`.metric-card` 等同类局部样式。出现新需求时应优先扩展 `mrr-metric-card` 的修饰类，并同步更新本规范。

### 0.6 共享布局与容器

- `MrrPageShell` 使用可收缩的单列网格，页面内容不以自身最小宽度撑开工作区。
- 页头、非紧凑筛选栏、分区卡和指标卡的外层统一使用 `--mrr-radius-card`；紧凑筛选栏保留控件级圆角。
- 页头标题固定为 28px，760px 以下为 24px；英文标签与标题间距为 8px（plain 模式为 6px）。
- `MrrSectionCard` 的长标题、描述和操作区允许换行；计数只表达数量，不附带状态点。
- Element Plus 分页在窄屏将总数/每页条数、翻页导航、跳页分别排布；`MrrDataTablePanel` 与既有页面共用此规则，表格仍在自身滚动区域内横向滚动。
- 既有 `.page-shell` 同样使用可收缩的单列网格；窄屏行内表单约束标签后的控件宽度，日期范围和数字输入不得越过所在内容区。
- 设置页的通用控件网格按内容区域自动排列，最小列宽 240px；不要以整个窗口宽度代替扣除两级侧栏后的可用宽度。
- 配色编辑行折行隐藏列头时，必须显示字段标签；操作按钮允许换行，不能通过裁切或隐藏解决宽度不足。
- 使用 `gap` 的显式操作组不叠加相邻按钮左边距；游标分页保留前后翻批与当前批次的完整导航分组。
- 单行输入框、选择器、日期范围和数字输入统一使用 `--mrr-input-height`（33px），包括 small / default / large 和筛选栏；登录、注册表单的单行输入框与滑块轨道使用局部 `--auth-input-height`（38px）。多行文本与多选标签保留按内容展开的高度。
- 操作按钮与输入框统一为 33px 高：`--mrr-control-height` 及 compact / large 均引用输入高度，Element Plus 和 FaButton 的普通、文字、大小号、图标按钮复用该尺寸；图标按钮保持 33px 正方形。
- 侧栏、账号菜单和首页模块入口统一使用本地 Lucide 图标；路由加载仅显示顶部 2px 细进度条，等待 200ms 后再显示，快速完成不闪现，不显示转圈动画。
- 品牌 Logo 与侧栏控制图标为 17px，桌面单侧栏提供 33px 的 Logo 按钮：悬停或键盘聚焦显示收起图标，收起后常显展开图标；标题链接继续返回首页，其他导航模式保留原品牌链接。
- 卡片式页头与摘要指标卡共享 `--mrr-summary-min-height`（110px）；长说明、附加内容和窄屏允许按内容增高，避免固定高度裁切。
- 窄屏双月日期范围弹层上下排布并限制视口内尺寸；范围入口共用 `ui/provider/picker-options.ts` 的 Popper 纵向避让配置，嵌套时间面板向输入右侧对齐，保留结束时间及确认操作。
- 工作区实色背景与装饰渐变分开声明，Chrome 86 丢弃渐变后仍保留明暗主题表面。
- 按钮的键盘焦点使用独立的 2px 主色轮廓（危险操作使用危险色），避免被按钮变体的阴影覆盖。

## 1. Visual Theme & Atmosphere

Notion's website embodies the philosophy of the tool itself: a blank canvas that gets out of your way. The design system is built on warm neutrals rather than cold grays, creating a distinctly approachable minimalism that feels like quality paper rather than sterile glass. The page canvas is pure white (`#ffffff`) but the text isn't pure black -- it's a warm near-black (`rgba(0,0,0,0.95)`) that softens the reading experience imperceptibly. The warm gray scale (`#f6f5f4`, `#31302e`, `#615d59`, `#a39e98`) carries subtle yellow-brown undertones, giving the interface a tactile, almost analog warmth.

The custom NotionInter font (a modified Inter) is the backbone of the system. At display sizes (64px), it uses aggressive negative letter-spacing (-2.125px), creating headlines that feel compressed and precise. The weight range is broader than typical systems: 400 for body, 500 for UI elements, 600 for semi-bold labels, and 700 for display headings. OpenType features `"lnum"` (lining numerals) and `"locl"` (localized forms) are enabled on larger text, adding typographic sophistication that rewards close reading.

What makes Notion's visual language distinctive is its border philosophy. Rather than heavy borders or shadows, Notion uses ultra-thin `1px solid rgba(0,0,0,0.1)` borders -- borders that exist as whispers, barely perceptible division lines that create structure without weight. The shadow system is equally restrained: multi-layer stacks with cumulative opacity never exceeding 0.05, creating depth that's felt rather than seen.

**Key Characteristics:**
- NotionInter (modified Inter) with negative letter-spacing at display sizes (-2.125px at 64px)
- Warm neutral palette: grays carry yellow-brown undertones (`#f6f5f4` warm white, `#31302e` warm dark)
- Near-black text via `rgba(0,0,0,0.95)` -- not pure black, creating micro-warmth
- Ultra-thin borders: `1px solid rgba(0,0,0,0.1)` throughout -- whisper-weight division
- Multi-layer shadow stacks with sub-0.05 opacity for barely-there depth
- Notion Blue (`#0075de`) as the singular accent color for CTAs and interactive elements
- Pill badges (9999px radius) with tinted blue backgrounds for status indicators
- 8px base spacing unit with an organic, non-rigid scale

## 2. Color Palette & Roles

### Primary
- **Notion Black** (`rgba(0,0,0,0.95)` / `#000000f2`): Primary text, headings, body copy. The 95% opacity softens pure black without sacrificing readability.
- **Pure White** (`#ffffff`): Page background, card surfaces, button text on blue.
- **Notion Blue** (`#0075de`): Primary CTA, link color, interactive accent -- the only saturated color in the core UI chrome.

### Brand Secondary
- **Deep Navy** (`#213183`): Secondary brand color, used sparingly for emphasis and dark feature sections.
- **Active Blue** (`#005bab`): Button active/pressed state -- darker variant of Notion Blue.

### Warm Neutral Scale
- **Warm White** (`#f6f5f4`): Background surface tint, section alternation, subtle card fill. The yellow undertone is key.
- **Warm Dark** (`#31302e`): Dark surface background, dark section text. Warmer than standard grays.
- **Warm Gray 500** (`#615d59`): Secondary text, descriptions, muted labels.
- **Warm Gray 300** (`#a39e98`): Placeholder text, disabled states, caption text.

### Semantic Accent Colors
- **Teal** (`#2a9d99`): Success states, positive indicators.
- **Green** (`#1aae39`): Confirmation, completion badges.
- **Orange** (`#dd5b00`): Warning states, attention indicators.
- **Pink** (`#ff64c8`): Decorative accent, feature highlights.
- **Purple** (`#391c57`): Premium features, deep accents.
- **Brown** (`#523410`): Earthy accent, warm feature sections.

### Interactive
- **Link Blue** (`#0075de`): Primary link color with underline-on-hover.
- **Link Light Blue** (`#62aef0`): Lighter link variant for dark backgrounds.
- **Focus Blue** (`#097fe8`): Focus ring on interactive elements.
- **Badge Blue Bg** (`#f2f9ff`): Pill badge background, tinted blue surface.
- **Badge Blue Text** (`#097fe8`): Pill badge text, darker blue for readability.

### Shadows & Depth
- **Card Shadow** (`rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`): Multi-layer card elevation.
- **Deep Shadow** (`rgba(0,0,0,0.01) 0px 1px 3px, rgba(0,0,0,0.02) 0px 3px 7px, rgba(0,0,0,0.02) 0px 7px 15px, rgba(0,0,0,0.04) 0px 14px 28px, rgba(0,0,0,0.05) 0px 23px 52px`): Five-layer deep elevation for modals and featured content.
- **Whisper Border** (`1px solid rgba(0,0,0,0.1)`): Standard division border -- cards, dividers, sections.

## 3. Typography Rules

### Font Family
- **Primary**: `NotionInter`, with fallbacks: `Inter, -apple-system, system-ui, Segoe UI, Helvetica, Apple Color Emoji, Arial, Segoe UI Emoji, Segoe UI Symbol`
- **OpenType Features**: `"lnum"` (lining numerals) and `"locl"` (localized forms) enabled on display and heading text.

### Hierarchy

| Role | Font | Size | Weight | Line Height | Letter Spacing | Notes |
|------|------|------|--------|-------------|----------------|-------|
| Display Hero | NotionInter | 64px (4.00rem) | 700 | 1.00 (tight) | -2.125px | Maximum compression, billboard headlines |
| Display Secondary | NotionInter | 54px (3.38rem) | 700 | 1.04 (tight) | -1.875px | Secondary hero, feature headlines |
| Section Heading | NotionInter | 48px (3.00rem) | 700 | 1.00 (tight) | -1.5px | Feature section titles, with `"lnum"` |
| Sub-heading Large | NotionInter | 40px (2.50rem) | 700 | 1.50 | normal | Card headings, feature sub-sections |
| Sub-heading | NotionInter | 26px (1.63rem) | 700 | 1.23 (tight) | -0.625px | Section sub-titles, content headers |
| Card Title | NotionInter | 22px (1.38rem) | 700 | 1.27 (tight) | -0.25px | Feature cards, list titles |
| Body Large | NotionInter | 20px (1.25rem) | 600 | 1.40 | -0.125px | Introductions, feature descriptions |
| Body | NotionInter | 16px (1.00rem) | 400 | 1.50 | normal | Standard reading text |
| Body Medium | NotionInter | 16px (1.00rem) | 500 | 1.50 | normal | Navigation, emphasized UI text |
| Body Semibold | NotionInter | 16px (1.00rem) | 600 | 1.50 | normal | Strong labels, active states |
| Body Bold | NotionInter | 16px (1.00rem) | 700 | 1.50 | normal | Headlines at body size |
| Nav / Button | NotionInter | 15px (0.94rem) | 600 | 1.33 | normal | Navigation links, button text |
| Caption | NotionInter | 14px (0.88rem) | 500 | 1.43 | normal | Metadata, secondary labels |
| Caption Light | NotionInter | 14px (0.88rem) | 400 | 1.43 | normal | Body captions, descriptions |
| Badge | NotionInter | 12px (0.75rem) | 600 | 1.33 | 0.125px | Pill badges, tags, status labels |
| Micro Label | NotionInter | 12px (0.75rem) | 400 | 1.33 | 0.125px | Small metadata, timestamps |

### Principles
- **Compression at scale**: NotionInter at display sizes uses -2.125px letter-spacing at 64px, progressively relaxing to -0.625px at 26px and normal at 16px. The compression creates density at headlines while maintaining readability at body sizes.
- **Four-weight system**: 400 (body/reading), 500 (UI/interactive), 600 (emphasis/navigation), 700 (headings/display). The broader weight range compared to most systems allows nuanced hierarchy.
- **Warm scaling**: Line height tightens as size increases -- 1.50 at body (16px), 1.23-1.27 at sub-headings, 1.00-1.04 at display. This creates denser, more impactful headlines.
- **Badge micro-tracking**: The 12px badge text uses positive letter-spacing (0.125px) -- the only positive tracking in the system, creating wider, more legible small text.

## 4. Component Stylings

### Buttons

**Primary Blue**
- Background: `#0075de` (Notion Blue)
- Text: `#ffffff`
- Padding: 8px 16px
- Radius: 4px (subtle)
- Border: `1px solid transparent`
- Hover: background darkens to `#005bab`
- Active: scale(0.9) transform
- Focus: `2px solid` focus outline, `var(--shadow-level-200)` shadow
- Use: Primary CTA ("Get Notion free", "Try it")

**Secondary / Tertiary**
- Background: `rgba(0,0,0,0.05)` (translucent warm gray)
- Text: `#000000` (near-black)
- Padding: 8px 16px
- Radius: 4px
- Hover: text color shifts, scale(1.05)
- Active: scale(0.9) transform
- Use: Secondary actions, form submissions

**Ghost / Link Button**
- Background: transparent
- Text: `rgba(0,0,0,0.95)`
- Decoration: underline on hover
- Use: Tertiary actions, inline links

**Pill Badge Button**
- Background: `#f2f9ff` (tinted blue)
- Text: `#097fe8`
- Padding: 4px 8px
- Radius: 9999px (full pill)
- Font: 12px weight 600
- Use: Status badges, feature labels, "New" tags

### Cards & Containers

> 单一核心指标必须使用上文的 MRR Metric Card；本节仅描述普通内容容器。
- Background: `#ffffff`
- Border: `1px solid rgba(0,0,0,0.1)` (whisper border)
- Radius: 12px (standard cards), 16px (featured/hero cards)
- Shadow: `rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`
- Hover: subtle shadow intensification
- Image cards: 12px top radius, image fills top half

### Inputs & Forms
- Background: `#ffffff`
- Text: `rgba(0,0,0,0.9)`
- Border: `1px solid #dddddd`
- Padding: 6px
- Radius: 4px
- Focus: blue outline ring
- Placeholder: warm gray `#a39e98`

### Navigation
- Clean horizontal nav on white, not sticky
- Brand logo left-aligned (33x34px icon + wordmark)
- Links: NotionInter 15px weight 500-600, near-black text
- Hover: color shift to `var(--color-link-primary-text-hover)`
- CTA: blue pill button ("Get Notion free") right-aligned
- Mobile: hamburger menu collapse
- Product dropdowns with multi-level categorized menus

### Image Treatment
- Product screenshots with `1px solid rgba(0,0,0,0.1)` border
- Top-rounded images: `12px 12px 0px 0px` radius
- Dashboard/workspace preview screenshots dominate feature sections
- Warm gradient backgrounds behind hero illustrations (decorative character illustrations)

### Distinctive Components

**Feature Cards with Illustrations**
- Large illustrative headers (The Great Wave, product UI screenshots)
- 12px radius card with whisper border
- Title at 22px weight 700, description at 16px weight 400
- Warm white (`#f6f5f4`) background variant for alternating sections

**Trust Bar / Logo Grid**
- Company logos (trusted teams section) in their brand colors
- Horizontal scroll or grid layout with team counts
- Metric display: large number + description pattern

**Metric Cards**
- Large number display (e.g., "$4,200 ROI")
- NotionInter 40px+ weight 700 for the metric
- Description below in warm gray body text
- Whisper-bordered card container

## 5. Layout Principles

### Spacing System
- Base unit: 8px
- Scale: 2px, 3px, 4px, 5px, 6px, 7px, 8px, 11px, 12px, 14px, 16px, 24px, 32px
- Non-rigid organic scale with fractional values (5.6px, 6.4px) for micro-adjustments

### Grid & Container
- Max content width: approximately 1200px
- Hero: centered single-column with generous top padding (80-120px)
- Feature sections: 2-3 column grids for cards
- Full-width warm white (`#f6f5f4`) section backgrounds for alternation
- Code/dashboard screenshots as contained with whisper border

### Whitespace Philosophy
- **Generous vertical rhythm**: 64-120px between major sections. Notion lets content breathe with vast vertical padding.
- **Warm alternation**: White sections alternate with warm white (`#f6f5f4`) sections, creating gentle visual rhythm without harsh color breaks.
- **Content-first density**: Body text blocks are compact (line-height 1.50) but surrounded by ample margin, creating islands of readable content in a sea of white space.

### Border Radius Scale

圆角只有一个来源：`--radius`（系统设置 → 应用配置 → 全局界面圆角，默认 `0.75rem` = 12px）。
下表为默认基准下的实际取值，**不要在组件里写字面量**。

| 令牌 | 默认值 | 用途 |
|------|--------|------|
| `--mrr-radius-sm` | 6px | 标签、微型按钮、代码块 |
| `--mrr-radius-md` | 8px | 控件、菜单项、块级内层 |
| `--mrr-radius-lg` | 10px | 小卡片、内嵌面板、头像容器 |
| `--mrr-radius-card` | 12px | **页面级表面：卡片、页头、分区、设置分组** |
| `--mrr-radius-xl` | 14px | 卡片内的二级分组面板（见下方分层约定） |
| `--mrr-radius-dialog` | 12px | 弹窗、确认框，与全局圆角基准一致 |
| `--mrr-radius-2xl` | 18px | 全页容器、登录外壳 |
| `--mrr-radius-pill` | 999px | 徽标、胶囊 |

**分层约定（有意为之，非不一致）**：外层表面固定 `--mrr-radius-card`（12px），
卡片**内部**的二级分组面板用 `--mrr-radius-xl`（14px）。两者相差 2px，形成「外紧内松」的
套嵌暗示；若内外同值，嵌套层级会读不出来。允许使用 `--mrr-radius-xl` 的仅限：
`settings/*` 的 `.setting-group` / `.switch-list` / `.trusted-sources-card`、
`MrrSectionCard` 内层、首页 widget 内层、`statistics-detail` 档案袋内层。
页面级卡片一律不得使用 `--mrr-radius-xl`。

**保留字面量的例外**：≤6px 的微型形状（热力图格子 2px、进度条 3px）与圆形（`50%` / `100%`）。

## 6. Depth & Elevation

| Level | Treatment | Use |
|-------|-----------|-----|
| Flat (Level 0) | No shadow, no border | Page background, text blocks |
| Whisper (Level 1) | `1px solid rgba(0,0,0,0.1)` | Standard borders, card outlines, dividers |
| Soft Card (Level 2) | 4-layer shadow stack (max opacity 0.04) | Content cards, feature blocks |
| Deep Card (Level 3) | 5-layer shadow stack (max opacity 0.05, 52px blur) | Modals, featured panels, hero elements |
| Focus (Accessibility) | `2px solid var(--focus-color)` outline | Keyboard focus on all interactive elements |

**Shadow Philosophy**: Notion's shadow system uses multiple layers with extremely low individual opacity (0.01 to 0.05) that accumulate into soft, natural-looking elevation. The 4-layer card shadow spans from 1.04px to 18px blur, creating a gradient of depth rather than a single hard shadow. The 5-layer deep shadow extends to 52px blur at 0.05 opacity, producing ambient occlusion that feels like natural light rather than computer-generated depth. This layered approach makes elements feel embedded in the page rather than floating above it.

### Decorative Depth
- Hero section: decorative character illustrations (playful, hand-drawn style)
- Section alternation: white to warm white (`#f6f5f4`) background shifts
- No hard section borders -- separation comes from background color changes and spacing

## 7. Responsive Behavior

### Breakpoints
| Name | Width | Key Changes |
|------|-------|-------------|
| Mobile Small | <400px | Tight single column, minimal padding |
| Mobile | 400-600px | Standard mobile, stacked layout |
| Tablet Small | 600-768px | 2-column grids begin |
| Tablet | 768-1080px | Full card grids, expanded padding |
| Desktop Small | 1080-1200px | Standard desktop layout |
| Desktop | 1200-1440px | Full layout, maximum content width |
| Large Desktop | >1440px | Centered, generous margins |

### Touch Targets
- Buttons use comfortable padding (8px-16px vertical)
- Navigation links at 15px with adequate spacing
- Pill badges have 8px horizontal padding for tap targets
- Mobile menu toggle uses standard hamburger button

### Collapsing Strategy
- Hero: 64px display -> scales to 40px -> 26px on mobile, maintains proportional letter-spacing
- Navigation: horizontal links + blue CTA -> hamburger menu
- Feature cards: 3-column -> 2-column -> single column stacked
- Product screenshots: maintain aspect ratio with responsive images
- Trust bar logos: grid -> horizontal scroll on mobile
- Footer: multi-column -> stacked single column
- Section spacing: 80px+ -> 48px on mobile

### Image Behavior
- Workspace screenshots maintain whisper border at all sizes
- Hero illustrations scale proportionally
- Product screenshots use responsive images with consistent border radius
- Full-width warm white sections maintain edge-to-edge treatment

## 8. Accessibility & States

### Focus System
- All interactive elements receive visible focus indicators
- Focus outline: `2px solid` with focus color + shadow level 200
- Tab navigation supported throughout all interactive components
- High contrast text: near-black on white exceeds WCAG AAA (>14:1 ratio)

### Interactive States
- **Default**: Standard appearance with whisper borders
- **Hover**: Color shift on text, scale(1.05) on buttons, underline on links
- **Active/Pressed**: scale(0.9) transform, darker background variant
- **Focus**: Blue outline ring with shadow reinforcement
- **Disabled**: Warm gray (`#a39e98`) text, reduced opacity

### Color Contrast
- Primary text (rgba(0,0,0,0.95)) on white: ~18:1 ratio
- Secondary text (#615d59) on white: ~5.5:1 ratio (WCAG AA)
- Blue CTA (#0075de) on white: ~4.6:1 ratio (WCAG AA for large text)
- Badge text (#097fe8) on badge bg (#f2f9ff): ~4.5:1 ratio (WCAG AA for large text)

## 9. Agent Prompt Guide

### Quick Color Reference
- Primary CTA: Notion Blue (`#0075de`)
- Background: Pure White (`#ffffff`)
- Alt Background: Warm White (`#f6f5f4`)
- Heading text: Near-Black (`rgba(0,0,0,0.95)`)
- Body text: Near-Black (`rgba(0,0,0,0.95)`)
- Secondary text: Warm Gray 500 (`#615d59`)
- Muted text: Warm Gray 300 (`#a39e98`)
- Border: `1px solid rgba(0,0,0,0.1)`
- Link: Notion Blue (`#0075de`)
- Focus ring: Focus Blue (`#097fe8`)

### Example Component Prompts
- "Create a hero section on white background. Headline at 64px NotionInter weight 700, line-height 1.00, letter-spacing -2.125px, color rgba(0,0,0,0.95). Subtitle at 20px weight 600, line-height 1.40, color #615d59. Blue CTA button (#0075de, 4px radius, 8px 16px padding, white text) and ghost button (transparent bg, near-black text, underline on hover)."
- "Design a card: white background, 1px solid rgba(0,0,0,0.1) border, 12px radius. Use shadow stack: rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.85px, rgba(0,0,0,0.02) 0px 0.8px 2.93px, rgba(0,0,0,0.01) 0px 0.175px 1.04px. Title at 22px NotionInter weight 700, letter-spacing -0.25px. Body at 16px weight 400, color #615d59."
- "Build a pill badge: #f2f9ff background, #097fe8 text, 9999px radius, 4px 8px padding, 12px NotionInter weight 600, letter-spacing 0.125px."
- "Create navigation: white header. NotionInter 15px weight 600 for links, near-black text. Blue pill CTA 'Get Notion free' right-aligned (#0075de bg, white text, 4px radius)."
- "Design an alternating section layout: white sections alternate with warm white (#f6f5f4) sections. Each section has 64-80px vertical padding, max-width 1200px centered. Section heading at 48px weight 700, line-height 1.00, letter-spacing -1.5px."

### Iteration Guide
1. Always use warm neutrals -- Notion's grays have yellow-brown undertones (#f6f5f4, #31302e, #615d59, #a39e98), never blue-gray
2. Letter-spacing scales with font size: -2.125px at 64px, -1.875px at 54px, -0.625px at 26px, normal at 16px
3. Four weights: 400 (read), 500 (interact), 600 (emphasize), 700 (announce)
4. Borders are whispers: 1px solid rgba(0,0,0,0.1) -- never heavier
5. Shadows use 4-5 layers with individual opacity never exceeding 0.05
6. The warm white (#f6f5f4) section background is essential for visual rhythm
7. Pill badges (9999px) for status/tags, 4px radius for buttons and inputs
8. Notion Blue (#0075de) is the only saturated color in core UI -- use it sparingly for CTAs and links

---

## 10. 浏览器兼容与降级边界

目标浏览器基线是 **Chrome 86**。以下现代特性在 86 上会让**整条声明失效**（不是静默忽略某一段）：

| 特性 | 最低版本 | 失效后果 |
|------|----------|----------|
| `color-mix()` | 111 | 整条 `background` / `border` / `color` 声明被丢弃 |
| `@media` / `@container` 区间语法 `(width <= 760px)` | 104 / 105 | 查询恒不匹配，**整块规则静默生效为 0** |
| `:has()` | 105 | 整条规则被丢弃 |
| `:is()` / `:where()` / `:not(a, b)` | 88 | 整条规则被丢弃（列表形态） |
| `@container` | 105 | 整块查询失效 |
| `dvh` / `svh` / `lvh` | 108 | 整条声明失效，需 `vh` 兜底 |

源码门禁：`scripts/verify-chrome86-build.mjs` 会扫描构建产物，源码里出现上述写法直接阻断构建
（Lightning CSS / UnoCSS / Element Plus 的残留仅提示）。`:not(a, b)` 另由 stylelint
`selector-not-notation: simple` 拦住，请改链式 `:not(.a):not(.b)`；区间语法由
`media-feature-range-notation: prefix` 拦住，请改 `max-width` / `min-width`。

> **区间语法是特别容易漏的一类**：它不报错、不影响其它规则，只是让那段 `@media` 从来
> 没匹配过。项目里曾积累 77 处，等于目标浏览器上的折叠布局长期未生效。

### color-mix() 降级策略

**默认接受降级，不逐处补回退。** 当前 `src/` 里 200+ 处 `color-mix()` 绝大多数是色调底、
hover 高亮、渐变光晕、文本微调色——在 Chrome 86 上退化成「无色」或「继承色」，信息不丢失，
视觉只是变弱，属于可接受的装饰性损失。

但下面四类**不属于装饰**，必须写静态回退，否则功能或可读性真的会坏：

1. **数据编码**：图表色阶、热力图强度、进度条填充、状态点。整档透明等于数据消失。
2. **浮层背景**：popover / tooltip / dropdown / dialog / 自定义弹层的底色。透明后文字直接
   压在页面内容上。
3. **承载分层或可读性的实色底**：尤其是 `background: <gradient>, var(--solid)` 这种简写——
   渐变里夹了 `color-mix` 会让**实色一起丢**。必须把实色拆成独立的 `background-color`。
4. **唯一视觉信号**：某个状态只靠 `color-mix` 背景区分、没有文字/图标/边框兜底时。

回退写法是**相邻双声明**（前静态、后 `color-mix`，不支持则后者被丢弃）：

```css
.level-2 {
  background: var(--el-color-primary-light-5);
  background: color-mix(in srgb, var(--color-primary) 45%, var(--el-bg-color));
}
```

静态值优先级：Element Plus 的 `--el-color-*-light-3/5/7/8`（由 `applyThemeColor()` 以 hex
同步写入，主色变更时依然正确）> 语义实色令牌（`--mrr-card` / `--mrr-muted` / `--mrr-popover`）
> 字面量（仅限万不得已，且不得是主题色）。

stylelint 的 `declaration-block-no-duplicate-properties` 已允许
`consecutive-duplicates-with-different-values`，双声明不会被判违规。

**已知未覆盖**：`statistics-detail` 档案袋的五套分类色渐变、`page-header` 的渐变网格、
指标卡的光晕背景，在 Chrome 86 上会退化为纯色底（底色仍在，仅失去层次）。这是明确接受的现状，
若要改善，按上面第 3 条把实色拆成 `background-color` 即可，不必逐条复刻混色比例。

---

## 相关文档

- **[工程规范](ENGINEERING.md)** — Vue 3 编码约定、API 模式、组件规范、状态管理、路由、性能
- **[主题变量](themes/index.ts)** — 完整的 HSL 主题色阶定义
- **[UnoCSS 配置](uno.config.ts)** — 原子化 CSS 预设与自定义规则
