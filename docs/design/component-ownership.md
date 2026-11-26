# 控件唯一实现与源码使用清单

2026-11-25，用户明确同一种元素只使用同一种控件，替代前期双族长期并存方案。Element Plus 为基础业务控件唯一目标；Fa 专门用途组件、MRR 组合组件保留，内部同类基础控件也必须迁移。当前处于迁移期，不将双族视觉一致性视为最终验收。

## 选型与归属

| 原实现 | 唯一目标 |
| --- | --- |
| FaButton | ElButton |
| FaInput | ElInput |
| FaTextarea | ElInput (textarea) |
| FaSelect | ElSelect |
| FaSwitch | ElSwitch |
| FaCheckbox | ElCheckbox |
| FaSlider | ElSlider |
| FaTabs | ElTabs |
| FaTooltip | ElTooltip |
| FaModal | ElDialog |
| FaDrawer | ElDrawer |
| FaPopover | ElPopover |
| FaProgress | ElProgress |
| FaDropdown | ElDropdown |
| FaButtonGroup | ElButtonGroup |
| FaAvatar | ElAvatar |
| FaDivider | ElDivider |
| FaCard | ElCard |
| FaContextMenu | 评估 ElDropdown contextmenu |
| FaImagePreview | 评估 ElImageViewer |

FaIcon、FaKbd、FaPinInput、FaScrollArea、FaSmartFixedBlock、FaPasswordStrength 等非同类或专门用途组件不机械删除；若实际有等价成熟实现，再单独评估。FaContextMenu、复杂导航搜索、浮层、登录表单等需要明确焦点、键盘、表单验证和生命周期合同，不能仅替换标签。MRR 的 MrrDataTablePanel、MrrSectionCard、MrrFilterBar 等组合组件属于业务抽象而非另一套基础控件库。原生 HTML 的语义按钮、文件选择、链接等不一刀切，需区分复合控件内部实现与重复的独立基础控件。

## 迁移前实际统计

固定源快照 `dd69b9566807fb2baef4d07df8b8c6b4cbfe41e0`（#837 旧 head）。仅 src/views、src/components、src/layouts 的 Vue 开始标签，排除 ui-components 展厅与 __tests__，不包含动态渲染、内部封装展开次数与原生 HTML。标签统计不等于页面运行时实例数或视觉缺陷数；el-input 含 type=textarea 15处，另有文本/其他类型123处。原始 FaSwitch 22处业务及5处展厅；FaTabs 直接导入的 Tabs/TabsList/TabsTrigger/TabsContent 另见 AppConfigPanel，不计入 FaTabs 标签。

| 控件 | Fa | Element Plus |
| --- | ---: | ---: |
| 按钮 | 21 | 328 |
| 文本/其他单行输入 | 17 | 123 |
| 文本域 | 1 | 15 |
| 选择 | 0 | 51 |
| 开关 | 22 | 21 |
| 复选 | 1 | 8 |
| 滑块 | 1 | 5 |
| 标签页 | 2 | 6 |
| Tooltip | 6 | 7 |
| 弹窗 | 3 | 21 |
| 抽屉 | 1 | 2 |
| 浮层 | 0 | 4 |
| 进度 | 0 | 10 |
| 数字输入 | 0 | 21 |
| 表格 | 0 | 56 |
| 分页 | 0 | 13 |
| 日期 | 0 | 8 |
| 表单 | 0 | 24 |

## 实施顺序与禁止事项

1. 完成开关全量迁移；删除 FaSwitch 及其专属 CSS / 自动类型 / 展厅入口，实际验证键盘、禁用、持久化、G2 圆角及业务布局。禁止再引入同类实现。
2. 集中迁移文本输入、按钮、复选、选择、数字、滑块和标签页，覆盖设置与登录/注册/账户；原验证/保存/权限不变。双族桥接 CSS 仅过渡使用，零引用后清除；不新增 Fa 同类控件。
3. 集中迁移全局导航与 Tooltip/Popover/Dialog/Drawer，重点验证焦点恢复、快捷键、Escape、打开关闭、悬浮状态、层级、移动端滚动。再迁移组件内部依赖、展厅，最后清除死代码和冗余主题分支。
4. 完成定义：同类业务/展厅/组合组件只用一个基础实现，源码与直接导入检查、实际浏览器、真实业务流程、设计令牌所有权和无旧专属样式；依赖删除仅当所有引用不存在。每批按风险审查、集中PR。保护 V1.1/main/医疗数据。

## 已实施：开关归一

22处外观设置和5处展厅 FaSwitch 均迁移 ElSwitch，删除三份旧组件文件、自动类型及旧G2选择器；22个设置开关与原标签对应的可访问名称完整，保留原 v-model/disabled 与设置持久化、权限合同。全量源码无旧开关组件/样式引用，新增 component-ownership.test.ts 防止回流。原页面其他控件不在此次迁移范围。合成数据 E2E 验证键盘、禁用、点击、保存/重新加载。样式测试69/69；首轮联合32/33，旧展厅通过 getByLabel 点击新控件视觉隐藏的原生 input 超时，改为键盘切换并确认 aria-checked 后保留原折行断言。不延长时限或跳过测试。最终定向展厅/圆角/开关15/15含预热（43.7秒）；真实业务布局18项亦通过。日志 /tmp/mrr-v12-switch-{red,green,unit,final,tsc}.log。

## 迁移必须保持页面视觉（用户追加约束）

替换基础控件时优先保留当前页面布局与外观，不将设计改版混入归一 PR。针对实际调用方在同一浏览器、视口、主题、数据和状态下采集旧版本与新版本截图及计算样式；控制位置与几何偏差不超过1 CSS px，字号/字重/圆角/底色/字色保持一致，差异先定位原因。像素级差异须记录，不能将跨平台字体渲染或不同描边机制误称为完全一致；不以隐藏溢出、硬编码全局尺寸或放宽断言掩盖变化。组件替换后的行为、权限、焦点及持久化单独验收。

本次基线 `52557ea5` 与已合并 `3014094e` Git tree 完全一致。AppConfigPanel 五处 FaInput、两组三按钮 FaButton 迁为原生 Element Plus；保留既有200px字段列、flex gap、原尺寸/主题与选中状态，附加可访问名称。Element Plus 自动给双汉字插入间隔，首次对照发现单按钮宽3.609375px、整组位移7.21875px；仅在迁移的两组关闭 auto-insert-space，未修改其他页面。消除原生相邻按钮额外 margin，保留页面 gap。实际四套经典/暖色×明暗、1440/760px、每种七行共56组前后截图与计算样式；第二轮5/5含预热，所测几何最大差0px、所测视觉属性无差异；56对截图同尺寸，RGB任一通道差>8的像素比例中位0.004888%、最大0.117832%，并检查最大差异截图（主页名称）。默认状态下主页字段可编辑、版权四字段禁用。截图不是全页面/全状态像素相等证明。最终设置视觉/操作/搜索及业务控件布局联合 Chromium 28/28（含预热，46.5秒），vue-tsc、ESLint、Stylelint、文档与冻结基线通过。迁移前后证据目录 /tmp/mrr-v12-control-visual-52557，日志 /tmp/mrr-v12-control-visual-{before,after,after2,final,tsc}.log。

