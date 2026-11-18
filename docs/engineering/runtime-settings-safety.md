# 运行参数确认与异步状态保护

Issue #590，风险 P1：运行参数包含登录限流、会话及日志留存。
分支 `codex/runtime-settings-safety`，工作区 `D:/Project/CodexWorkTress/runtime-settings-safety/MRR`，负责人 Codex，状态：待合并。

## 问题与调用链

`UnifiedSettingsPage.handleRuntimeSave → RuntimeSettings.saveSettings → api/modules/settings → 统一 Axios → 后端系统设置服务`。
原实现确认后才组装 payload，并在确认后设置 saving；确认期间可重复进入，实际提交也可能不再是确认时的编辑。
恢复覆盖值没有统一写入守卫，加载响应没有激活代次隔离；设置页为 KeepAlive 页面，离开后旧确认仍可能提交。
保存后加载自行吞错，外层仍返回 true，父侧栏追加“已保存”。初次加载失败也会被父侧栏当作“已同步”。

## 最小方案及文件范围

1. `RuntimeSettings.vue`：确认前冻结完整 payload、敏感项名称和数量；保存、恢复、加载及填默认互斥。输入与操作按钮复用已有 loading/saving 状态禁用。
2. 同组件严格读取：先在局部构造完整 schema、覆盖值、基线和控件值，全部成功且仍属于当前激活代次才落地。错误保留旧编辑和最后已核对基线。
3. 同组件生命周期：使用局部激活代次处理 KeepAlive 离页和真实销毁；旧确认不得写入，旧响应不得回填；已发写入保持同实例 busy 直至请求结束。
4. `UnifiedSettingsPage.vue`：仅扩展运行参数面板的 `canSave/statusLabel/synced` ref 契约，保存按钮和同步提示由真实读取状态驱动。
5. 定向 Vitest 和真实设置路由的 Mock Playwright 验证确认快照、取消、防重、离页、失败及 GET 核对恢复。

继续使用现有设置 API、权限、审计、值域与后写覆盖语义。不改数据库、Flyway、后端配置或其他设置分组，不新增 Store、网络客户端、全局任务平台或通用状态框架。

## 行为与边界

- 首次读取或重新读取失败：保留已有页面数据，标记待核对，禁止 PUT/DELETE，只允许重新读取。
- PUT/DELETE 未知结果：服务器可能已应用，禁止自动或直接重复写入；人工 GET 核对成功后恢复编辑。
- 写入成功但回读失败：明确说明已写入、尚未核对；保存方法返回 false，父侧栏不能显示“已保存”。
- 单项恢复确认固定 key、名称与默认说明；全量回读会丢弃其他草稿，确认框对此明确告知。
- 非敏感项单独提交保留既有无需二次确认的行为；所有提交均从调用开始互斥。
- 缓存切页使旧操作失效，但不能撤回服务器已收到的写入；同实例在途 guard 直至结束。真实卸载后的新实例、其他标签页及其他管理员不共享全局锁，后端权限和后写覆盖语义保持。
- 现有 `/settings/schema`、PUT 和 DELETE 的权限仍由后端负责；Mock 测试不证明 JWT 或医院账号验收。

## 验证记录

- RED：原实现新增 8 项 Vitest 全部失败，实际复现快照漂移、确认重入、加载互斥、未知结果、回读误报以及缓存离页。
- GREEN：`pnpm exec vitest run src/views/settings/components/runtime-settings-safety.test.ts`，扩展后 17 项通过。
- `pnpm install --frozen-lockfile`、改动源 ESLint/Stylelint、`pnpm build`（含 vue-tsc）、`pnpm test:compat:chrome86` 通过。兼容检查仍提示既有依赖 CSS 的降级边界。
- 独立审查直接读取 Issue、diff、调用链和测试，首轮复跑 8 项成功；最终补充边界和浏览器证据在 PR 中记录。
- 本轮只启动前端 Mock 验证，不启动应用后端、业务 PostgreSQL 或 Docker。真实 Chrome86/医院终端验收仍由 #350 跟踪。

## 回滚与待合并组合

代码使用 `git revert` 本 PR squash 提交，无 schema 或医疗数据恢复步骤。
此任务从 main 独立开始；待合并 #589 也修改 RuntimeSettings，后合并时须同时保留其目录路由设置排除逻辑和本次异步保护，不能以整文件覆盖解决冲突。


最终独立复核复跑 17 项单测通过，未发现剩余代码阻断。
浏览器执行 `pnpm exec playwright test e2e/runtime-settings-safety.spec.ts --config .mrr-dev/runtime-playwright.config.ts --project chromium --workers 1`，4 项功能用例及 1 项预热通过；关闭 demo、使用既有合成会话，独立端口仅调整 Origin。
验证真实数字输入在忙碌时只读、核对成功后恢复编辑，确认取消无 PUT、成功仅一次 PUT、503 未知结果及写入后回读失败均只允许 GET 核对，首次失败父侧也不误报同步。

浏览器发现当前 Element Plus InputNumber 仅挂载时写入 `aria-disabled`，动态解除禁用后读屏状态可能滞留。数字控件的临时忙碌保护使用 `readonly`，组件增减/方向键入口也检查只读；需要重启的项继续使用稳定 disabled。此项修复经过依赖源码及独立审查确认，不升级依赖或操作控件 DOM。
