# ADR-0007：前端引入 Motion（motion-v）动画库作为局部增强层

- 状态：Proposed
- 日期：2026-10-25
- 决策者：待评审（MRR Team）
- 关联 Issue：待创建（评估提案，建议挂一个 "动画体系演进" 跟踪 Issue）
- 关联 PR：待创建
- 替代 ADR：无

## 背景

前端动画当前由三套已落地、相互配合的机制构成，且已具备无障碍降级：

1. 自研 CSS 动画体系：`src/assets/styles/motion.css` 定义 `mrr-icon-interactive` / `mrr-status-pop` / `mrr-content-enter` / `mrr-fade-enter` 等命名动画与缓动变量，并在 `@media (prefers-reduced-motion: reduce)` 下整体关闭；
2. UnoCSS 动画预设：`unocss-preset-animations` 已在 `uno.config.ts` 通过 `presetAnimations()` 启用，以 `animate-*` 原子类覆盖约 70+ 文件；
3. Vue 3 内置 `<Transition>` / `<TransitionGroup>`：用于页面切换、侧边栏、Tab、下拉、对话框、Tooltip、抽屉等进出场过渡（30+ 处）。

此外还有针对性动画：nprogress（路由进度条，经 `@vueuse/integrations` 的 `useNProgress`）、ECharts（图表入场/更新）、Element Plus 与 reka-ui 组件库内置过渡。

约束：

- **Chrome 86 兼容红线**：`frontend-fantastic-admin/package.json` 的 `browserslist` 声明 `Chrome >= 86`，且前端关键流程必须兼容 Chrome 86；
- **治理红线**：`AGENTS.md` 第 6 章规定，引入新基础设施或改变依赖方向必须先写 ADR；禁止未论证就建立第二套机制；
- **死依赖前车之鉴**：`package.json` 已声明 `gsap: ^3.14.2`，但全仓无任何 import/调用，属未落地依赖，说明动画库引入需谨慎。

触发点：社区提出使用 `motiondivision/motion`（原 Framer Motion，Vue 包名 `motion-v`）优化动画效果。Motion 是面向 React / JavaScript / Vue 的现代动画库，采用 hybrid engine：默认走 Web Animations API（WAAPI），老浏览器回退 `requestAnimationFrame`，并声称对老浏览器有 safe fallback。

需要长期决策的问题：是否引入、引入范围、如何保证 Chrome 86 下不出现静默降级或可用性问题。

## 决策

**不全面替换现有动画体系**，保留 `motion.css + UnoCSS presetAnimations + Vue <Transition>` 作为通用动画基线；**以受控方式引入 `motion-v` 作为增量增强层**，仅覆盖现有 CSS 体系实现成本高、且能带来明确体验增益的场景。

引入前置条件（任一不满足则维持现状）：

1. **ADR 评审通过**（本提案从 Proposed 转为 Accepted）；
2. **完成 Chrome 86 兼容性试点验证**：在一个非关键交互上引入 `motion-v`，实测其在 Chrome 86 下的基础动画、spring、`linear()` 自定义缓动、手势/拖拽与 scroll-linked 表现，确认无"瞬间完成"等静默降级；
3. **清理未使用的 `gsap` 依赖**，避免两个死/半死动画库并存，保持依赖方向清晰；
4. **保留无障碍降级**：通过 Motion 的 `useReducedMotion` 或等价机制接入现有 `prefers-reduced-motion` 策略。

在 ADR 评审通过且试点达标之前，**不向 `package.json` 添加 `motion-v`**，现有体系继续作为唯一基线。

## 必须保持的不变量

- 前端关键流程兼容 Chrome 86；任何动画在 86 下不得静默失败或"瞬间完成"而无降级提示；
- 不建立第二套"通用"动画机制——Motion 只用于 4.1 列出的增量场景，不替代 `motion.css` / UnoCSS / Vue Transition 的通用用途；
- 引入的 Motion 版本须为 Vue 专用包 `motion-v`，不引入 `framer-motion` / `motion/react`，避免 React 运行时进入依赖图；
- 无障碍降级（`prefers-reduced-motion`）在所有动画路径上必须保持生效；
- 不引入新的运维组件或部署依赖；Windows 内网单机部署边界不变。

## 方案边界

### 包含

- 引入 `motion-v` 作为运行时依赖（hybrid 约 17kb / mini `animate` 约 2.3kb，gzip 口径，以实测为准）；
- 在以下场景试点并使用 Motion：spring 物理动画、layout 布局动画、手势/拖拽交互、scroll-linked 效果；
- 配套接入 `prefers-reduced-motion`；
- 清理 `gsap` 死依赖（与其二选一，不并存）。

### 不包含

- 不重写/替换现有 `motion.css`、UnoCSS 动画预设、Vue `<Transition>` 既有用法；
- 不引入 Motion 的 React 版本或任何 React 运行时；
- 不依赖 Chrome 86 不支持的高级特性（ScrollTimeline 115+、View Transitions 111+、CSS `linear()` easing 113+ 作为硬依赖）；这些特性若使用，必须显式 feature-detect 并提供 86 回退；
- 不改变后端、数据库、部署架构。

## 替代方案

### 方案 A：全面替换现有体系为 Motion

- 优点：动画 API 统一，开发者体验一致。
- 缺点：推翻已验证稳定的 Chrome 86 兼容路径；高级特性在 86 上会降级；包体积增加；收益与成本不匹配。
- 未采用原因：违反"不未论证就建第二套机制"与 Chrome 86 红线，且无充分理由弃用已工作的体系。