复现：同一环境分别在旧、新工作区运行 e2e/settings-control-visual.spec.ts（旧工作区复制该测试，勿改生产源码）；旧版使用 MRR_CONTROL_VISUAL_CAPTURE=true 和 MRR_CONTROL_VISUAL_EVIDENCE=/tmp/独立证据目录，指定 -g '外观设置控件迁移视觉契约'；新版仅提供同一 EVIDENCE 路径。无环境变量时 CI 验证唯一实现、宽度、间距、状态及业务交互；不以 Mac 的像素截图作为 Linux 固定快照。截图只含合成数据。下一批登录/注册须覆盖真实表单验证、密码切换和提交/错误/加载等行为，不能机械替换。

## 登录与注册迁移（2026-11-25）

- #838 已合并 `3c93d1f0`，实际开发基线 `a95f3906` 与该合并 tree 完全一致。登录2输入/1按钮、注册5输入/1文本域/3按钮（含按阶段出现的返回和提交）均直接采用 ElInput/ElButton；保留 FaCheckbox 记住用户名与专用图标，后续单独迁移复选框。VeeValidate/Zod、表单请求、身份、密码显示及两阶段字段保持不变。旧原生 textarea 的 show-word-limit 是无效 HTML 属性，为保留原视觉不启用额外计数器，仍限制 maxlength=500。没有将找回密码（无实际路由入口）或个人设置修改密码（尚无视觉基准）强行纳入本轮。
- 原登录局部38px高度、图标/眼睛位置、按钮基线与原注册表单分区保持。将原字段外观定位迁至 Element Plus wrapper，避免重复边框，错误状态显式类及 aria-invalid，兼容不支持 :has 的浏览器；native-type=submit 保留原生提交。关闭迁移按钮自动双字间隔，避免默认 margin 叠加布局 gap。不改其他页面的按钮默认值。
- 实测经典/暖色×明暗、1440/390、登录/身份/申请三状态共24对前后截图与计算样式，5/5含预热；几何最大差0px，所测字号/字重/圆角/底色/字色无差异。24对同尺寸截图，任一RGB通道差>8的像素比例中位0.096074%、最大1.113596%（暖色浅色390登录），已查看该对实际截图。证据目录 /tmp/mrr-v12-account-visual-a95。未宣称全部状态、像素或跨平台完全相同。
- 账户schema与样式12文件72/72、vue-tsc、ESLint、Stylelint通过。联合Chromium首轮37/38（含预热），唯一失败是模拟登录无Token响应的提示仅放外层，而原表单按ApiResult.data读取；修正测试响应层级，不修改认证业务、不放宽断言，定向复验2/2（24.6秒，含预热）。覆盖实际Mock登录失败/恢复/成功、请求体、记住用户名且不保存密码、密码切换、原匿名注册提交/两阶段返回焦点/数据保留、原动效与认证存储隔离。日志 /tmp/mrr-v12-account-{before,after,unit,tsc,final,recovery-final}.log。未重新执行整套38项；无真实医院/跨浏览器现场验收。
- 复现 e2e/account-control-visual.spec.ts，旧版使用 MRR_ACCOUNT_VISUAL_CAPTURE=true、MRR_ACCOUNT_VISUAL_EVIDENCE=/tmp/独立目录 并限定 -g '账户表单迁移视觉'，新版仅传 EVIDENCE；默认CI检查实际唯一实现、主题、尺寸、行为，不依赖Mac固定截图。先保持原有组件生命周期和语义，再考虑剩余账户修改密码、找回密码、复选、布局/全局工具栏及重复组件清理。

## 复选框与个人设置修改密码（2026-11-25）

- #839已合并968bc901，原head ec0dd83b tree一致，159文件1064/1064与Pages定向7/7。迁移3处FaCheckbox（登录1、展厅2），删除组件目录、自动声明，源码守护禁止回流。登录保留16px圆形、8px间隙、14px标签及局部配色，不影响其他业务复选框。
- 原VeeValidate checkbox componentField与ElCheckbox原生change/model更新语义不完全一致，实际测试发现勾选抵消。改为明确布尔值的modelValue/setValue，保留原字段校验和存储，不修改认证逻辑。浏览器针对真实可见标签点击、原生键盘、选中和禁用测试，避免直接点击Element Plus零尺寸input。
- 个人设置3输入、1按钮统一ElInput/ElButton，保留表单schema、原密码/新密码合同、成功注销。Mock失败/成功、错误反馈、回退可用性及Token清理纳入验收；未触及真实账号。
- 视觉基线20组：四主题×两宽度×两复选状态16组，个人设置桌面4组。原基线采集5/5；首次对照发现1.5px垂直偏移和标签盒模型差异，按原页面尺寸修复，不放宽1px门禁。联合测试曾因旧input定位方式与布尔绑定差异失败；最终联合34/34含预热（1.0m），20对截图所测几何最大0px、计算样式无差，RGB任一通道差>8中位0.113720%、最大2.019011%；非全页或跨平台像素相等。日志 /tmp/mrr-v12-password-{before,after,after2,final,retest,retest2,exact-final,unit,final-tsc}.log，证据目录 /tmp/mrr-v12-password-visual-ec0。73/73单测、vue-tsc、ESLint、Stylelint通过。改密增加覆盖校验及请求生命周期的提交锁，Mock首请求挂起时再次Enter仍仅一请求，失败释放后允许主动重试，成功退出并清除Token。一次历史测试出现2请求，未确认唯一根因；最终专项通过。注册阶段已切换但旧locator.press重试卸载节点超时，改为断言真实焦点后page.keyboard.press，保留联系方式焦点及返回数据断言。日志 /tmp/mrr-v12-password-closure.log。未执行医院现场或全站跨浏览器。无路由找回密码暂保留，滑块和其余基础控件后续集中迁移。

## 滑块与选择器唯一实现（2026-11-25）

