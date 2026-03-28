# 前端 Figma 规范（PMR）

> 版本：`v1.0`  
> 生效日期：`2026-03-24`  
> 适用范围：PMR Web 管理端（Vue + Element Plus）

## 1. 目标与边界

本规范用于统一 PMR 前端设计语言与研发交付标准，目标是：

- 降低页面风格漂移，保证后台各模块视觉一致。
- 通过 Token 驱动设计与代码同步，减少“设计稿不可还原”。
- 提升交互可预期性，缩短评审与联调周期。

不在本规范覆盖范围内：

- 品牌 Logo、插画与市场物料视觉。
- 小程序与原生 App 的独立设计规范。

## 2. 设计原则

1. 信息优先：先保证可读和层级，再考虑装饰效果。  
2. 操作明确：主操作突出，危险操作需二次确认。  
3. 系统一致：同类组件在尺寸、状态、反馈上保持一致。  
4. 轻量质感：使用浅色基底、蓝系强调、适度毛玻璃与阴影。  
5. 可实现性：设计方案必须可由现有技术栈稳定落地。

## 3. Figma 文件结构

建议统一以下 Page 结构：

- `00_Cover`：文档说明、版本与维护人。
- `01_Foundations`：颜色、字体、间距、圆角、阴影、动效。
- `02_Components`：按钮、输入框、表格、弹窗、卡片等组件集。
- `03_Patterns`：页面级模板（登录、管理页、列表页、详情页）。
- `04_Screens`：业务页面高保真稿。
- `05_Archive`：历史版本与废弃方案。

## 4. Design Tokens（必须变量化）

### 4.1 色彩体系

基础色板（与当前项目风格对齐）：

- `Blue/50 #F2F7FF`
- `Blue/100 #E9F2FF`
- `Blue/200 #DCEAFF`
- `Blue/300 #CBE0FF`
- `Blue/500 #409EFF`
- `Blue/600 #2E81FF`
- `Blue/700 #2467C6`
- `Blue/800 #1F3F75`
- `Neutral/0 #FFFFFF`
- `Neutral/50 #F8FAFC`
- `Neutral/100 #F1F5F9`
- `Neutral/300 #CBD5E1`
- `Neutral/500 #64748B`
- `Neutral/700 #334155`
- `Neutral/900 #0F172A`
- `Success/500 #34C759`
- `Warning/500 #FF9500`
- `Danger/500 #EF4444`

语义变量（优先在页面中使用语义变量）：

- `Color/Bg/Page = Blue/50`
- `Color/Bg/Card = Neutral/0`
- `Color/Text/Primary = Neutral/900`
- `Color/Text/Secondary = Neutral/500`
- `Color/Border/Default = Blue/200`
- `Color/Action/Primary = Blue/600`
- `Color/Action/Danger = Danger/500`

### 4.2 字体与排版

字体家族：

- 中文：`PingFang SC`
- 英文与数字：`SF Pro Display`
- 回退：`Helvetica Neue`, `Arial`, `sans-serif`

字号与行高：

- `Display/L: 32 / 40 / 600`
- `Heading/L: 24 / 32 / 600`
- `Heading/M: 20 / 28 / 600`
- `Title: 18 / 26 / 600`
- `Body/M: 14 / 22 / 400`
- `Body/S: 13 / 20 / 400`
- `Caption: 12 / 18 / 400`

### 4.3 间距与尺寸

采用 `4px` 基准栅格：

- 间距：`4, 8, 12, 16, 20, 24, 32, 40, 48`
- 控件高度：`28, 32, 36, 40`
- 图标尺寸：`16, 18, 20, 24`

### 4.4 圆角与阴影

- 圆角：`6, 8, 10, 12, 14, 18`
- 阴影：
  - `Shadow/Sm: 0 1 3 rgba(0,0,0,0.10)`
  - `Shadow/Md: 0 8 24 rgba(27,72,145,0.08)`
  - `Shadow/Lg: 0 16 40 rgba(15,23,42,0.18)`

### 4.5 动效

- `Duration/Fast: 150ms`
- `Duration/Normal: 220ms`
- `Duration/Slow: 300ms`
- `Easing/Standard: cubic-bezier(0.4, 0, 0.2, 1)`

## 5. 栅格与响应式

断点定义：

- `Desktop`: `>= 1280`
- `Laptop`: `1024 ~ 1279`
- `Tablet`: `768 ~ 1023`
- `Mobile`: `< 768`

