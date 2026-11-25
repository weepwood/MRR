# V1.2 本地 UI 所有权审查

基线 `0161abbd`，2026-11-26。仅本地开发。统计为静态源码，不等于运行实例或缺陷。按钮以 Vue SFC 模板 AST 计数，CSS 沿用 .el-* 文本口径并用 PostCSS 检查规则；scoped、媒体条件及优先级不能仅凭字符串推断冲突。

## 原生按钮逐项清单

AST 71 个按钮、34 个文件。保留不是视觉或无障碍验收。

| 文件及基线行 | 类名 | 决策 |
| --- | --- | --- |
| `src/components/AccountForm/LoginForm.vue:151` | `password-toggle` | 保留：复合密码字段内部操作 |
| `src/components/AccountForm/RegisterFieldLabel.vue:28` | `field-info-trigger` | 待逐项几何与焦点验收 |
| `src/components/AccountForm/RegisterForm.vue:149` | `password-toggle` | 保留：复合密码字段内部操作 |
| `src/components/AccountForm/RegisterForm.vue:165` | `password-toggle` | 保留：复合密码字段内部操作 |
| `src/components/MrrInfoTip/index.vue:28` | `mrr-info-tip` | 待逐项几何与焦点验收 |
| `src/components/MrrStorageBrowser/InlinePreview.vue:90` | `inline-image` | 待逐项几何与焦点验收 |
| `src/components/MrrStorageBrowser/index.vue:332` | `source-info` | 待逐项几何与焦点验收 |
| `src/components/MrrStorageBrowser/index.vue:383` | `crumb-button` | 待专项：导航、分段、复合触发器 |
| `src/components/MrrStorageBrowser/index.vue:420` | `file-tile` | 保留：交互内容单元 |
| `src/components/MrrStorageBrowser/index.vue:448` | `sort-button` | 待专项：导航、分段、复合触发器 |
| `src/components/MrrStorageBrowser/index.vue:464` | `entry-name` | 保留：交互内容单元 |
| `src/components/ScanQueryDiagnostics/index.vue:294` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/components/SystemAdminContactPopover/index.vue:65` | `admin-contact__trigger` | 待逐项几何与焦点验收 |
| `src/components/SystemAdminContactPopover/index.vue:90` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/components/SystemAdminContactPopover/index.vue:103` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/components/charts/MrrRequestHeatmap.vue:334` | `heatmap-cell` | 保留：交互内容单元 |
| `src/layouts/components/Header/index.vue:31` | `mrr-navigation-control menu-item-container relative w-full flex cursor-pointer items-center justify-between gap-1` | 待专项：导航、分段、复合触发器 |
| `src/layouts/components/MainSidebar/index.vue:50` | `mrr-navigation-control group menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1 rounded-lg py-4 text-[var(--g-main-sidebar-menu-color)] transition-colors px-2!` | 待专项：导航、分段、复合触发器 |
| `src/layouts/components/Topbar/Tabbar/index.vue:251` | `action-icon` | 待逐项几何与焦点验收 |
| `src/layouts/components/views/link.vue:31` | `link-copy my-2 max-w-[300px] cursor-pointer text-center text-[14px] text-secondary-foreground/50` | 待逐项几何与焦点验收 |
| `src/layouts/index.vue:127` | `mobile-sidebar-mask` | 待专项：导航、分段、复合触发器 |
| `src/views/archive-boxes/index.vue:606` | `box-number-button` | 本批迁移 ElButton |
| `src/views/data-relations/components/ArchiveScanInspector.vue:208` | `scan-row` | 保留：交互内容单元 |
| `src/views/data-relations/index.vue:606` | `archive-candidate` | 保留：交互内容单元 |
| `src/views/database-browse/index.vue:337` | `directory-item` | 保留：交互内容单元 |
| `src/views/home/DashboardWorkspace.vue:776` | `color-picker-option` | 待专项：导航、分段、复合触发器 |
| `src/views/login.vue:301` | `environment-badge` | 待逐项几何与焦点验收 |
| `src/views/login.vue:367` | `environment-item-info` | 待逐项几何与焦点验收 |
| `src/views/login.vue:391` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/views/login.vue:394` | `is-primary` | 待逐项几何与焦点验收 |
| `src/views/login.vue:406` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/views/login.vue:409` | `（动态/无静态类）` | 待逐项几何与焦点验收 |
| `src/views/password/change-required.vue:106` | `visibility-button` | 保留：复合密码字段内部操作 |
| `src/views/password/change-required.vue:121` | `visibility-button` | 保留：复合密码字段内部操作 |
| `src/views/runtime-errors/index.vue:355` | `metric-action` | 保留：交互内容单元 |
| `src/views/runtime-errors/index.vue:358` | `metric-action` | 保留：交互内容单元 |
| `src/views/runtime-errors/index.vue:362` | `metric-action` | 保留：交互内容单元 |
| `src/views/runtime-errors/index.vue:443` | `module-filter` | 本批迁移 ElButton |
| `src/views/scan-img/index.vue:592` | `thumbs-mode-btn` | 待专项：导航、分段、复合触发器 |
| `src/views/scan-img/index.vue:595` | `thumbs-mode-btn` | 待专项：导航、分段、复合触发器 |
| `src/views/scan-img/index.vue:602` | `type-item` | 待专项：导航、分段、复合触发器 |
| `src/views/scan-img/index.vue:612` | `type-item` | 待专项：导航、分段、复合触发器 |
| `src/views/scan-img/index.vue:734` | `type-option` | 待专项：导航、分段、复合触发器 |
| `src/views/scan-img/index.vue:759` | `retry-btn` | 本批迁移 ElButton |
| `src/views/settings/components/AppConfigPanel.vue:232` | `theme-palette-option` | 待专项：导航、分段、复合触发器 |
| `src/views/settings/components/AppConfigPanel.vue:292` | `theme-swatch` | 待专项：导航、分段、复合触发器 |
| `src/views/settings/components/AppConfigPanel.vue:320` | `theme-palette-accent__reset` | 待专项：导航、分段、复合触发器 |
| `src/views/settings/components/AppConfigPanel.vue:343` | `corner-option` | 待专项：导航、分段、复合触发器 |
| `src/views/settings/components/AppConfigPanel.vue:397` | `mode-option` | 待专项：导航、分段、复合触发器 |
| `src/views/settings/components/UnifiedSettingsPage.vue:419` | `settings-nav-item` | 待逐项几何与焦点验收 |
| `src/views/statistics-detail/ArchiveDetailContent.vue:514` | `folder-select` | 保留：交互内容单元 |
| `src/views/statistics-detail/ArchiveDetailContent.vue:532` | `folder-copy` | 待逐项几何与焦点验收 |
| `src/views/statistics-detail/ArchiveDetailContent.vue:542` | `folder-copy` | 待逐项几何与焦点验收 |
| `src/views/statistics/archive/components/ArchiveCaseList.vue:92` | `case-item` | 保留：交互内容单元 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:271` | `history-main` | 保留：交互内容单元 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:280` | `history-favorite-button` | 待逐项几何与焦点验收 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:306` | `history-filter-button` | 待专项：导航、分段、复合触发器 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:316` | `history-filter-button` | 待专项：导航、分段、复合触发器 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:326` | `history-filter-button` | 待专项：导航、分段、复合触发器 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:341` | `history-main` | 保留：交互内容单元 |
| `src/views/statistics/archive/components/ArchiveHeader.vue:352` | `history-favorite-button` | 待逐项几何与焦点验收 |
| `src/views/statistics/archive/components/ArchiveQueryFailure.vue:102` | `trace-copy` | 待逐项几何与焦点验收 |
| `src/views/statistics/archive/components/ArchiveRecentLookups.vue:136` | `recent-lookups__item` | 保留：交互内容单元 |
| `src/views/statistics/archive/components/ArchiveWall.vue:333` | `archive-wall-check` | 保留：交互内容单元 |
| `src/views/statistics/archive/components/PatientCard.vue:35` | `field-label field-label--switchable` | 待专项：导航、分段、复合触发器 |
| `src/views/statistics/archive/components/TypeFilterBar.vue:70` | `type-bar-toggle` | 待逐项几何与焦点验收 |
| `src/views/statistics/archive/components/TypeFilterBar.vue:76` | `type-tab` | 待专项：导航、分段、复合触发器 |
| `src/views/statistics/archive/components/TypeFilterBar.vue:94` | `type-tab` | 待专项：导航、分段、复合触发器 |
| `src/views/status/index.vue:276` | `refresh-button` | 待逐项几何与焦点验收 |
| `src/views/status/index.vue:368` | `day-cell` | 保留：交互内容单元 |
| `src/views/status/index.vue:430` | `clear-date-filter` | 待逐项几何与焦点验收 |

## Element Plus 桥接外样式所有权

60 个文件、475 处 .el-* 引用、290 条实际规则，PostCSS 解析失败 0。以下分类不意味着全部级联冲突已消除。

| 文件 | 引用数 | 规则数 | 所有权 |
| --- | ---: | ---: | --- |
| `src/assets/styles/components/form-controls.css` | 140 | 71 | 共享语义修饰 |
| `src/assets/styles/themes/warm-flat.css` | 45 | 22 | 主题差异 |
| `src/assets/styles/components/busy-indicator.css` | 25 | 7 | 共享语义修饰 |
| `src/assets/styles/components/corner-shape.css` | 25 | 5 | 共享语义修饰 |
| `src/views/permissions/index.vue` | 18 | 9 | 业务布局/例外 |
| `src/views/settings/components/AppConfigPanel.vue` | 17 | 10 | 业务布局/例外 |
| `src/components/AccountForm/LoginForm.vue` | 10 | 10 | 账户局部几何 |
| `src/views/data-relations/components/ArchiveScanInspector.vue` | 10 | 4 | 业务布局/例外 |
| `src/components/MrrDataTablePanel/index.vue` | 9 | 9 | 组件/壳层内部布局 |
| `src/components/MrrFilterBar/index.vue` | 9 | 6 | 组件/壳层内部布局 |
| `src/components/MrrSectionCard/index.vue` | 9 | 8 | 组件/壳层内部布局 |
| `src/views/statistics/archive/components/ArchiveSearchBar.vue` | 9 | 9 | 业务布局/例外 |
| `src/views/data-exchange/index.vue` | 8 | 6 | 业务布局/例外 |
| `src/views/oss-migration/index.vue` | 8 | 7 | 业务布局/例外 |
| `src/components/AccountForm/RegisterForm.vue` | 7 | 5 | 账户局部几何 |
| `src/components/MrrScanContentDialog/index.vue` | 7 | 5 | 组件/壳层内部布局 |
| `src/views/logs/index.vue` | 7 | 7 | 业务布局/例外 |
| `src/views/settings/components/SettingsSearch.vue` | 7 | 6 | 业务布局/例外 |
| `src/assets/styles/browser-compatibility.css` | 6 | 1 | 全局基础/兼容 |
| `src/assets/styles/components/metric-card.css` | 6 | 6 | 共享语义修饰 |
| `src/components/MrrStorageBrowser/index.vue` | 6 | 3 | 组件/壳层内部布局 |
| `src/views/data-relations/index.vue` | 6 | 5 | 业务布局/例外 |
| `src/views/home/DashboardWorkspace.vue` | 6 | 4 | 业务布局/例外 |
| `src/components/ScanQueryDiagnostics/index.vue` | 5 | 4 | 组件/壳层内部布局 |
| `src/views/ocr-batches/index.vue` | 5 | 4 | 业务布局/例外 |
| `src/assets/styles/components/glass-surface.css` | 4 | 3 | 共享语义修饰 |
| `src/views/patients/components/PatientAnalyticsPanel.vue` | 4 | 4 | 业务布局/例外 |
| `src/views/statistics/archive.vue` | 4 | 4 | 业务布局/例外 |
| `src/views/archive-boxes/index.vue` | 3 | 3 | 业务布局/例外 |
| `src/views/help/index.vue` | 3 | 3 | 业务布局/例外 |
| `src/views/settings/components/unified-settings.css` | 3 | 2 | 业务布局/例外 |
| `src/views/statistics-detail/ArchiveDetailContent.vue` | 3 | 2 | 业务布局/例外 |
| `src/views/statistics/archive/components/ArchiveMoreSettings.vue` | 3 | 3 | 业务布局/例外 |
| `src/assets/styles/archive-legacy-mode.css` | 2 | 2 | 全局基础/兼容 |
| `src/assets/styles/base.css` | 2 | 2 | 全局基础/兼容 |
| `src/components/MrrPageHeader/index.vue` | 2 | 1 | 组件/壳层内部布局 |
| `src/components/MrrStorageBrowser/Preview.vue` | 2 | 2 | 组件/壳层内部布局 |
| `src/views/audit-images/index.vue` | 2 | 2 | 业务布局/例外 |
| `src/views/auth-test/components/OssDiagnosticsTester.vue` | 2 | 1 | 业务布局/例外 |
| `src/views/login.vue` | 2 | 2 | 业务布局/例外 |
| `src/views/operations-center/index.vue` | 2 | 2 | 业务布局/例外 |
| `src/views/settings/components/UnifiedSettingsPage.vue` | 2 | 1 | 业务布局/例外 |
| `src/views/statistics/archive/components/ArchiveQueryFailure.vue` | 2 | 1 | 业务布局/例外 |
| `src/views/statistics/archive/components/TypeFilterBar.vue` | 2 | 2 | 业务布局/例外 |
| `src/assets/styles/components/archive-workspace.css` | 1 | 1 | 共享语义修饰 |
| `src/components/MrrFilterActionButton/index.vue` | 1 | 0 | 组件/壳层内部布局 |
| `src/components/MrrTableActions/index.vue` | 1 | 1 | 组件/壳层内部布局 |
| `src/views/auth-test/index.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/data-relations/ArchiveManifestCard.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/home/HomeArchiveLookup.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/password/change-required.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/response-analysis/index.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/ArchiveSettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/DepartmentThemeSettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/DeveloperSettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/DocumentationSettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/ExternalLinksSettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/settings/components/SecuritySettings.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/statistics/archive/components/PreviewActionBar.vue` | 1 | 1 | 业务布局/例外 |
| `src/views/ui-components/index.vue` | 1 | 1 | 业务布局/例外 |

跨文件相同 selector、条件、属性候选 3 组（scoped 不直接视为冲突）：
- `.password-shell :deep(.el-input__wrapper)` / `padding-right`：`src/components/AccountForm/LoginForm.vue` = `44px`；`src/components/AccountForm/RegisterForm.vue` = `42px`。
- `.slider-control :deep(.el-slider)` / `width`：`src/views/settings/components/ArchiveSettings.vue` = `100%`；`src/views/settings/components/SecuritySettings.vue` = `100%`。
- `.slider-control :deep(.el-slider)` / `min-width`：`src/views/settings/components/ArchiveSettings.vue` = `120px`；`src/views/settings/components/SecuritySettings.vue` = `120px`。

本批合并 ArchiveSettings / SecuritySettings 滑块几何，保留各自数值单位宽度及原窄屏规则；密码字段44/42px局部几何不同，保留。按钮图标尺寸16px、间距6px提升为令牌。基础桥接负责控件尺寸及状态，共享修饰负责语义，主题优先改令牌，局部只负责排版或明确专用几何。

## 剩余 FA 专用组件

- FaIcon：保留资源入口，增加稳定 mrr-icon 标识；业务 Lucide 优先，EP 内建图标保留。
- FaScrollArea：六处导航滚动区域，横向滚轮映射、起末遮罩、滚动事件、公开 viewportElement 与 scrollTo、动效开关是实际合同。ElScrollbar 需做适配及连续交互基线，不直接替换。
- FaBackToTop：已有 ElButton 底层，保留自动隐藏及减弱动效；不是重复基础按钮。
- FaPinInput：仅 ResetPasswordForm 调用；已有 ElInputOtp，未验证验证码粘贴、焦点、完成和实际可达性前保留。
- FaPageMain：仅外链确认页使用，历史折叠与过渡布局；先建立外链视觉基线，再归入 MrrSectionCard 或明确保留专用壳。
- FaKbd、FaCopyright、FaSmartFixedBlock、FaSystemInfo、FaNotAllowed 保留。Reka 仍为滚动、PIN、账户表单辅助依赖。

## 本批业务组件边界

Manifest 两个选择器改 ElSelect，数值版本、空值、已加载异常、请求及游标不变；OCR 原文为权限保护只读 ElInput textarea；导航搜索和追踪编号改 ElInput，保留复合字段壳。数据库授权浏览复用 MrrFilterActionButton 请求反馈，保留页码与估算统计，区分首屏加载、空与错误。展厅新增数值版本选择示例。原生文件输入及内容单元不机械替换。

## 本地验收与风险边界

- P2 公共控件/布局与 P1 病案完整性、只读权限及操作语义；无后端、API、数据库或鉴权变更。原 11 项 WebKit 失败隔离复现 12/12（含预热），扩大验证后定向 WebKit 19/20、Chromium 11/12，唯一剩余版本比较经可用状态及真实键盘展开修复，双引擎 3/3（含预热）。实际剪贴板仅 Chromium；不宣称 Linux 完整矩阵已修复。
- 类型、114 项定向单测、生产构建与 Chrome86 静态门禁通过；尚无本轮所有页面迁移前后像素对照、真实 Chrome86、完整 Linux/Firefox 证据。共用样式及三处按钮可随本提交 revert；后续逐项补齐剩余按钮与专用 FA 几何/交互验收，避免盲目删除。

## 第二批本地公共组件深化（基线 2a10a360）

- 基线清单中继续迁移 refresh-button、clear-date-filter、trace-copy、theme-palette-accent__reset 四处普通命令；内容型 day-cell、主题预设卡片、密码字段专用触发器仍保留。上批原生按钮清单是历史基线而非当前剩余计数。
- 日志、审计、记录、患者四页的业务 Lucide 图标统一 FaIcon 资源入口；已有 EP 原生加载不强行替代。复合字段紧凑按钮由 form-controls.css 的 mrr-embedded-icon-action 持有尺寸，页面仅指定局部 24px / 13px。
- 数据浏览使用 MrrFilterBar grid、MrrDataTablePanel loading / result-key、独立首次加载骨架和共享分页槽；原 cursorStack、activeFilter、权限清理和请求取消逻辑不变。不用总页数伪造游标。
- Chrome86：危险按钮 hover、主/危险 plain 边界与焦点补回退；导航示意第二分区使用主色+透明度，容器基线采用语义底色；影像分类选中态提供基础阴影。已有 token 层及其他 @supports not 回退仍有效，静态产物 warning 总数不是实际未回退缺陷数量。
- 实际浏览器定向26项通过（含预热），1项 WebKit 剪贴板权限按既有策略跳过；Chromium 实际复制通过。116项单测通过。未做所有页面的1px几何基线对比或旧浏览器实机验证，专用FA后续仍需单独交互合同验收。

## 第三批：专用组件交互与展厅深化（基线 e6e89159）

- FaScrollArea 保留 Reka 底层，统一公开 viewport / scrollTo；导航标签和搜索结果不再读取嵌套私有引用。观察实际视口和内容尺寸；事件只交给 viewport，滚轮按方向、单位、边界及修饰键处理。FaBackToTop 保留 ElButton、自动隐藏和减弱动效，焦点期间保持可见，退出清理计时器。
- FaPinInput 保留 Reka 底层，外部预设、部分输入、删除和清空实时同步，保留前导零，补充逐位可访问名称。实际重置密码业务仍未启用；不添加发送验证码或重置接口，不视为真实认证验收。
- FaPageMain：调查现有唯一引用 LinkView，当前常用外链菜单直接打开链接；未找到实际可达确认路由并建立视觉基线，撤回本轮容器替换，保留旧实现。ElScrollbar / ElInputOtp 未完成完整行为对照，不强制迁移。
- 展厅新增专用滚动、PIN、紧凑动作与游标模拟。示例数据仅本地，不写入 API 或真实剪贴板；游标模式表达批次未知总数，非生产后端游标算法。现有业务路由和权限不改。
- 导航链接明确 tabindex=0；视觉与导航用例隔离共享 Mock UI 设置。首轮两项 WebKit Tab 聚焦失败，后续复验通过；两项游标测试观察到 aria-checked=true 且实际批次已切换，但原生 input checked 读值不同，改为校验可访问状态加原有业务结果断言。