- #840 已合并 ef5e5e35，159文件1065/1065、Pages定向34/34、tree 与 f39a3dda 一致。新批次迁移1处圆角滑块、1处展厅滑块、1处展厅选择器；删除 FaSelect/FaSlider 共16个实现文件、类型与旧样式锚点，新增源码守护。保留现有圆角数值（单值而非旧单元素数组）、键盘步长0.25及设置保存，选择器本页状态独立。统一 Element Plus 不影响其他业务控件默认尺寸。
- 原滑块轨道8px、手柄20px，使用共享紧凑规格保留；选择器采用单值字段规格，不强行限制多选内容高度。原控件默认状态对照发现 Element Plus wrapper 字号14而非13px、暖色32而非30px；复用字段字号/高度/内边距/描边与焦点令牌修正，截图抽查暖色边框偏浅后再处理实际描边。
- 四主题×1440/760两宽度×2控件共16组真实前后截图及计算样式。只比较滑块中点与选择器关闭态，未证明滑块端点、所有悬停/焦点/禁用状态、跨浏览器或全页像素相等。实际选项打开、选择、键盘步进、保存与G2圆弧通过前一轮18/18，首次G2开关单通道4/255超原阈值3，后续原阈值复验通过，不修改阈值。71/71样式单测、vue-tsc通过。选择器测试改为点击可见wrapper，区分隐藏input-wrapper与已选择文本，保留实际操作和断言。
- 证据目录 /tmp/mrr-v12-selection-visual-f39；日志 /tmp/mrr-v12-selection-{before,after,final,closure,closure2,closure3,unit,tsc}.log。最终closure3为17/18（39.7秒），唯一未通过是原G2开关像素阈值：4/255>3，未改阈值，前次18/18不替代最新结果。16组所测相对几何最大0px、计算样式无差；12对同尺寸截图RGB任一通道差>8中位0%、最大1.646969%，四对760px选择器截图高差1个像素，未进行像素差比例比较。暖色选择器初次可见边框偏浅，追加描边/留白/文字颜色修正后同比像素差下降。本轮稳定截图时序：相同滚动定位、鼠标移出、等待两帧绘制、禁用截图动画，阈值仍为3/255且完整像素比较不变。G2定向连续6/6（另含预热，共7/7，36.5秒），最终联合18/18（37.8秒），日志 /tmp/mrr-v12-selection-{g2-repeat,final-gate}.log。此前4/255根因未独立证明，仅确认稳定采集下通过；滑块端点截图及完整跨浏览器未执行。提交远端门禁。远端45641871744 Linux定向17/18，原滑块像素差6/255超3；本地Mac通过不代表跨平台。增加真实前后截图附件、超阈值像素坐标/通道及控件几何诊断，失败时上传合成Mock测试证据；不修改阈值。Linux运行45642934648三次诊断一致：320×9截图片内(x233,y8)红通道248→242、绿250→246，52个通道有变化且仅2通道超3。根320×8、20px手柄上下外溢6px，原截图遗漏完整手柄。新规则使开关/滑块在两模式均显式标准圆弧，截图采用包含完整轨道/手柄的整数边界；首次联合17/18仅裁切区域超视口，改全页裁切后专项2/2含预热（21.5s），样式71/71，待Linux复验，不声称已证明唯一根因。日志/tmp/mrr-v12-selection-{ci-diagnostic,fullthumb,fullthumb2,fullthumb-unit}.log。后续标签页、导航按钮及浮层待迁移；无真实医院及完整跨浏览器验收。

## 标签页迁移本地检查点（2026-11-25）

- #841 已创建，head 8563d5a8 的Linux差异诊断独立继续；本工作区基线 993f7427，未包含后续诊断提交，不得直接合并。预览仓库 6d52400、运行45641735418 成功，实际公网根清单和V1.2身份均 ef5e5e35。
- 源码调查：设置分类四项（直接导入FaTabs内部组件）、颜色主题、标题风格和展厅四组，原 FaTabs 6个实现文件。本地替换 ElTabs/ElTabPane、唯一实现守护，采用共享 segmented 样式；保留四列/两列分类、旧组宽及色彩，分组 name 使用 useId 前缀避免多实例冲突，分类内容仅活动组挂载，搜索锚点定位到真实 role=tab，恢复类别 aria-label。尚须验收搜索焦点、主题跟随系统空字符串、Drawer与主设置共存、键盘方向键及 Home/End 合同，不能宣称全部通过。
- 原版4主题×2宽度×4场景共32组实际截图/计算样式，基线5/5含预热（29.4秒），目录 /tmp/mrr-v12-tabs-visual-993。首轮 nested tablist 定位冲突已修；四套桌面分类实际几何及样式一致；主题首尾padding修复后原70/70/98px宽度一致，但三个标签仍整体偏右3px，超过原1px几何门禁，取消EP滚动平移仍未解决。最终 after4 为1/5（4个主题用例失败，预热通过），只采集到8组替换后数据，剩余标题/展厅/窄屏未完成对照。72/72样式单测、vue-tsc 通过；未扩大生产代码范围。日志 /tmp/mrr-v12-tabs-{before,after,after2,after3,after4,tsc,tsc2,unit}.log。
- 本地检查点不推送、不建PR；后续先收口#841，按准确新基线重放，继续实际主题位置诊断及全量32组比较，不放宽阈值；注意ElTabs键盘Home/End差异和Chrome86兼容。

## 标签页视觉与键盘收口（2026-11-25）

- 主题组原240px容器含238px内在内容，恢复1px左右内边距，四套主题全32组相对几何最大差0px、所测字号/字重/颜色/圆角无差，全部截图同尺寸。初次像素最大3.4859%，抽查确认旧UnoCSS shadow-sm为0 1px 2px 0 rgba(0,0,0,.05)，误用卡片阴影；恢复共享分段标签轻阴影后，32对任一RGB通道差>8的像素比例均0%，不宣称所有像素完全一致。保留两列/四列分类及原组宽。
- 新增仅处理Home/End的辅助键盘函数，方向键仍由Element Plus拥有；实际设置搜索、目标焦点、主题空字符串跟随系统、标题设置、抽屉独立ID、保存与离开确认通过。联合40/40（1.0m，阴影修正前），最终阴影修正后定向6/6（29.8秒，含预热），样式72/72、vue-tsc、lint通过。日志 /tmp/mrr-v12-tabs-{after5,closure,closure-tsc,final-unit,shadow-final}.log。
- #841已合并2e9db6ef，head40786f77与合并tree一致，远端159文件1066/1066、Pages18/18、230/230路由。标签分支重放至2e9db6ef成功，保留G2修复；最终联合标签/G2 9/9（32.7秒）。未执行Chrome86现场与完整跨浏览器。

## 全局工具栏与导航按钮迁移（2026-11-25）

- #842 已合并 bec3cd9a，head3e02d580，159文件1067/1067、Pages6/6、tree一致；本批基线bec3cd9a。账户菜单触发器、应用外观、标志折叠、次导航折叠、移动导航、全屏、刷新、主题切换及全局搜索共9处调用迁至 ElButton；保留菜单、提示、弹窗现有实现和事件合同。共享导航按钮样式仅用于迁移实例，保留方形图标、搜索留白、账户中性底与状态；消除原生按钮相邻margin和slot容器引入的布局差异。9处防回退源码守护。
- 实际4主题×1440/720共8对顶栏截图、52个可见按钮实例的几何/字号/字重/圆角/颜色/边框，首轮5/5，8对任一RGB通道差>8像素比例0%。增加侧栏头像和标志四主题×展开/收起×两控件16对基线；发现账户内层24px图标被EP通用16px规则缩小，恢复原图标继承尺寸；联合30/34，四失败均由收起侧栏原宽泛span隐藏规则使新EP内容span消失，收窄至.logo-title。没有放宽1px几何门禁。最终工具栏与导航联合15/15含预热（31.7秒），24对同尺寸截图所测几何最大0px、属性一致，任一RGB通道差>8的像素比例全0%；非完全像素相等。主题、全屏、刷新、搜索、账户菜单键盘/焦点及折叠状态通过；未重跑此前34项联合全套。旧对比度测试从旧工具类改为共享搜索锚点及12px padding/8px margin检查，保留 muted 对比度断言；73/73样式测试、vue-tsc通过。日志 /tmp/mrr-v12-toolbar-{before-all,after,sidebar-before,sidebar-after,final,closure,unit-final,tsc}.log；证据目录 /tmp/mrr-v12-toolbar-visual-bec-all（含补采侧栏）及 /tmp/mrr-v12-toolbar-sidebar-bec。
- 预览仓库0831685增加当前运行及尝试精确制品元数据可见性门禁（发布job actions:read），实际45646958524成功，独立no-cache根及V1.2身份bec3cd9a、V1.1保持92896f7c；未证明未来所有调度正常。
- 尚存29处FaButton调用（展厅、链接/404及无入口找回密码），FaInput/FaTextarea等另批；不将专用菜单或业务组件机械改写。移动搜索原图标按钮无显式可访问名称的历史问题另行修复；未执行医院现场/完整跨浏览器。

