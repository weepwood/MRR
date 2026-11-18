# 共享图表初始化的尺寸唤醒优化

风险 P2，负责人当前 Codex。分支 codex/chart-resize-initialization，专属 worktree D:/Project/CodexWorkTress/chart-resize-initialization/MRR。调查基线为本地 origin/main d66235c3；Git fetch 与 GitHub connector 当前连接失败，未确认远程最新状态，未创建远程 Issue/PR，不声称 CI 通过。

## 调查与最小计划

useMrrChart 默认 autoresize=true，已用 useResizeObserver；但 ensureChart 对零尺寸容器仍每32ms安排重试，观察器则只 resize 已有实例，不能负责初次初始化。这使隐藏容器保持轮询。32ms是代码事实，不是已测CPU占用。

先用合成DOM尺寸、真实Vue生命周期和模拟ECharts/观察器测试：默认零尺寸无重复定时器、尺寸恢复后正确初始化、尺寸变化复用实例、卸载不迟到初始化。再让自动适配模式依靠已有尺寸观察器唤醒；autoresize=false保留原初始化重试及手工resize契约。保持80ms防抖、主题观察、暂停和销毁边界，不建立新框架。

仅修改 useMrrChart、对应定向测试和本说明。请求、权限、审计、图表内容、渲染器注册和后端不变；不修改其他未合并PR，不重写大页面。检查类型、变更ESLint、生产构建及Chrome86静态，独立代理审查。具体执行结果以最终交付说明为准。

## 回滚和限制

独立revert本批提交并重建前端，无数据库或医疗数据影响。现代测试DOM不证明真实Chrome86或医院终端，网络恢复后需同步最新main、创建PR并执行当前提交CI。上一批菜单去涟漪修改保留在独立分支，不混入本批。

## 额外审查发现（本批未修复）

useCrudList 被 logs 和 audit-images 使用，loadData 没有请求版本校验。对本地原始TypeScript实现转译后，以两个可控Promise复现：新查询先返回、旧查询后返回，最终 query.keyword='new' 而 list=['old']。这是真实响应顺序问题，不是性能推断；旧finally也缺少请求归属检查。

该共享链路涉及审计列表，后续按P1独立处理：先有Issue/失败路径测试/独立审查和回滚，不混入本次图表优化。当前GitHub连接失败，无法登记远程Issue，故将证据先留在本记录；没有为完成局部工作绕过AGENTS的P1要求。下一工作包应测试搜索/翻页/重置的交错、loading、销毁及调用方错误状态，避免只在共享函数拦旧列表却仍留下调用方旧错误。

图表首批4项回归在旧实现2失败2通过；修复后另补真实80ms防抖与DOM替换，共6项，配合既有趋势/热力图测试定向验证。具体运行结果见交付说明；不把此小范围审查称为全前端审计完成。
