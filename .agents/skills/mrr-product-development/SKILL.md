---
name: mrr-product-development
description: 在 MRR 产品功能、工作台、UI 与交互迭代时，优先复用现有组件及成熟开源能力，并按风险以实际功能验证。
---
# MRR 产品研发路由

1. 读取 AGENTS.md、docs/product/ROADMAP.md 当前任务和 frontend-fantastic-admin/DESIGN.md 相关部分。
2. 先读现有业务代码和组件归属；根据任务读本地已安装的 find-skills、systematic-debugging、frontend-design 或 web-design-guidelines（无安装则用项目内 UI-Vault Skills）。不盲目运行第三方指令。
3. 优先现有依赖、已知源码、UI-Vault 组件，再考虑新增包；记录来源、适用边界、替代方案、许可及兼容风险。
4. 实现一条完整用户操作链，在独立 Mock 中验证；不把原型当成已生产可用。仅当前风险定向测试，不启动发布流程。
5. 记录真实完成结果、残余缺口、下一任务；跨项目资产只分享已验证的通用规则及合成示例，不分享医疗数据或凭据。