## 按钮、按钮组、输入与文本域全量归一（2026-11-25）

- #843 已合并 fc2a56f7，head e4a55ba1，159文件1068/1068、Pages10/10、tree一致。GitHub GraphQL/评论API错误、merge返回502但之后查询实际MERGED；不重复合并。预览触发HTTP500，无新运行确认，公网仍以已核验bec3cd9a为准。
- 重新全源码扫描而非仅页面：45处按钮、3处按钮组、8处输入及1处文本域，含公共组件内部、展厅、无路由旧找回密码表单、独立验收夹具。全部迁ElButton/ElButtonGroup/ElInput，删除4+6=10个旧实现文件、双轨样式及类型声明；新增唯一归属守护。保留原方形/圆形图标、中性辅助、加载图标与按钮组共享边缘；明确native-type提交，不启用旧找回密码功能。展厅仍保留原示例布局，文案和目录更改为唯一实现。DESIGN.md与ENGINEERING.md更新。
- 输入旧基线48组（四主题×1440/760×6类），基线5/5，替换5/5，前一轮字段专项联合33/33（51.2秒）；所测宽高0px、计算属性一致，48对同尺寸，RGB任一通道差>8中位1.7149%、最大13.7575%（窄屏独立校验），实际抽查边框、字边缘与搜索位置有轻微差异，不宣称像素完全相同。只读显式样式锚点保护Chrome86无:has场景，显式rows保留原高度；统一校验反馈及无障碍属性。
- 按钮64组行级真实前后对照（四主题×1440/760×默认/禁用/加载及尺寸），基线5/5（24.5s），首两轮1/5，定位Element Plus文字禁用背景!important覆盖辅助底色，局部恢复原中性背景而不修改普通文字按钮；第三轮5/5（27.7秒）。最大几何差1px仅按钮组相邻边缘，所测属性0差；64对同尺寸，RGB任一通道差>8比例中位0%、最大14.3231%（窄屏尺寸/按钮组），实际抽查外观相近，非像素完全相等。75/75样式测试、vue-tsc、lint通过；联合35/36（1.0m），唯一失败为独立验收夹具迁ElButton后未显式aria-busy；生产原Fa加载调用已显式传入。补齐夹具后交互专项7/7（20.3秒），保留原忙碌断言，未重跑全部36项。日志 /tmp/mrr-v12-base-controls-accessibility.log。
- 证据目录 /tmp/mrr-v12-fields-visual-e4、/tmp/mrr-v12-buttons-visual-e4；日志 /tmp/mrr-v12-fields-{before,after,closure,unit,tsc}.log、/tmp/mrr-v12-buttons-{before,after,after2,after3,unit-final,tsc}.log、/tmp/mrr-v12-base-controls-closure.log。尚未验收无路由旧找回密码、文件上传/旧图片预览专用组件的完整交互与真实截图、医院Chrome86及全站跨浏览器。后续集中处理浮层、菜单与提示；不机械替换业务组合组件。

## 提示与轻量气泡统一（2026-11-26）

- #844精确head f6934742，base fc2a56f7，治理45649948054、质量45649947924（159文件1070/1070）、Pages45649947825（230/230路由、31/31定向浏览器）全部成功。自审评论/合并GraphQL异常、REST尝试响应不完整，最后查询仍OPEN，不能宣称合并。该分支基于已验收f6934742独立开发，须在#844确认合并且tree一致后重放，不绕过保护。
- 8处FaTooltip、1处FaPopover迁移Element Plus，删除11个旧实现文件及类型，新增唯一归属守护。MrrTooltip为单一ElTooltip的薄策略封装，保留300/200/1000ms延迟、hover/focus、Escape不吞掉上层关闭、原触发节点及原有键盘激活。禁用时直接渲染触发器，防止EP覆盖导航分组aria-expanded/aria-controls。既有直接ElTooltip仍使用同一底层实现，其暗色/箭头等业务差异暂保留。Popover使用EP的定位/关闭，保留点击外部、Escape、固有宽度及12px碰撞余量。
- 原基准首次采集节点消失、后续发现入场缩放95%，不使用不稳定数据；等待动画结束后的16对真实截图（四主题×1440/760×两浮层），基线5/5（24.1秒），初次替换尺寸5/5但窄屏Popover位置差约196~214px，新增实际位置断言，联合26/32（登录、展厅等通过，2处导航aria被覆盖、4组窄屏位置失败）。修复禁用分支与Popover固有宽度后最终定向10/10（35.7秒），16对同尺寸，所测相对x/y/宽高最大0px、圆角/颜色/背景/字号/留白/边框一致，RGB任一通道差>8比例全0%，非所有像素完全相等。单测80/80（76样式+4菜单），vue-tsc、lint通过。未重跑最终32全套、医院Chrome86及完整跨浏览器。日志/tmp/mrr-v12-tooltip-{before-stable,after,closure,final,unit,tsc2}.log，证据/tmp/mrr-v12-tooltip-stable-f693。
- 本地检查点保留，未合并。后续先处理#844实际合并，再集中迁移Dialog/Drawer/Dropdown；保留账户、搜索与设置关闭及焦点语义。

## 对话框与抽屉统一（2026-11-26）

- #844 已合并2dff0e4b，原head f6934742 tree一致；#845 head6b59857、远端1071/1071（159文件）、Pages5/5、230/230路由及治理全部通过，首次合并超时后重新确认OPEN、精确head及base，第二次GitHub连接器成功合并3cff46b1，tree一致。预览dispatch HTTP500，未确认新版本上线。
- 4处旧Modal、3处旧Drawer及独立交互夹具迁移MrrDialog/MrrDrawer，唯一底层为ElDialog/ElDrawer；删除23个旧Reka实现文件。保留旧表面几何、标题/正文/按钮、遮罩色、设置抽屉持久挂载、导航搜索opened后聚焦、个人设置原关闭限制（保留props）。不再维护无调用的最大化/自研拖拽接口。焦点陷阱与关闭生命周期由EP承担，去除未实际实现的旧openAutoFocus参数，属于焦点策略变化。未迁移账户Dropdown。
- 24组旧/新真实视觉（四主题×1440/760×展厅Dialog/Drawer/设置Drawer），基线5/5（26.6s）；首轮1/5，暖色间距覆盖和滑入采集错误，修正后5/5（32.4s）；联合39/40（1.0m）仅一例入场位移20px，修复祖先动画等待；实际截图发现设置标题下方5px偏移，恢复旧4px间距及20px描述行高。最终21/21（44.2s），24对同尺寸，所测几何最大0px、属性一致，RGB任一通道差>8中位0.010995%、最大0.470486%，非像素完全相同；77/77样式，vue-tsc及lint通过。覆盖关闭事件正常/减弱/关闭动效、搜索权限、设置保存、独立标识、改密失败恢复/重复提交/退出。未重跑最终40全套；尚未独立对照快捷键介绍、系统信息、导航搜索、个人设置全表面截图及其遮罩/Escape限制专项，医院Chrome86与全站跨浏览器未执行。
- 日志 /tmp/mrr-v12-dialog-{before,after,after2,closure,final,unit-final,final-tsc}.log；证据 /tmp/mrr-v12-dialog-visual-6b。新批基线6b59857与3cff46b1代码树一致，重放至合并基线后提交。下一批账户下拉菜单及历史样式/依赖清理。

