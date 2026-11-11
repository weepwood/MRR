# MRR 项目设计入口

核对日期：2026-11-11；主线基线：`bbaf95c4`。本文件提供设计任务的阅读入口，详细规则各有一个维护位置，不在根目录重复定义颜色、字体、尺寸或组件 API。

## 选择设计依据

先遵守 [AGENTS.md](AGENTS.md) 的阅读和交付流程，再按任务选择下列文档：

| 任务 | 主要依据 |
| --- | --- |
| 确认产品范围、核心流程和阶段状态 | [项目设计导航](docs/design/README.md)；产品规格提案与已实现能力分开记录 |
| 新增或调整页面 | [前端设计规范](frontend-fantastic-admin/DESIGN.md)，包括 [Layout](frontend-fantastic-admin/DESIGN.md#layout) 的页面蓝图与 [Components](frontend-fantastic-admin/DESIGN.md#components) 的交互细则 |
| 选择共享组件、组织请求与状态 | [前端工程规范](frontend-fantastic-admin/ENGINEERING.md)及设计规范中的组件来源 |
| 改变模块职责、接口、权限或数据边界 | [架构边界](ARCHITECTURE.md)、[ADR 索引](docs/adr/README.md)和相应风险提案 |
| 判定候选是否可在医院使用 | [医院副本验收包](docs/engineering/hospital-replica-acceptance.md)，按真实候选与适用环境记录证据 |

## 设计变更怎样落地

1. 写明操作者、要完成的任务、当前问题和受影响页面。
2. 找到现有组件、调用链、令牌和测试，说明最小修改及非目标。
3. 画清信息顺序、操作与加载/空/错/拒绝等状态；对照前端规范选择页面组合。
4. 按影响确定验证范围和独立审查，记录实际 SHA、结果、未测项与恢复方式。
5. 通过专属分支和 PR 交付；实现与对应设计规则一起更新。

设计稿、候选实现、已合入主线和现场验收是不同事实。未经确认的重设计方向留在隔离任务中，不静默替代现有规范，也不从文档更新推导出业务或权限变更。运行数值仍以源码和实际设置为依据，文档不得成为第二套主题配置。