### 方案 B：仅用自研 CSS / UnoCSS 实现 spring 与 layout 动画

- 优点：零新增依赖，完全兼容 86。
- 缺点：spring 物理与 layout 共享布局动画在纯 CSS 下实现成本高、性能与一致性差、维护复杂。
- 未采用原因：这正是 Motion 的增量价值所在，纯 CSS 路线性价比低。

### 方案 C：保持现状，不引入任何新动画库

- 优点：零风险、零体积增量、Chrome 86 完全可控。
- 缺点：放弃 spring / layout / 手势等更精细的交互增益。
- 采用条件：若 Chrome 86 试点验证不达标，则落入本方案（见回滚）。

### 方案 D（本提案）：局部增强 + Chrome 86 试点验证前置

- 优点：在可控范围内获得 Motion 增量价值，且以实测证据兜底 86 兼容性。
- 缺点：需要额外的试点与评审成本；两套机制并存需纪律约束范围。
- 结论：推荐采用，但须满足背景中列出的前置条件。

## 影响

### 正面影响

- spring 物理动画、layout 共享布局动画、手势/拖拽、scroll-linked 效果等体验提升，且实现成本显著低于纯 CSS；
- 与现有无障碍策略可结合，不削弱降级能力。

### 负面影响与技术债

- 新增运行时依赖（`motion-v`），增加前端包体积与构建产物；
- Chrome 86 上部分高级特性（见方案边界"不包含"）必然降级，需在试点中量化影响并决定是否启用；
- 两套动画机制并存，需要以本 ADR 的范围约定约束后续使用，防止扩散为事实上的第二套通用体系。

### 安全、隐私与审计

- 无直接影响；Motion 仅做客户端动画，不接触病案/影像/凭据/审计数据。

### 数据库、性能与容量

- 无数据库影响；性能影响限于前端运行时与包体积，需在验证阶段度量。

### Chrome 86、Windows 与部署兼容性

- 关键风险点。已知事实：
  - WAAPI 基础能力在 Chrome 84+ 即被支持，Chrome 86 基础 `animate()` 可走 WAAPI 或 rAF 回退；
  - CSS `linear()` easing（Chrome 113+）、ScrollTimeline（115+）、View Transitions（111+）在 Chrome 86 **不可用**，Motion 会以 JS 回退或静默降级处理；
  - Motion 官方仅承诺"支持所有现代浏览器，不支持 IE11"，**未承诺 Chrome 86**；其 FAQ 明确：浏览器不支持 `CSS.registerProperty` 时 CSS 变量动画会"瞬间完成"（Chrome 86 支持该 API，但仍需在试点中实测确认）；
  - 因此 Chrome 86 下的真实表现必须**实测**，不得仅依据官方"现代浏览器"表述推断。
- Windows 内网单机部署无新组件，部署形态不变。

## 迁移方案

1. 评审通过且试点达标后，于 `frontend-fantastic-admin` 添加 `motion-v` 依赖（锁定与当前 Node 24 / Vite 8 兼容的版本）；
2. 从 `package.json` 移除 `gsap`（确认无引用后）；
3. 选取 1 个非关键交互（如筛选条/卡片的 spring 入场或 layout 重排）作为首个落地点，封装为可复用组件，并接入 `prefers-reduced-motion`；
4. 补充该场景的 Vitest（jsdom）渲染测试与 Playwright Chrome 86 通道 E2E；
5. 在 `ENGINEERING.md` / 前端约定文档中记录"动画选型边界"：通用动画用 CSS/UnoCSS/Vue Transition，增量场景用 Motion。

## 回滚与前向修复

- 代码回滚：移除 `motion-v` 相关组件与依赖，通用动画基线不受影响（天然回滚）；
- 功能开关：首个落地点可加 `settings` / 编译期开关，便于灰度关闭；
- 若试点验证 Chrome 86 不达标：放弃引入，落入方案 C（保持现状），并据此更新本 ADR 状态为 `Rejected`（用于阻止重复讨论）；
- 无数据库结构变更，无已发布 Flyway 迁移，回滚不涉及数据修复。

## 验证

- 单元测试：Motion 封装组件在 jsdom 下的渲染与 `prefers-reduced-motion` 行为；
- Chrome 86 专项（必要）：在 Chrome 86 实测——基础 `animate`、`spring` 物理、自定义缓动（`linear()` 回退路径）、手势/拖拽、scroll-linked 的表现，记录是否出现"瞬间完成"或异常；
- E2E 或手工流程：Playwright 以 Chrome 86 通道运行试点场景，对比 Chrome 最新版行为差异；
- 性能与容量：包体积增量、首屏与交互帧率（内网低配终端抽样）；
- 生产或灰度验证：在受控内网终端（含 Chrome 86）实际演练试点交互。

## 后续事项

- [ ] 创建跟踪 Issue 并关联本 ADR；
- [ ] 完成 Chrome 86 试点验证，形成证据记录（建议放在 `.workbuddy/analysis/` 或 PR 描述）；
- [ ] 清理 `gsap` 死依赖（与引入 Motion 二选一，不并存）；
- [ ] 评审通过将本 ADR 状态更新为 Accepted，并在 `docs/adr/README.md` 的已记录决策表登记 ADR-0007；
- [ ] 若试点不达标，将状态改为 Rejected 并说明落入方案 C 的理由。