## 账户与标签页菜单统一（2026-11-26）

- #846 已合并 adc06302，head cdf7a301，159文件1072/1072、Pages5/5及治理通过，tree一致。账户菜单、标签页右键菜单均迁 ElDropdown，删除16+17个旧实现文件，保留共享样式、危险色、禁用操作、原指针定位。标签页使用单一虚拟锚点，无额外逐标签可交互节点，支持Shift+F10/ContextMenu及Escape焦点恢复。
- 账户菜单四主题×1440/760共8对截图，旧版移动入口须先展开侧栏，修复采集路径后5/5；旧/新个人设置关闭保护（Escape、遮罩、保留输入及关闭按钮）通过。右键菜单浅/深两组真实截图及关闭非活动标签测试，初次宽差5px源自EP默认图标margin，移除重复间距。最终10对截图同尺寸，RGB任一通道差>8比例全0%，非逐像素相等。账户所测几何与属性、右键所测几何与字体/圆角通过1px门禁。样式78/78，vue-tsc、lint通过。联合26/26（44.5s），新增Shift+F10/Escape后右键专项3/3（18.1s）。未验证触摸长按、医院Chrome86、全站跨浏览器；其他业务浮层全表面截图仍待补齐。
- 剩余Reka实际调用包含头像、折叠、旧图片预览、通知、PIN、进度、滚动及shadcn表单/标签；class-variance-authority仍用于头像和通知，不直接移除依赖。下一批按重叠能力处理Avatar/Progress/Collapse/Divider及旧上传/预览与通知，避免机械删除专用实现。
- 日志/tmp/mrr-v12-menu-{before2,after,closure,unit,tsc}.log，/tmp/mrr-v12-context-{before,after,final,tsc}.log；证据/tmp/mrr-v12-menu-visual-cdf及/tmp/mrr-v12-context-visual-cdf。

## 残余基础控件与无调用链清理（2026-11-26）

- #847 head4723c38c，159文件1073/1073、Pages7/7、230/230路由及治理全部成功，已合并9c9022a1，tree一致。
- Avatar两处、展厅Progress一处、系统信息Divider四处迁EP。曾检查旧图片上传内部Progress，因全链无业务调用最终整链删除，不计实际迁移。FaCard/FaCollapsible无运行时引用（Card仅活跃工程文档示例），删除；旧FaFileUpload/FaImageUpload/FaImagePreview仅内部相互引用，旧FaNotification仅App挂载空容器、无调用；删除这四个组件及挂载。实际业务通知仍由ElMessage、病案查看器原业务实现承担。共删除49个旧实现文件，新增两项唯一归属守护。移除无引用class-variance-authority，package及lock仅对应删除11行；保留仍服务PIN、ScrollArea、shadcn表单/标签的Reka。历史自动生成文档保留，不作为当前实现依据。
- 28组真实视觉（四主题×1440×两头像/进度/四分隔线），基线5/5（27.1秒），首次迁移5/5（26.5秒）。旧基线gr8r-light产品版本x1456为抽屉入场帧，最终等待实际抽屉右边界后x1072；其余27组所测位置一致。全部28对同尺寸，所测宽高最大0px、背景/色/圆角/字体大小一致，RGB任一通道差>8中位0%、最大0.10745%（进度条），非像素完全相等。清理依赖后最终37/37（1.2m）含系统信息、账户、展厅、改密及病案查看器，92/92单测含通知/请求错误/剪贴板，vue-tsc/lint通过。未进行非空头像网络慢加载/真实上传专用全流程（旧链已无调用）、全部业务浮层截图、医院Chrome86及完整跨浏览器。
- 日志/tmp/mrr-v12-residual-{before,after,closure,unit,final-tsc,remove}.log；证据/tmp/mrr-v12-residual-visual-9c。预览手动触发恢复，run45656099309计划V1.2 9c9022a1及V1.1冻结92896f7c，正在构建，尚未确认部署。
- 后续集中完成公共通知入口约束（已统一底层，不强制批量改业务调用）、保留专用组件的归属审查、历史CSS/文档、全站视觉与浏览器验收。禁止为了零Reka而重写专用PIN和滚动区。

## 全局样式工程与剩余专用组件归属（2026-11-26）

- #848首轮head444c5479的生产构建成功，Chrome86静态门禁发现旧分段标签 `:is()`（需要88+）；b4a4e8bc改为等价显式:nth-child(2)/:last-child列表，新增守护9/9，本地生产构建和静态兼容通过。未放宽门禁，待精确新head远端复验。原部署run45656099309首次Pages查不到已上传artifact，核验artifact实际存在后重试失败任务成功，公网versions.json及preview-build.json均证实V1.2为9c9022a1、V1.1冻结92896f7c。
- 独立样式治理分支基于b4a4e8bc。1826行EP桥接按原连续声明顺序拆八个模块，入口9行；PostCSS去除注释和空白后的完整选择器、at-rule、属性、值、important和顺序AST完全一致。四套原守护改读取真实导入级联，新增模块顺序/完备性守护。仅在外部form-controls.css合并分段标签nav的重复规则、tokens.css删除同根暗色重复控制边框别名；不跨模块搬动其他可能存在级联意义的同选择器规则。
- 复查FaAuth、FaFixedActionBar、FaPageHeader、FaPasswordStrength、FaSearchBar零运行时引用，删除5个旧实现及类型，保留现有路由/业务权限，不修改密码校验。其余11个旧目录包含ActionDialog（全局确认）、FaBackToTop、FaCopyright、FaIcon、FaKbd、FaNotAllowed、FaPageMain（外部链接容器）、FaPinInput（旧找回密码，未启用）、FaScrollArea、FaSmartFixedBlock（顶栏布局）、FaSystemInfo（系统信息），均有专用职责或实际调用，保留。禁止为清零Fa前缀而重写。
- 短期消息底层已统一ElMessage；46个文件包含直接引用，218处四类型调用，保留已有业务参数，不机械批量迁移。新增notify六项契约测试，默认5000ms、重复分组、无关闭按钮，支持duration=0及显式关闭/位置/分组，空说明不加标点。已有请求级去重和查看器z-index不变。
- 90/90样式及通知单测、vue-tsc/lint、生产构建和Chrome86静态门禁通过；原兼容脚本对依赖@container、工具链选择器、107处color-mix及8处硬编码动效仍有提示，不能声称旧浏览器真实完整验收。跨浏览器配置调用未获执行，不绕过；本轮只执行既有Chromium定向回归。日志/tmp/mrr-v12-style-{unit,tsc,build,compat,browser}.log。最终Chromium定向29/29（1.3m），包含配色、G2、账户菜单、头像/进度/分隔线、分段标签及展厅，原截图基准仍生效。#848远端新head复验中，独立本地检查点待合并基线确认后重放。

## 逐文件原始引用清单

