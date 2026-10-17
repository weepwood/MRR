# OCR 实施与验证记录

日期：2026-10-18。关联变更提案 [#342](https://github.com/weepwood/MRR/issues/342)，风险 P1。
工程实现已形成分阶段草稿 PR，均未合并。ADR 仍为 Proposed，独立规格审查和代码审查待完成。

## 交付范围

| 阶段 | PR | 主要内容 |
| --- | --- | --- |
| 提案与预检 | [#343](https://github.com/weepwood/MRR/pull/343) | ADR、范围与只读数据质量脚本 |
| OCR 闭环 | [#344](https://github.com/weepwood/MRR/pull/344) | 白名单离线进程、持久化队列、版本结果、文字抽屉 |
| 分类与审核 | [#345](https://github.com/weepwood/MRR/pull/345) | 规则发布、建议审核、统一类型事务审计 |
| 有界历史批次 | [#346](https://github.com/weepwood/MRR/pull/346) | 范围预览、游标、租约、夜间资源门禁、管理页 |
| 自动归类与回退 | [#347](https://github.com/weepwood/MRR/pull/347) | 独立证据门禁、真实授权人复核、条件回退 |
| 离线交付与回归 | [#348](https://github.com/weepwood/MRR/pull/348) | 运行时打包、校验、存储读取超时、集成测试隔离及浏览器回归 |

PR 依次堆叠，按顺序审查、合并并调整后续基线。每个阶段独立提交，不改写已公开历史。
代码、迁移和前端随应用发布；OCR 模型与 Python 为独立不可变离线包，不提交大型二进制。
默认关闭 OCR/自动落库，自动允许类别为空；没有真实模型或验收报告时保持关闭。

## 已执行的验证

使用 JDK21、Maven3.9.9、PostgreSQL16 Testcontainers、Windows 本机 Python3.12.14。
构建和测试使用合成数据，不连接生产数据库、不处理真实患者图片。命令从仓库根目录运行，
Maven/前端命令分别在 backend-repo/frontend-fantastic-admin 下运行。

| 命令或检查 | 实际结果 |
| --- | --- |
| mvn -B -ntp verify | 770 单元测试、50 PostgreSQL 集成测试，失败0、跳过0，BUILD SUCCESS |
| Flyway PostgreSQL16 新库迁移 | 验证40条迁移，完成至 V20261018040000，包含非事务并发索引 |
| pnpm test:run | 58文件、343测试通过 |
| pnpm build / pnpm lint:tsc | 生产构建与类型检查通过 |
| pnpm test:compat:chrome86 | 构建兼容检查通过；依赖CSS的装饰降级提示保留，未运行真实Chrome86 |
| pnpm exec playwright test e2e/ocr-workflow.spec.ts --project=chromium --workers=1 | 3通过（含预热）；合成Mock端点验证范围绑定、自动授权禁用、单页文字、审核分页、关闭重开恢复 |
| Python unittest discover -s backend-repo/ocr-runtime -p test_*.py -v | 8测试通过，覆盖路径边界、校验值、独立样本去重/分组及CER |
| Python unittest discover -s scripts/tests -p test_*.py -v | 78测试通过 |
| Python scripts/test_inventory.py --fail-on-focused | 通过，无.only/@Disabled |
| Python scripts/release_baseline.py validate | 通过，v0.8.0，登记最高迁移V20261018040000 |
| governance_guard.py 对六个实际PR正文及各自base/head检查 | 全部通过，阻塞0；跨工程域/测试范围警告留给独立审查 |
| 新增PS脚本由Windows PowerShell5.1解析、执行ocr-doctor.ps1 | 通过，OFFLINE_SMOKE_PASSED |
| ESLint/Stylelint针对修改前端文件，git diff --check | 通过 |

测试覆盖权限拒绝、账号撤权、进程崩溃/超时/超量、源读取阻塞、重复任务、领取与游标恢复、
OCR结果版本、规则分类、自动与人工并发保护、审计失败事务回滚、后续人工修改不被回退覆盖。
新增非事务集成测试必须清理自己的随机合成病案，避免共享测试容器的数据污染既有断言。

## 离线运行时证据

本机真实 RapidOCR + ONNX Runtime CPU 使用显式中文 PP-OCRv5 mobile 模型，
识别合成中文“手术记录”成功，启动含推理约1.8秒。worker 禁止socket出站，不允许自动下载。
可重定位离线包经逐文件SHA-256检查、复制后的解释器识别合成英文页面通过；两次冒烟约3.7秒和1.5秒。
该耗时仅是开发机冒烟，不是历史任务吞吐基准，也不证明真实病历精度。

本地预览包 backend-repo/target/ocr offline preview.zip（约116MiB，Git忽略）：

```text
ZIP SHA256: 4b14e8e1404a4f1a5800e191c62c7abb99d6d13380311a97a8790fd5467b0ad9
模型清单 SHA256: f191b2d5e3ad6a1f719c01637c7dc12b62659d157c0573e88c48750830c0bde1
```

这是当前本地产物的校验值；重新构建必须使用新版本目录并重新登记，不作为通用生产配置。
部署流程见 [Windows交付说明](ocr-windows-release.md)。

## 尚未取得的上线证据

- #142 的生产 archive_id 数据质量统计、异常归属清单及修复确认。
- 院内至少2000页、按病案分离的独立验收集，600个自动候选零误分及类别准入证据。
- 目标Windows Server、实际本地/NAS/Nginx/OSS读取、断网及真实Chrome86交互验证。
- 正常调阅P95基线、CPU/内存/磁盘试点，1000/10000页持续任务和完成时间估算。
- 生产大小数据库的迁移耗时、索引查询计划、并发索引失败恢复及真实已有库升级演练。
- 独立规格审查、代码审查及各PR的远端governance-gate；本地测试不能代替审查。

因此本交付可供代码审查与院内试点准备，不宣称已满足生产自动归类上线条件。
运行关闭保留OCR事实和审计；数据回退只撤销满足批次/最后操作/类型版本条件的自动变更。
