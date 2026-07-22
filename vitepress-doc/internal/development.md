# 开发流程

## 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 22，或满足 `^20.19.0 || >=22.12.0`
- pnpm 10.33.0
- PostgreSQL 16+
- Git

## 分支

```bash
git fetch origin
git switch main
git pull --ff-only
git checkout -b feat/short-description-YYYYMMDD
```

常用前缀：`feat/`、`fix/`、`refactor/`、`docs/`、`chore/`。

## 本地数据库与后端

```powershell
Copy-Item backend-repo/src/main/resources/application-local.template.properties `
  backend-repo/src/main/resources/application-local.properties

$env:JWT_SECRET_KEY = '本地签名密钥'
$env:AES_SECRET_KEY = '本地 AES 密钥'

cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

至少配置 PostgreSQL、AES、JWT、图片路径和图片服务地址。新数据库由 V0 基线初始化。

## 前端开发

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

仅浏览界面或执行无后端测试时：

```bash
pnpm dev:mock
```

Mock 模式不能替代认证、数据库、图片服务和 OSS 的真实联调。

## 文档开发

```bash
cd vitepress-doc
npm install
npm run docs:dev:user
npm run docs:dev:internal
```

同时运行：

```bash
npm run docs:dev:user -- --port 5210
npm run docs:dev:internal -- --port 5310
```

启动脚本默认绑定 `127.0.0.1`，遇到 Windows `EACCES` 或端口占用时自动向后查找可用端口。可检查排除范围：

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

## 前端检查

```bash
pnpm lint:tsc
pnpm lint:eslint
pnpm lint:stylelint
pnpm test:run
pnpm build
pnpm build:mock
pnpm test:e2e
```

最低要求：

1. TypeScript 检查通过。
2. 修改文件的 ESLint 与 Stylelint 通过。
3. 相关 Vitest 通过。
4. 正式构建通过。
5. 交互变更执行 Playwright 或人工回归。

## 后端检查

```bash
mvn test
mvn package
```

数据库和权限变更应增加：

- V0 新库初始化测试。
- V0 之后增量迁移测试。
- 边界病案号 `9999999` 与 `10000000`。
- 仅病案号、仅上架号和成对查询。
- 权限允许与拒绝。
- 身份证令牌往返、随机 IV 和篡改失败。

## 文档检查

```bash
cd vitepress-doc
npm run docs:build
```

确认：

- 用户手册不泄露内部实现和敏感配置。
- 内部文档与当前代码、路由和 V0 基线一致。
- 不描述未实现功能。
- 链接有效。
- 代码块语言使用 Shiki 支持的 `text`、`properties`、`dotenv` 等。

## 提交规范

```text
feat(archive): add archive case switcher
fix(frontend): prevent modal layout shift
docs(internal): rewrite deployment guide
```

功能分支过程提交较多时，PR 建议 Squash and merge。

## PR 说明

至少包含背景、根因、主要变更、影响范围、数据库或配置变化、验证结果、未执行检查和部署注意事项。不要声称未实际运行的检查已通过。

## 开发约束

- 不引入 TailwindCSS，延续 UnoCSS、CSS 和设计令牌。
- V0 基线部署后不直接修改；数据库变化新增增量迁移。
- 不把密钥、医院内网地址或患者信息提交仓库。
- 新设置项必须有实际消费方。
- 新图表复用统一 ECharts 体系。
- 新弹窗验证滚动锁定、焦点和布局稳定性。
- 路由页面离开后清理全局监听、定时器和类名。