| 源文件（相对 src/） | Fa 标签 | Element Plus 标签 |
| --- | --- | --- |
| `components/AccountButton/index.vue` | FaAvatar × 2, FaButton × 1, FaIcon × 6, FaModal × 1 | — |
| `components/AccountForm/EditPasswordForm.vue` | FaButton × 1, FaInput × 3 | — |
| `components/AccountForm/LoginForm.vue` | FaButton × 1, FaCheckbox × 1, FaIcon × 3, FaInput × 2 | el-icon × 2 |
| `components/AccountForm/RegisterFieldLabel.vue` | FaIcon × 1, FaTooltip × 1 | — |
| `components/AccountForm/RegisterForm.vue` | FaButton × 2, FaIcon × 5, FaInput × 5, FaTextarea × 1 | — |
| `components/AccountForm/ResetPasswordForm.vue` | FaButton × 3, FaInput × 2, FaPinInput × 1 | — |
| `components/AppEmpty/index.vue` | — | el-button × 1 |
| `components/AppError/index.vue` | — | el-button × 1 |
| `components/MrrDataTablePanel/index.vue` | — | el-tag × 1 |
| `components/MrrFilterActionButton/index.vue` | FaIcon × 1 | el-button × 1 |
| `components/MrrInfoTip/index.vue` | — | el-icon × 1, el-tooltip × 1 |
| `components/MrrMetricCard/index.vue` | FaIcon × 1 | el-card × 1 |
| `components/MrrPageHeader/index.vue` | FaIcon × 1 | — |
| `components/MrrScanContentDialog/index.vue` | — | el-button × 7 |
| `components/MrrSectionCard/index.vue` | FaIcon × 1 | el-card × 1 |
| `components/MrrStorageBrowser/InlinePreview.vue` | — | el-button × 1 |
| `components/MrrStorageBrowser/Preview.vue` | — | el-dialog × 1, el-image × 1 |
| `components/MrrStorageBrowser/index.vue` | — | el-button × 11, el-button-group × 3, el-icon × 2, el-input × 2, el-tooltip × 1 |
| `components/MrrTableActions/index.vue` | FaIcon × 4 | el-button × 2, el-dropdown × 1, el-dropdown-item × 1, el-dropdown-menu × 1, el-tooltip × 1 |
| `components/MrrUserSearchSelect/index.vue` | — | el-option × 1, el-select × 1 |
| `components/ScanQueryDiagnostics/index.vue` | FaIcon × 1 | el-alert × 1, el-button × 2, el-dialog × 1, el-tag × 1 |
| `components/SystemAdminContactPopover/index.vue` | FaIcon × 3 | el-popover × 1 |
| `layouts/components/AppSetting/index.vue` | FaButton × 1, FaDrawer × 1, FaIcon × 1 | — |
| `layouts/components/Header/index.vue` | FaIcon × 1, FaScrollArea × 1 | — |
| `layouts/components/HotkeysIntro/index.vue` | FaKbd × 19, FaModal × 1 | — |
| `layouts/components/Logo/index.vue` | FaButton × 1, FaIcon × 1 | — |
| `layouts/components/MainSidebar/index.vue` | FaIcon × 1, FaScrollArea × 1 | — |
| `layouts/components/Menu/item.vue` | FaIcon × 2, FaTooltip × 1 | — |
| `layouts/components/Menu/sub.vue` | FaScrollArea × 1 | — |
| `layouts/components/SubSidebar/index.vue` | FaButton × 1, FaIcon × 1, FaScrollArea × 1 | — |
| `layouts/components/Topbar/Tabbar/index.vue` | FaContextMenu × 1, FaIcon × 2, FaScrollArea × 1, FaTooltip × 1 | — |
| `layouts/components/Topbar/Toolbar/ColorScheme/index.vue` | FaButton × 1, FaIcon × 1 | — |
| `layouts/components/Topbar/Toolbar/Fullscreen/index.vue` | FaButton × 1, FaIcon × 1 | — |
| `layouts/components/Topbar/Toolbar/NavSearch/index.vue` | FaButton × 1, FaIcon × 1, FaKbd × 1 | — |
| `layouts/components/Topbar/Toolbar/NavSearch/search.vue` | FaIcon × 8, FaKbd × 4, FaModal × 1, FaScrollArea × 1 | — |
| `layouts/components/Topbar/Toolbar/PageReload/index.vue` | FaButton × 1, FaIcon × 1, FaKbd × 1, FaTooltip × 1 | — |
| `layouts/components/Topbar/Toolbar/leftSide.vue` | FaButton × 1, FaIcon × 1 | — |
| `layouts/components/Topbar/index.vue` | FaSmartFixedBlock × 1 | — |
| `layouts/components/views/link.vue` | FaButton × 1, FaIcon × 2, FaPageMain × 1, FaTooltip × 1 | — |
| `layouts/index.vue` | FaCopyright × 1 | — |
| `views/[...all].vue` | FaButton × 2, FaIcon × 1 | — |
| `views/archive-boxes/index.vue` | — | el-button × 5, el-dialog × 1, el-form × 1, el-form-item × 6, el-icon × 2, el-input × 10, el-option × 2, el-pagination × 2, el-radio-button × 2, el-radio-group × 1, el-select × 2, el-table × 2, el-table-column × 13, el-tag × 1 |
| `views/audit-images/components/AuditAnalytics.vue` | — | el-table × 2, el-table-column × 8, el-tag × 2 |
| `views/audit-images/index.vue` | — | el-alert × 1, el-button × 2, el-date-picker × 1, el-descriptions × 1, el-descriptions-item × 13, el-dialog × 1, el-form × 1, el-form-item × 6, el-icon × 2, el-input × 2, el-option × 2, el-pagination × 1, el-select × 2, el-table × 1, el-table-column × 10, el-tag × 2 |
| `views/auth-test/components/ExternalArchiveTicketTester.vue` | — | el-alert × 5, el-button × 13, el-form × 3, el-form-item × 14, el-input × 13, el-option × 1, el-select × 1, el-switch × 2, el-table × 1, el-table-column × 5, el-tag × 4 |
| `views/auth-test/components/JwtLoginTester.vue` | — | el-alert × 1, el-button × 6, el-form × 2, el-form-item × 3, el-input × 3, el-tag × 2 |
| `views/auth-test/components/OssDiagnosticsTester.vue` | — | el-alert × 1, el-button × 3, el-tag × 1 |
| `views/auth-test/components/ProtectedApiTester.vue` | — | el-button × 2, el-checkbox × 1, el-form × 1, el-form-item × 6, el-input × 4, el-option × 1, el-select × 1, el-tag × 1 |
| `views/auth-test/index.vue` | — | el-button × 1, el-card × 1, el-tab-pane × 5, el-table × 1, el-table-column × 7, el-tabs × 1, el-tag × 3 |
| `views/data-exchange/components/DatasetImportPanel.vue` | — | el-alert × 2, el-button × 4, el-icon × 1, el-table × 1, el-table-column × 4, el-upload × 1 |
| `views/data-exchange/index.vue` | — | el-button × 5, el-date-picker × 2, el-input × 19, el-input-number × 2, el-option × 5, el-segmented × 1, el-select × 1 |
| `views/data-relations/ArchiveManifestCard.vue` | — | el-alert × 1, el-button × 12, el-option × 1, el-progress × 1, el-select × 1, el-table × 2, el-table-column × 11 |
| `views/data-relations/RepairOrderPanel.vue` | — | el-alert × 1, el-button × 7, el-table × 1, el-table-column × 6, el-tag × 2, el-timeline × 1, el-timeline-item × 1 |
| `views/data-relations/components/ArchiveScanInspector.vue` | — | el-button × 7, el-skeleton × 1 |
| `views/data-relations/components/QualityCaseReview.vue` | — | el-button × 7, el-table × 1, el-table-column × 5, el-timeline × 1, el-timeline-item × 1 |
| `views/data-relations/index.vue` | FaIcon × 4 | el-alert × 3, el-button × 10, el-card × 2, el-descriptions × 2, el-descriptions-item × 5, el-drawer × 1, el-input × 2, el-option × 6, el-progress × 1, el-select × 2, el-tab-pane × 8, el-table × 10, el-table-column × 60, el-tabs × 2, el-tag × 3 |
| `views/database-browse/index.vue` | FaIcon × 1 | el-alert × 4, el-button × 5, el-input × 1, el-option × 1, el-select × 1, el-table × 1, el-table-column × 1, el-tag × 2 |
| `views/help/index.vue` | FaIcon × 1 | el-button × 1, el-card × 1, el-icon × 1, el-tag × 1 |
| `views/home/DashboardWorkspace.vue` | FaIcon × 16 | el-button × 13, el-dialog × 2, el-dropdown × 1, el-dropdown-item × 6, el-dropdown-menu × 1, el-form × 1, el-form-item × 4, el-input × 2, el-option × 2, el-radio-button × 1, el-radio-group × 1, el-select × 2, el-switch × 1, el-tooltip × 1 |
| `views/home/DemoScenarioControl.vue` | — | el-button × 2, el-option × 1, el-select × 1 |
| `views/home/HomeArchiveLookup.vue` | FaIcon × 1 | el-button × 1, el-input × 2 |
| `views/idcard/index.vue` | — | el-button × 1, el-input × 1 |
| `views/login.vue` | FaCopyright × 1, FaIcon × 2, FaTooltip × 1 | el-alert × 1, el-icon × 1, el-popover × 1 |
| `views/logs/index.vue` | — | el-alert × 1, el-button × 4, el-date-picker × 1, el-descriptions × 1, el-descriptions-item × 18, el-dialog × 1, el-form × 1, el-form-item × 7, el-icon × 2, el-input × 3, el-option × 2, el-pagination × 1, el-select × 2, el-table × 1, el-table-column × 9, el-tag × 2 |
| `views/monitoring-dashboard/index.vue` | — | el-button × 2 |
| `views/monitoring/index.vue` | — | el-alert × 1, el-button × 4, el-descriptions × 2, el-descriptions-item × 16, el-progress × 2, el-table × 3, el-table-column × 15, el-tag × 5 |
| `views/nginx-browser/index.vue` | — | el-button × 4, el-option × 1, el-select × 1, el-tag × 1 |
| `views/ocr-batches/index.vue` | FaIcon × 1 | el-alert × 2, el-button × 12, el-checkbox × 1, el-dialog × 1, el-form × 1, el-form-item × 3, el-input-number × 3, el-option × 1, el-select × 1, el-table × 2, el-table-column × 13 |
| `views/operations-center/DeliveryStatusCard.vue` | — | el-alert × 1, el-button × 1, el-descriptions × 2, el-descriptions-item × 10, el-skeleton × 1, el-tag × 2 |
| `views/operations-center/components/NginxConfigurationCard.vue` | — | el-alert × 1, el-button × 4, el-tag × 1 |
| `views/operations-center/components/NginxRuntimeCard.vue` | — | el-button × 3, el-radio-button × 2, el-radio-group × 1, el-table × 1, el-table-column × 5, el-tag × 1 |
| `views/operations-center/components/OperationsHistoryCard.vue` | — | el-button × 1, el-radio-button × 2, el-radio-group × 1, el-skeleton × 1 |
| `views/operations-center/components/StorageCircuitHistoryCard.vue` | — | el-button × 1, el-radio-button × 2, el-radio-group × 1, el-skeleton × 1, el-table × 1, el-table-column × 5, el-tag × 1 |
| `views/operations-center/index.vue` | — | el-alert × 7, el-button × 23, el-card × 1, el-descriptions × 1, el-descriptions-item × 3, el-form × 1, el-form-item × 5, el-input × 3, el-input-number × 1, el-option × 1, el-progress × 2, el-select × 1, el-skeleton × 1, el-tab-pane × 8, el-table × 6, el-table-column × 35, el-tabs × 1, el-tag × 16, el-timeline × 1, el-timeline-item × 1 |
| `views/oss-browser/index.vue` | — | el-tag × 1 |
| `views/oss-migration/components/OssFileExplorer.vue` | — | el-button × 3 |
| `views/oss-migration/index.vue` | — | el-alert × 7, el-button × 11, el-card × 1, el-descriptions × 1, el-descriptions-item × 13, el-dialog × 1, el-drawer × 1, el-form × 3, el-form-item × 12, el-input × 4, el-input-number × 3, el-option × 7, el-pagination × 2, el-progress × 2, el-radio-button × 3, el-radio-group × 1, el-select × 3, el-switch × 1, el-tab-pane × 2, el-table × 4, el-table-column × 33, el-tabs × 1, el-tag × 13 |
| `views/password/change-required.vue` | FaIcon × 3 | el-alert × 1, el-button × 2, el-form × 1, el-form-item × 3, el-input × 3, el-progress × 1 |
| `views/patients/components/PatientAnalyticsPanel.vue` | — | el-alert × 1, el-pagination × 2, el-switch × 1, el-tab-pane × 3, el-table × 2, el-table-column × 13, el-tabs × 1, el-tag × 1 |
| `views/patients/components/PatientEditDialog.vue` | — | el-alert × 1, el-button × 2, el-date-picker × 2, el-dialog × 1, el-form × 1, el-form-item × 8, el-input × 6 |
| `views/patients/components/PatientImportDialog.vue` | — | el-alert × 3, el-button × 4, el-dialog × 1, el-icon × 1, el-table × 1, el-table-column × 4, el-upload × 1 |
| `views/patients/index.vue` | FaIcon × 1 | el-button × 4, el-icon × 1, el-input × 1, el-pagination × 1, el-table × 1, el-table-column × 10 |
| `views/permissions/index.vue` | — | el-button × 3, el-card × 1, el-input × 2, el-option × 1, el-option-group × 1, el-select × 1, el-table × 1, el-table-column × 2, el-tag × 5, el-tooltip × 1 |
| `views/records/index.vue` | — | el-button × 3, el-descriptions × 1, el-descriptions-item × 12, el-dialog × 1, el-icon × 1, el-input × 4, el-option × 2, el-select × 2, el-table × 1, el-table-column × 11 |
| `views/response-analysis/index.vue` | — | el-button × 1, el-icon × 1, el-table × 1, el-table-column × 7 |
| `views/runtime-errors/index.vue` | — | el-button × 6, el-descriptions × 1, el-descriptions-item × 12, el-dialog × 1, el-icon × 1, el-input × 1, el-option × 6, el-pagination × 1, el-select × 3, el-table × 1, el-table-column × 9, el-tag × 2 |
| `views/scan-img/index.vue` | — | el-icon × 1, el-image × 2, el-tooltip × 2 |
| `views/settings/components/AppConfigPanel.vue` | FaButton × 2, FaIcon × 8, FaInput × 5, FaSlider × 1, FaSwitch × 22, FaTabs × 2 | el-button × 3, el-color-picker × 1 |
| `views/settings/components/ArchiveSettings.vue` | — | el-alert × 1, el-form-item × 4, el-input-number × 1, el-segmented × 3, el-slider × 1, el-switch × 2 |
| `views/settings/components/ClassificationRules.vue` | — | el-alert × 1, el-button × 9, el-checkbox × 3, el-input × 5, el-input-number × 2, el-option × 8, el-select × 4, el-table × 2, el-table-column × 11 |
| `views/settings/components/DepartmentThemeSettings.vue` | FaIcon × 2 | el-alert × 1, el-button × 8, el-card × 1, el-color-picker × 2, el-dialog × 1, el-input × 2, el-option × 1, el-select × 1 |
| `views/settings/components/DeveloperSettings.vue` | FaIcon × 2 | el-alert × 3, el-input × 1, el-switch × 2, el-tag × 1 |
| `views/settings/components/DocumentationSettings.vue` | FaIcon × 1 | el-alert × 1, el-button × 1, el-form-item × 1, el-input × 1 |
| `views/settings/components/ExternalLinksSettings.vue` | FaIcon × 7 | el-button × 3, el-input × 2, el-tag × 1 |
| `views/settings/components/ImageDirectoryRoutingSettings.vue` | — | el-alert × 1, el-button × 5, el-input × 2, el-option × 3, el-select × 2, el-tag × 1 |
| `views/settings/components/LoginSupportSettings.vue` | — | el-alert × 1, el-form-item × 17, el-input × 17, el-switch × 3 |
| `views/settings/components/OcrClassificationSettings.vue` | — | el-alert × 1, el-form × 1, el-form-item × 7, el-input × 2, el-input-number × 5, el-switch × 3 |
| `views/settings/components/RuntimeSettings.vue` | FaIcon × 1 | el-alert × 2, el-button × 3, el-form × 1, el-form-item × 1, el-input × 1, el-input-number × 1, el-option × 1, el-select × 1, el-switch × 1, el-tag × 3 |
| `views/settings/components/SecuritySettings.vue` | — | el-form-item × 2, el-input-number × 1, el-slider × 1, el-switch × 3 |
| `views/settings/components/SettingsSearch.vue` | FaIcon × 2 | el-autocomplete × 1 |
| `views/settings/components/SystemInfoSettings.vue` | — | el-alert × 1, el-form-item × 5, el-input × 5 |
| `views/settings/components/UnifiedSettingsPage.vue` | FaIcon × 8 | el-button × 6, el-form × 1, el-option × 1, el-select × 1, el-tag × 1 |
| `views/statistics-detail/ArchiveDetailContent.vue` | FaIcon × 2 | el-button × 4, el-date-picker × 1, el-input × 3, el-option × 2, el-pagination × 1, el-radio-button × 2, el-radio-group × 1, el-select × 2, el-table × 1, el-table-column × 12, el-tag × 3 |
| `views/statistics/archive.vue` | — | el-alert × 1, el-button × 10, el-option × 2, el-select × 1 |
| `views/statistics/archive/components/ArchiveCaseList.vue` | — | el-skeleton × 1, el-tag × 1 |
| `views/statistics/archive/components/ArchiveHeader.vue` | — | el-button × 3, el-dialog × 1, el-popover × 1 |
| `views/statistics/archive/components/ArchiveMoreSettings.vue` | — | el-button × 2, el-popover × 1, el-segmented × 8, el-slider × 3, el-switch × 2 |
| `views/statistics/archive/components/ArchiveQueryFailure.vue` | FaIcon × 2 | el-button × 2 |
| `views/statistics/archive/components/ArchiveRecentLookups.vue` | FaIcon × 1 | — |
| `views/statistics/archive/components/ArchiveSearchBar.vue` | — | el-input × 3 |
| `views/statistics/archive/components/ArchiveWall.vue` | — | ElImageViewer × 1 |
| `views/statistics/archive/components/ClassificationReview.vue` | — | el-alert × 1, el-button × 8, el-option × 2, el-select × 2, el-table × 2, el-table-column × 11 |
| `views/statistics/archive/components/OcrPanel.vue` | — | el-alert × 1, el-button × 6, el-config-provider × 1, el-progress × 1, el-tag × 1 |
| `views/statistics/archive/components/PatientCard.vue` | FaIcon × 1 | — |
| `views/statistics/archive/components/PreviewActionBar.vue` | — | el-button × 3, el-option × 1, el-select × 1 |
| `views/statistics/archive/components/PreviewPanel.vue` | — | ElImageViewer × 1 |
| `views/statistics/archive/components/PreviewViewerToolbar.vue` | — | el-icon × 5 |
| `views/statistics/archive/components/TypeFilterBar.vue` | — | el-checkbox × 2 |
| `views/statistics/external-archive.vue` | — | el-button × 3, el-result × 3 |
| `views/statistics/index.vue` | — | el-button × 2, el-date-picker × 1, el-input × 1, el-option × 2, el-pagination × 1, el-select × 1, el-table × 1, el-table-column × 7, el-tag × 1 |
| `views/users/index.vue` | FaIcon × 3 | el-alert × 5, el-button × 16, el-checkbox × 1, el-dialog × 6, el-form × 4, el-form-item × 11, el-input × 5, el-input-number × 2, el-option × 6, el-pagination × 1, el-select × 6, el-table × 1, el-table-column × 8, el-tag × 1 |

