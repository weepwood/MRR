# MRR × 暖色扁平风格配色方案

## 来源与目标

配色界面名称为「暖色扁平风格」，内部配置标识继续使用 `gr8r` 以兼容已有设置。原始色值参考自 [weepwood/gr8r-studio-ui](https://github.com/weepwood/gr8r-studio-ui/blob/main/src/tokens.css) v0.2.0 提取明暗主题色板，作为 MRR 现有「MRR 经典」之外的一个**可切换的整体配色方案**。

不仅替换按钮主色，也同步替换页面背景、双侧栏、顶栏、卡片、弹窗、输入、文字、分割线和状态色。保留现有组件、布局、圆角和业务功能，不移植另一个项目的组件代码。

## 关键视觉令牌

| 角色 | 明亮模式 | 暗黑模式 |
| --- | --- | --- |
| 页面画布 | #FAF9F7 | #1B1B1A |
| 侧栏 | #F3F2EF | #171716 |
| 卡片/弹层 | #FFFFFF | #222220 |
| 柔和容器 | #F7F6F3 | #282826 |
| 抬高表面 | #F0EFEB | #32312E |
| 分割线 | #E7E5DF | #32312E |
| 文本 | #242320 | #EAE8E3 |
| 次级文字 | #65625C | #B5B1A9 |
| 辅助文字 | #6E6A63 | #A09C93 |
| 品牌强调 | #5362CE | #9DA8F7 |
| 成功 | #226C4F | #76C3A0 |
| 警告 | #886111 | #E6B65B |
| 危险 | #B63E38 | #EC928C |
| 信息 | #286F9E | #88BBDF |

完整色源与桥接规则见 frontend-fantastic-admin/src/utils/theme-palette.ts。

## 用户使用

「系统设置 → 应用配置 → 主题与页面样式 → 整体配色方案」中选择：

- **MRR 经典**：完全恢复原有 MRR 页面色彩及自行选择的主题主色；
- **暖色扁平风格**：暖灰中性色 + 固定配套靛蓝主色。选择明亮/暗黑/跟随系统时自动切换对应主色。

切换为暖色扁平风格不修改原有 themeColor，返回 MRR 经典继续使用此前自定义的主色。

## 实现与兼容性

- app.themePalette 取值 mrr | gr8r；默认 mrr，兼容历史设置。
- 原有 app.colorScheme 继续管理明亮/暗黑，app.themeColor 继续记录 MRR 原主色。
- theme-palette.ts 生成完整的 shadcn / UnoCSS / MRR 语义变量映射；settings.ts 内只调用原有主题主色写入逻辑，避免双来源争抢。
- 使用 <html> inline style 写入变量以兼容 UnoCSS Preflight 与页面样式；回切时按管理清单逐项删除而非写默认颜色，确保 :root 和 :root.dark 原值恢复。
- Element Plus 状态色阶预先计算为静态十六进制，确保最低兼容浏览器不依赖 color-mix()；不改变布局、缓存、权限和后端接口。
- 选择会存入浏览器 app-settings，且复用现有 adminAppSettings 白名单：具备 system:manage 权限的账号可以同步全站，无权限则仅本机生效。

## 测试

- src/utils/theme-palette.test.ts：色值、HSL/语义映射、Chrome 86 静态回退、对比度、主题清除。
- src/store/modules/__tests__/settings.test.ts：明暗切换、恢复 MRR 主色、全站同步字段。
- e2e/appearance-palette.spec.ts：设置选择、明暗切换、持久化与恢复经典配色。

此实施仅作样式体系映射；完整浏览器视觉与辅助技术验收仍需要实际运行检查。
