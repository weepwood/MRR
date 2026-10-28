# 工程化首批验证记录

日期：2026-10-28。提案：[#453](https://github.com/weepwood/MRR/issues/453)。初始调查与本机运行快照基线：af39d6af；提交前无冲突变基到最新main489abfbc，变基后完整工具测试207项通过。

## 实际执行结果

| 验证 | 实际结果 |
| --- | --- |
| Windows PowerShell5.1 `scripts/dev.ps1 doctor` | Python3.12、Java21、Node24、Corepack所选pnpm10.33与Docker通过 |
| Windows PowerShell5.1完整 `up -WebPort 0` | 冻结安装、Wrapper干净打包和原生Java/Vite启动成功；仅本机访问 |
| 原生Vite代理 `/proxy/api/v1/auth/me` | 无Token返回401 |
| 重复up、status、down两次 | up复用同一实例；down幂等，保留命名数据库卷 |
| 已占用18045端口的up | 返回退出码1；未接管端口；原先服务仍返回200 |
| `python -m scripts.smoke_dev` | 两次完整开发Compose构建/健康检查通过；直连及Nginx登录页200，无Token API401；所属容器、卷、网络及临时镜像清理成功 |
| 合成本地配置归档验证 | 独占创建无凭据fixture application-local.properties；实际执行Wrapper clean package -Dlocal.config.exclude=true -DskipTests；JAR无该成员；fixture已删除 |
| 包管理器守卫 | 声明版本通过；npm和其他pnpm版本拒绝；执行无临时npx下载 |
| 前端新增脚本ESLint | `corepack pnpm exec eslint scripts/require-package-manager.mjs`通过 |
| 仓库治理 | test_inventory --fail-on-focused、release_baseline validate、check_engineering_docs与git diff --check通过 |

首次原生安装在下载完成后停滞，已终止确认属于该次构建的进程树。原preinstall包含未固定的npx临时下载；改为本地守卫后，冻结安装实际完成（1分2.5秒），完整入口随后通过。构建子进程现在有超时和中断清理。

精简镜像首次Wrapper校验失败。已从Maven Central下载Maven3.10.0 ZIP，核对官方SHA512，再计算SHA256，确认现有固定值正确：1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff。缺少unzip时Wrapper会改下tar.gz；构建阶段补齐unzip后下载校验与构建成功，没有关闭校验。

安装Git hooks曾暴露子目录命令在根仓库执行的问题，已将hooks明确切换到前端目录，通过Corepack执行。实际提交时pre-commit和commit-msg均正常执行；新增mjs另行运行了ESLint。

## 独立审查

用户明确授权只读子代理；审查者使用全新上下文直接读取Issue、diff、架构、治理、脚本和测试，未修改源码。独立运行202项工具测试通过，并在Windows验证了目录权限初始化、显式宽权限移除、锁排他与释放后重用。

已解决的独立发现：已有显式ACE泄漏、0700首次目录的Set-Acl特权失败、陈旧锁回收竞态、Nginx配置未触发开发冒烟、本地配置需实际资源排除并防止复用旧target。采用仅DACL的Windows API、操作系统文件锁、精确CI输入、clean打包与JAR成员检查修复。

最终审查未发现阻塞性P1/P2；自动生成env.d.ts噪声已恢复。独立审查者没有重复完整Docker/native启停，以上实际链路结果由实现者执行。新增选择器回归防止正式候选验收替代原生开发Windows检查；变基后独立增量复核24项测试通过，范围30文件899增42删（文档补充前）。

## 边界与回滚

远端CI以PR当前提交实际结果为准；不将本机结果当成远端通过。不涉及业务代码或迁移变更，不代表医院Windows Server、真实存储或Chrome86现场验收。原先运行的服务及数据库未改动。

提交按工具链/构建、本地生命周期、CI接入拆分；回滚前执行开发down，再按依赖逆序revert对应提交。默认保留开发命名卷和本地密钥，不回退业务数据。
