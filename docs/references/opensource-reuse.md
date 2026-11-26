# 开源复用索引（2026-11-26）

以下为已查阅官方资料的第一批研究，不是全部源码审计或安装许可审批。

| 能力 | 来源 | MRR 决策 |
| --- | --- | --- |
| 固定高虚拟列表 | [VueUse useVirtualList](https://vueuse.org/core/usevirtuallist/)；本地 14.3.x 已安装源码 | 直接复用；大影像列表原型，固定行高与滚动偏移必须一致；图片比例不固定的网格暂不迁移 |
| 高分辨率图片 | [OpenSeadragon](https://openseadragon.github.io/examples/tilesource-image/)；[仓库](https://github.com/openseadragon/openseadragon) New BSD | 候选，支持普通图片；先比对现有 Element Plus ImageViewer。普通图片不是自动分块，访问控制、同源、水印、资源回收与旧终端须验证；不直接联网调图 |
| 医疗阅读工作区 | [OHIF Viewer](https://docs.ohif.org/user-guide/viewer/) / [Toolbar](https://docs.ohif.org/user-guide/viewer/toolbar/) | 只参考工作台工具与视口组织；MRR 扫描图像不等于 DICOM，不引入整套引擎 |
| 虚拟表格 | [Element Plus Table V2](https://element-plus.org/en-US/component/table-v2.html) | 已有依赖，可作大量已加载记录原型；不替换普通后端分页表格，V2 仍需逐项兼容与功能对照 |
| 后台框架 | [Fantastic-admin](https://github.com/fantastic-admin/basic) | MRR 已有基础框架；研究其 AGENTS 与组件范式，不全量升级/复制主干 |
| 关系图 | [ECharts option](https://echarts.apache.org/en/option.html) / [dataset](https://apache.github.io/echarts-handbook/en/concepts/dataset/) | 优先现有 ECharts graph，先确定业务关系与来源；不把缺失关系画成已确认连接 |
| 可编辑流程 | [Vue Flow](https://vueflow.dev/) MIT | 仅当需要节点编辑或明确关系交互时评估，不为只读图增加依赖 |
| 设计资产 | [UI-Vault](https://github.com/weepwood/UI-Vault) a0def2b 本地 | 复用原始设计 Skills 与既有 Token 映射；禁止跨项目外传病案数据 |

新增依赖前核对实际目标版本、许可、维护、包体、旧浏览器、无障碍、隐私和清理成本；本轮不增加 package.json 依赖。