2026-11-26：#844 经 GitHub 合并接口成功 squash 至 2dff0e4b，验收 head f6934742 与合并 tree 一致；提示分支重放至新基线。此前 OPEN 为历史状态。

## 本地残余控件与 CSS 审查（2026-11-26）

详见[本地控件归属审计](local-ui-ownership-audit.md)：以 0161abbd 为基线的 71 处原生按钮、475 处外部 Element Plus 引用逐项分类；三处普通业务按钮、Manifest 两个选择器、OCR 文本域和两个输入字段改用统一底层，保留内容按钮及文件输入。共享图标令牌、设置滑块和行内命令样式，以及授权浏览查询反馈采用明确归属。不将所有 475 处引用视为冲突，不将剩余专用 FA 视为重复基础控件。实际验收边界见工程交接。

## 视觉令牌及图表归属最终补充（2026-11-26）

表面及交替行归 data-display.css 和 tokens.css；状态与指标语义前景归 tones.css，各组件仅消费 ink 令牌；图表色值解析、坐标与提示归 chart-theme.ts，图表实例与同配置交互保留归 useMrrChart.ts。仅业务字段换行和批量选中反馈保留页面级样式。不要新增重复的 Element Plus 暗色覆盖、逐页硬编码颜色或全局重置表格。

## 普通业务筛选归属补充（2026-11-26）

MrrFilterBar 统一拥有字段流式/网格布局、容器响应式、语义宽度、动作分组与高级区域结构；MrrFilterActionButton 统一查询/重置反馈，MrrDataTablePanel 继续拥有表格及分页。页面拥有字段值、权限、请求、已应用快照和业务操作；不复制普通筛选尺寸/间距。动态数据库筛选保留带标签网格，专用影像身份查询/侧栏及即时筛选保留专业布局。
