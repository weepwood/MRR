# 前端字体首次加载优化

对应 [Issue #574](https://github.com/weepwood/MRR/issues/574)，风险 P2。基线 `origin/main@4e59857b`；分支 `codex/frontend-font-loading`；专属 worktree `D:\Project\CodexWorkTress\frontend-font-loading\MRR`；负责人本次 Codex，状态待合并。

## 瓶颈与修改范围

字体由 globals.css → fonts.css 在页面加载时声明，正文和控件使用400/500/700三个字重。原生产构建的登录页实际请求了三个完整 WOFF2，共13,019,608字节。该问题与前几轮未合并的 UI/轮询 PR 无依赖。

保留三个完整字体逐字节不变，按源码中的界面字符生成1129个编码点的子集。三个字重共404,116字节；CSS 中常用字符范围与其补集互斥，补集仍指向原完整字体。字体家族、字形、字重、font-display:swap 和离线资源路径策略保留。

只修改字体资源/声明、开发生成与校验工具、对应测试和文档，不改变 JS 业务、API、权限、数据库、Nginx 或部署架构。正常 pnpm build 消费已提交的资源，不安装或运行 Python 字体工具。

## 生产页面实测

Windows开发机、现代Chromium、新浏览器上下文、同一1440×900生产登录页。公共API仅返回合成内容，不连接真实后台；等待 document.fonts.ready 后读取 resource encodedBodySize。

| 字重 | 原完整字体字节 | 优化后首屏字节 |
| --- | ---: | ---: |
| Regular 400 | 4,272,648 | 133,712 |
| Medium 500 | 4,345,916 | 135,208 |
| Bold 700 | 4,401,044 | 135,196 |
| 合计 | 13,019,608 | 404,116 |

该页面字体首次请求量减少约96.9%，不代表整页加载时间或全站性能提高同等比例。完整字库仍随离线包交付，安装包不会因此减少13MB；动态文本包含子集以外的字符时，相关字重仍会下载完整字体。

## 更新与校验

仅更新字体资源时，在 frontend-fantastic-admin 下使用隔离开发环境：

```powershell
python -m venv "$env:TEMP\mrr-font-tools"
& "$env:TEMP\mrr-font-tools\Scripts\python.exe" -m pip install -r scripts/requirements-fonts.txt
& "$env:TEMP\mrr-font-tools\Scripts\python.exe" scripts/build-ui-fonts.py
& "$env:TEMP\mrr-font-tools\Scripts\python.exe" scripts/build-ui-fonts.py --check
python scripts/test_ui_fonts.py
```

源码字符采集范围为 src 的 vue/ts/json，包含合成Mock标签，排除测试、生成声明和图标数据；不读取患者文件、运行日志或环境配置。子集文件名带内容摘要，清单记录原文件/子集SHA-256。脚本只清理固定字体目录内旧的生成子集，不删除完整字体。生成和完整字形校验需要解码原字库，会比普通单测慢；普通前端构建不执行该步骤。

`--check` 验证三个字重的源码字符覆盖、CSS区间互斥、原字库全部编码点覆盖、原文件/子集摘要、子集每个编码点的轮廓和字宽/垂直度量，以及原字体嵌入的版权/授权元数据。新增界面文字无需先生成资源才能正常显示；更新子集用于提高命中率，校验会提示源码字符变化。

## 验证与限制

- RED：原构建的字体预算用例失败，实际13,019,608字节超过2MiB；原字库的生僻字基线通过。
- 浏览器用例 `e2e/frontend-font-loading.spec.ts`：常用文字和生僻字均按400/500/700实际渲染，CDP确认使用自定义字体。常规首屏不请求原完整文件；合成生僻字触发原本地字库。
- Python边界用例覆盖Unicode端点、空/完整范围、CSS格式解析与源码采集排除规则；字体工具以固定开发版本重建和校验。
- 两项生产浏览器用例、四项Python边界用例通过；完整字库/字形校验通过。第二次生成的所有子集文件名、摘要及清单与第一次完全相同。
- 生产构建（Vite 18.36s，含类型检查）、定向ESLint/Stylelint和Chrome86静态门禁通过；远端Actions按本PR实际结果记录。初次Stylelint发现生成列表缺少逗号空格，已修正生成器格式，无绕过或忽略规则。
- 独立上下文读取Issue、架构/规范、diff及全部生成资源，并用原主线二进制摘要、完整字体校验和生产浏览器确认，未发现阻断项；首屏真实渲染断言已按审查建议补齐。

真实Chrome86、医院终端、弱网时延及临床动态文本分布未验收。现代Chromium及静态门禁不能替代现场结果，也不保证所有动态页面都只请求子集。

回滚为整体revert本PR的squash提交并重建前端，恢复原三个完整字体声明；无数据迁移或业务数据恢复。回滚包重建和现场恢复未执行。