栅格建议：

- Desktop：`12 列`，外边距 `24`，槽宽 `24`
- Tablet：`8 列`，外边距 `16`，槽宽 `16`
- Mobile：`4 列`，外边距 `12`，槽宽 `12`

## 6. 组件规范（核心）

### 6.1 Button

- 变体：`Primary / Secondary / Ghost / Danger`
- 尺寸：`S(28) / M(32) / L(36)`
- 状态：`Default / Hover / Active / Disabled / Loading`
- 规则：同一视图最多 1 个主按钮，危险按钮需靠右并二次确认。

### 6.2 Input / Select

- 输入框高度统一 `32` 或 `36`，同区域不可混用。
- 标签文字使用 `Body/S`，输入值使用 `Body/M`。
- 错误态必须包含：边框色变化 + 帮助文本。

### 6.3 Card

- 默认圆角 `12~14`，背景白色，边框 `Color/Border/Default`。
- 卡片内间距不小于 `16`，推荐 `20` 或 `24`。

### 6.4 Modal（含退出登录弹窗）

- 标准弹窗宽度：`400~480`
- 头部、内容、操作区间距：`16 / 16 / 16`
- 危险确认场景必须：
  - 标题明确动作结果（如“退出登录？”）
  - 主次按钮文案明确（如“继续使用 / 退出登录”）
  - 禁止点击遮罩直接关闭

macOS 风格弹窗（用于高风险确认）：

- 遮罩：半透明 + 背景模糊
- 容器：高圆角 + 玻璃质感背景 + 内高光
- 顶部可带三色窗口点作为视觉锚点

### 6.5 Table

- 表头背景使用浅色分层，正文斑马纹可选。
- 行高建议 `44~52`，保证可读性和点击区域。
- 操作列按钮不超过 3 个，更多动作收纳到下拉菜单。

## 7. 可访问性与可用性

- 正文文本对比度不低于 `4.5:1`。
- 可点击区域最小尺寸：`32x32`（桌面）/ `40x40`（触屏）。
- 不仅靠颜色表达状态，需配合图标或文字。
- 键盘可达：关键流程需可通过 `Tab` 完成。

## 8. 组件命名与变体规则

组件命名规范：

- `CMP/Button`
- `CMP/Input`
- `CMP/Modal`
- `PAT/AdminHeader`
- `PAT/StatsCard`

Variant 维度建议：

- `Type = Primary | Secondary | Danger`
- `Size = S | M | L`
- `State = Default | Hover | Active | Disabled | Loading`

图层命名：

- 结构层：`container / header / body / footer`
- 文本层：`title / subtitle / helper / value`
- 禁止使用 `矩形 1`、`文字 3` 这类无语义名称。

## 9. 设计到开发交付规范

每个页面交付时必须包含：

- 页面链接 + Frame 名称 + 适配断点说明
- 组件复用说明（是否来自组件库）
- Token 使用说明（颜色/间距/圆角/阴影）
- 交互状态说明（Hover/Disabled/Error/Loading）

推荐在 PR 中附上：

- Figma Frame 链接
- 关键改动前后截图
- 设计还原验收点（3~5 条）

## 10. 评审清单（Design Review Checklist）

1. 是否全部使用语义 Token，而不是临时色值。  
2. 同级页面的间距、标题层级、按钮尺寸是否一致。  
3. 危险操作是否具备确认弹窗和明确文案。  
4. 空状态、错误态、加载态是否齐全。  
5. 响应式断点下是否出现布局断裂或文本溢出。  
6. 是否能被现有前端组件体系直接实现。  

## 11. 维护机制

- 每两周进行一次设计资产整理（弃用组件归档到 `05_Archive`）。
- Token 改动需发起设计评审，并同步前端变量。
- 规范版本按 `major.minor` 管理：
  - `major`：视觉体系或组件结构重大变化
  - `minor`：新增组件、补充状态、文案规范优化

## 12. 一键映射产物

本规范对应的设计与前端变量文件：

- Figma Variables JSON：`docs/reference/figma-variables.json`
- 前端 Token 文件：`src/assets/css/tokens.css`
- 对照说明：`docs/reference/design-tokens.md`

执行顺序建议：

1. 设计侧先更新 `figma-variables.json`。
2. 前端同步更新 `tokens.css`（变量名不变，只改值）。
3. 执行 `npm run docs:build` 与页面回归，确认没有变量断链。
