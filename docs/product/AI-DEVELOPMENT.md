# 产品阶段 AI 研发方法

当前阶段从 503b2fb2 起恢复功能研发。优先用户任务闭环而非发布指标；保留现有安全、权限、审计、数据与旧终端核心降级契约。发布相关真实门禁仅后置，不取消。产品任务先写目标、证据、复用选择、非目标、风险、验收；普通 UI P2/P3 定向单测、类型和浏览器，P1 仍遵守原治理。多项相关功能集中实施，独立端口测试，不抢占9037。

## Skills 路由

项目内直接复用 UI-Vault a0def2b 四个原版 MIT Skills：design-extract、design-review、visual-refine、component-handoff（保留各自许可与来源）。mrr-product-development 仅为项目路由，不重造通用 Skills。全局已存在 find-skills、frontend-design、systematic-debugging、web-design-guidelines、brainstorming、writing-plans 等；根据需要读取实际 SKILL.md，不批量安装。通用规则与项目规则冲突时以明确用户需求及安全契约为准。web-design-guidelines 应同时读取其固定参考文件；不可擅自外传项目数据。

## 最小研发循环

问题/用户任务 → 现有能力检索 → 参考开源范式 → 真实功能实现 → Mock 截图与交互 → 记录复用决策 → 下一任务。不要把待办、静态调查或写好的测试等同于已完成。按任务风险验证，必要时补充失败测试；不重复无关历史测试。研发中不更新正式版本、不触发CI/制品/部署。
