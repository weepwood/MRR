# 产品研发第三批：异常样本可靠性

基线 8649f59b。范围：GOV-01/GOV-04 前端读取状态与筛选（P2，保留现有授权规则；不改变后端鉴权、事务、接口、数据库或部署）。复用 Element Plus、MrrFilterBar、AppError、既有 isSessionAccessDenied 与请求代次。仅使用合成 Mock。

## 实际问题与修复

- 首次读取失败：规则表不再回退到独立概览的 relationChecks，未成功取得时明确显示规则结果尚未取得。
- 并发读取：原 Promise.all 的普通失败可能掩盖随后返回的 403；改为 Promise.allSettled，两个结果都结算后优先处理访问拒绝，清除旧样本、质量摘要和筛选；普通刷新失败保留上次成功结果。继续使用原请求代次隔离。此改动不是权限变更或实时撤权机制。
- 无样本优先于无匹配，避免空结果受筛选条件影响；组合筛选继续沿用既有规则，不修改查询上限。
- 非目标：跨页工单上下文、独立实时复验、运维时段联动、全角色权限矩阵及生产环境验收。尚未覆盖访问拒绝先到而另一请求迟迟不完成、所有拒绝类型和所有并发代次排列。概览仍为独立授权读取。

## 验收

- RED Chromium 两个新用例均失败，分别复现未取得规则结果的状态错误及普通失败掩盖后续403。
- GREEN 初轮 Chromium 3/4，选择器内部输入被占位节点遮挡；改为实际 Element Plus 选择器外框点击，保留全部业务断言。WebKit 初轮因默认缓存缺少2388未执行；复用外置已有缓存。
- 最终独立9041 Mock，Chromium/WebKit 各4/4，合计8/8：组合筛选、规则定位与旧结果恢复、首次失败到空结果、并发失败后拒绝清理及恢复。
- vue-tsc -b、修改文件 ESLint、git diff --check 通过。未运行正式CI、真实数据库或全部E2E；未对真实Chrome86执行本轮专项。
- 证据：/Volumes/data/AI-Development/artifacts/mrr-product3-20261126/（red、green-initial、final）。日志 /tmp/mrr-product3-*.log。测试均为合成数据，不上传。
- 本地提交工具：Node 26 环境的 corepack 缺失、系统旧链接失效；仅本次提交用临时入口调用已安装且与 packageManager 一致的 pnpm 10.33.0，保留 lint-staged 与 commitlint 原有 hook，不修改共享 hook 或系统工具链。
- 回滚：撤销本轮独立提交即可，无迁移、无数据修复步骤。

下一任务：GOV-03 跨页上下文与服务端身份核对，OPS-02 时段证据关联。两者仍未实施。
