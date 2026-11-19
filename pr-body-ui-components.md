# fix(ui): Chrome 86 组件级回退补全 + 工作台与列表页交互完善

## 变更说明

本 PR 收口 2026-11-19 UI 诊断的两块遗留：补齐 10 个文件在 Chrome 86 上缺失的组件级 color-mix 回退（分层实色底与状态信号必须可辨），并落地实拍复查确认的列表页与工作台交互修正（常量枚举指标卡、全零指标收纳、入口暗示统一、状态标签语汇、页头字号镜像）。共 15 个文件，纯前端改动。

### 一、Chrome 86 组件级回退补全（批 2-6 收尾）

上午诊断确认 MrrStatusTag / MrrTableActions / 档案袋 / 表头底色等组件回退已在 #608/#610 落地，但全仓扫描仍有 10 个文件存在「未包在 @supports 内的 color-mix」且承载**分层实色底 / 状态信号**职能（装饰性阴影与渐变按既定拍板允许降级，本次不动）：

| 文件 | 缺口 |
| --- | --- |
| `views/home/DashboardWorkspace.vue` | 工作台 4 组色相变体的 `--widget-surface/--widget-border`、widget 图标块、置顶徽章底色 |
| `views/help/index.vue` | 文档卡着色底面、悬停描边、分类图标块 |
| `components/AccountForm/RegisterForm.vue` | 审核流程条、图标块、分区底色 |
| `layouts/components/Logo/index.vue` | 开发者模式危险徽标底/描边（唯一危险信号） |
| `views/password/change-required.vue` | 安全标识块警示底/描边 |
| `views/response-analysis/components/ResponseTrendChart.vue` | 小节标题胶囊底/描边 |
| `views/settings/components/DeveloperSettings.vue` | 警示区/危险区边界色（唯一风险提示） |
| `views/settings/components/ExternalLinksSettings.vue` | 头部图标块底色 |
| `views/settings/components/DepartmentThemeSettings.vue` | 文件夹主题预览选中描边 |
| `views/statistics/index.vue` + `views/users/index.vue` | 趋势摘要块/用户头像块分层底 |

回退值统一复用已在 `browser-compatibility.css` 静态定义的 `--mrr-tone-*-soft/-border`，或直接落到语义 accent 静态色。新增 4 条契约断言进 `chrome86-fallback.test.ts`，防止这些回退块在后续重构中被静默删除。

N7（运维中心 8 tab 溢出）经核实：EP el-tabs 自带 `scrollable` 溢出检测与滚动箭头，窄屏不会溢出，**无需改动**。

### 二、列表页与工作台交互完善（N3/N5/N8 + V-12/V-20）

- **N3**：records 页删除「读取方式：游标」常量枚举指标卡（不会变化的词不占真指标位），读取方式说明保留在筛选栏下方提示文案
- **N5**：users / statistics / archive-boxes 三页指标区在**数值全部为零**时整体收纳（`v-if="hasMetricActivity"`），空库/空筛态下为表格与图表让出首屏
- **N8**：首页小尺寸卡此前隐藏「进入模块」footer 导致 6 卡中 2 卡无入口暗示；改为小卡隐藏描述文案、保留暗示，全部卡统一
- **V-12（子集）**：用户表「凭据状态」由裸 `el-tag` 改为 `MrrStatusTag`，消除同一张表两种状态视觉语汇并列；「角色」列属分类语义保留 `el-tag`
- **V-20**：`MrrPageHeader` 内与全局不一致的字号（h1 28px/650、描述 13px、eyebrow 0.06em）镜像对齐全局规范（26px/700、14px、0.16em），修正误导性注释

### 不在本 PR（组件复用治理的剩余部分）

FaPageHeader 全站接入（16 页手写页头）与 el-tag 全量治理属上午诊断批 3-12（L 级、需设计拍板语义边界），另行推进。

## 关联事项

无关联 Issue：源自 2026-11-19 UI 全面诊断（`.workbuddy/analysis/ui-audit-2026-11-19/` 批 2-6 与当晚实拍复查 N3/N5/N8）的落地。

## 风险等级

P2

## 验证证据

- `vue-tsc -b` 通过；eslint / stylelint 对全部 15 个改动文件通过
- vitest 全量 **704/704 通过**（含新增 4 条 86 回退契约断言）
- 生产构建成功 + `verify-chrome86-build.mjs` 通过（无新增违规）
- e2e 依赖核查：`records.spec.ts` 等对「游标」的引用为分页逻辑断言，与本次删除的指标卡无关；`shared-ui-layout.spec.ts` 对 `.mrr-metric-card` 的断言在其他页面（非本次收纳的空态路径）仍成立

## 回滚方案

单提交纯前端回滚：`git revert` 后重新构建即可。各页改动相互独立，必要时可按文件粒度部分回滚。

## AI 参与说明

代码由 AI（WorkBuddy）生成，人工完成需求取舍与验收。
