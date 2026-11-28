# MRR Design Vocabulary R4 — 独立研究

仅开发期使用，不接入生产路由或构建入口；不使用真实患者资料，不发起医疗 API 请求。

## 运行

在本目录所属前端项目中启动独立 Mock Vite：`pnpm exec vite --mode mock --host 127.0.0.1 --port 19381 --strictPort`。如果该端口已有本设计工作区的进程，直接复用，不停止其他工作区服务。

访问 `http://127.0.0.1:19381/experiments/design-vocabulary/index.html`。

- `node --test experiments/design-vocabulary/model.test.mjs`：列宽预算、物理弹簧解析解与边界。
- `node experiments/design-vocabulary/browser-check.mjs`：真实 Chromium，交互、四主题四宽度、局部对比度及焦点。可使用 `LAB_BASE_URL` 和 `LAB_OUTPUT` 指定独立服务及输出目录。

## 迁移边界

复用项目现有令牌、主题函数及原生 WAAPI FLIP（不恢复 V1.2 已清理的 GSAP 依赖）；实验 CSS 和 DOM 不作为第二套生产组件库。FLIP 结束状态验证不等于视觉速度连续；弹簧的数学状态连续不等于主线程每帧无卡顿。表格权重、缩放及指标布局均需结合实际 V1.2 组件和医疗操作验收。Chrome 86 实际运行未验证。

## R4 工程化与 V1.2 对照

- `node --test experiments/design-vocabulary/{model,motion}.test.mjs`：新增长帧、事件间隔速度继承、取消和特性回退。
- `node experiments/design-vocabulary/motion-browser-check.mjs`：连续反向时几何测量、减少动效动态切换和 pagehide 清理；`LAB_BASE_URL` 默认指向隔离集成端口 19382。
- `node experiments/design-vocabulary/actual-measure.mjs`：仅对已启动的 V1.2 独立 Mock 实例测量记录表与影像工作台；可用 `LAB_AUTH` 指定合成会话文件、`LAB_BASE_URL` 指定端口、`LAB_OUTPUT` 指定截图目录。不能用于生产数据。
- 真实触控补丁与对应 E2E 留在 R4 隔离集成 worktree，未覆盖并行分支。
