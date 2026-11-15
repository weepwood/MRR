# 共享控件设计与动效验收

Issue #576，风险 P2。基线 origin/main@224e7865，分支 codex/control-interaction-polish，worktree `D:\Project\CodexWorkTress\control-interaction-polish\MRR`，负责人 Codex，状态待合并。

## 调查与边界

复用 main.ts → globals.css/form-controls.css、motion.css、FaButton 与 isMotionEnabled。原共享动效只部分尊重系统减少动态效果，应用关闭未覆盖 Element 按压位移。FaButton 需要可辨认的焦点与加载语义；共享确认动效存在超出最终尺寸的弹跳。

仅统一共享控件反馈与可访问动效。保留现有主题/字体/业务内容；不依赖开放的导航视觉 PR #568 或组件目录 PR #570。不改请求、权限、后端、数据库与部署。控件配色继续用现有语义令牌，默认/危险按钮前景在明暗主题均用既有白色前景，避免暗色主按钮黑字蓝底。

## 实现与验证

- FaButton 提供独立焦点描边、aria-busy 与加载禁用；按压只改变明度，保留调用方的结构 transform 和 rounded-full。输入/箭头使用原 120/180ms 节奏；状态确认缩放为 0.96→1，无超调。
- isMotionEnabled 实时同时读取应用开关与系统偏好，无第二套 Store/持久化。系统偏好与应用关闭路径将动画缩至1ms，保留 animationend，防止抽屉关闭/搜索聚焦生命周期丢失；展开方向、颜色与焦点本身保留。
- RED：新增偏好单测在原实现上系统减少动态效果用例失败（expected true to be false）。修复后偏好3项、现有settings15项共18项通过。
- Playwright 定向6项通过：键盘Enter/Space重复操作、焦点、加载/禁用；正常/reduced/off三种偏好下按钮按压、结构变换不被覆盖、下拉选择以及不销毁FaDrawer重复打开/关闭回调；明暗主题和390px无横向溢出。
- 合成夹具位于e2e，复用真实组件、settings主题与生产reset→Uno→全局覆盖顺序，不加入生产路由。截图仅证明控件局部，不代表全站验收。
- 独立审查在真实/users复现并推动修复了抽屉关闭和按钮结构transform覆盖问题；修复后独立复测三种偏好下设置抽屉各重复开关两次、导航搜索聚焦、390px侧栏按钮旋转均通过。
- frozen install、定向ESLint/Stylelint、生产构建含类型检查、Chrome86静态门禁通过。静态门禁现有容器查询/Level4选择器/color-mix警告仍保留，没有新增依赖或忽略规则。

真实Chrome86、医院终端与性能帧率未验收；不声称全站全部动画已经统一。关闭路径1ms是生命周期完成手段，不承诺绝对零帧。回滚为整体revert本PR并重建前端，无数据迁移；现场回滚包未验收。
