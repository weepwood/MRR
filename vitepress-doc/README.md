# MRR 文档系统

MRR 使用 VitePress 同时构建两套文档：

- **用户手册**：面向普通用户和管理员，部署到 `/docs/`；
- **内部文档**：面向开发、测试、数据库、部署和运维人员，部署到 `/docs/internal/`。

两套站点独立构建和搜索，避免配置、架构、数据库和运维内容进入普通用户的搜索索引。站点标题中的产品版本从仓库根目录 `VERSION` 自动读取。

## 目录结构

```text
vitepress-doc/
├── .vitepress/
│   ├── config.user.mts          # 用户手册配置
│   ├── config.full.mts          # 内部完整站点配置
│   └── theme/                   # 主题和样式
├── user-guide/                  # 用户手册正文
├── internal/                    # 内部工程文档
├── getting-started/             # 安装与基础配置
├── public/                      # Logo、图片等静态资源
├── scripts/
│   ├── run-docs.mjs             # 双站点启动/构建入口
│   └── generate-git-changelog.mjs
├── package.json
└── README.md
```

`ai-generation/` 等历史自动生成目录不属于当前内部文档导航，并在完整站点配置中排除。新增正式文档应优先放入 `user-guide/`、`internal/` 或 `getting-started/`。

## 环境要求

- Node.js `^20.19.0` 或 `>=22.12.0`；
- npm；
- 仓库根目录必须存在 `VERSION`；
- 生成 Git 更新记录时需要可用的 Git 历史。

## 安装依赖

在 `vitepress-doc` 目录执行：

```bash
npm ci
```

开发过程中依赖未锁定或锁文件需要更新时才使用 `npm install`。CI 和发布构建优先使用 `npm ci`。

## 启动用户手册

```bash
npm run docs:dev:user
```

用户手册使用：

- 配置：`.vitepress/config.user.mts`；
- 源目录：`user-guide/`；
- 基础路径：`/docs/`；
- 构建目录：`.vitepress/dist-user`。

## 启动内部文档

```bash
npm run docs:dev:internal
```

内部站点使用：

- 配置：`.vitepress/config.full.mts`；
- 源目录：`vitepress-doc/`；
- 包含 `internal/`、`user-guide/` 和 `getting-started/`；
- 构建目录由运行脚本和 VitePress 配置决定。

开发命令支持继续传递端口参数：

```bash
npm run docs:dev:user -- --port 5174
npm run docs:dev:internal -- --port 5175
```

Windows 出现 `listen EACCES` 时，通常是端口被占用、被系统保留或安全软件拦截。更换端口后重试，不要直接关闭系统安全策略。

## 构建

构建两套站点：

```bash
npm run docs:build
```

单独构建：

```bash
npm run docs:build:user
npm run docs:build:internal
```

构建前运行脚本会刷新 Git 更新记录。`ignoreDeadLinks` 已关闭，死链会导致构建失败。

## 预览

```bash
npm run docs:preview:user
npm run docs:preview:internal
```

预览的是静态构建产物，不等同于 MRR 正式部署中的登录和文档 Cookie 保护。

## 更新日志

生成当前分支的 Git 更新记录：

```bash
npm run docs:changelog
```

测试生成脚本：

```bash
npm run docs:changelog:test
```

自动更新记录按第一父级提交历史生成，适合追踪合并后的主线变化。面向发布者的重要新增、修复和限制仍需人工维护根目录 `CHANGELOG.md` 和 `user-guide/release-notes.md`。

## 文档分层

### 用户手册

写入 `user-guide/`，只描述用户可以看到和执行的操作：

- 登录、注册、审核和密码；
- 患者、记录、统计和装箱；
- 影像档案袋、ZIP/PDF 导出；
- OSS 迁移和文件浏览；
- 系统设置、权限、日志和常见问题。

不要在用户手册中写入生产密钥、数据库内部结构、真实内网地址或破坏性运维命令。

### 内部文档

写入 `internal/`，记录：

- 架构和模块边界；
- 配置与环境变量；
- 数据库和迁移；
- 开发、测试和发布流程；
- Windows 部署、Nginx、监控和故障恢复；
- 安全边界和外部系统接入。

### 实时 API

接口字段、请求和响应模型优先维护在 Springdoc/OpenAPI 注解中。VitePress 只记录认证方式、接口分组、权限和接入流程，避免复制整套容易过期的接口清单。

## 新增文档

1. 确定读者和目录；
2. 创建 Markdown 文件；
3. 更新 `config.user.mts` 或 `config.full.mts` 的导航；
4. 在相关页面增加交叉链接；
5. 更新发布说明或 README；
6. 执行 `npm run docs:build`；
7. 检查用户站点没有包含内部资料。

完整规范见 [内部文档维护规范](./internal/documentation.md)。

## Markdown 约定

- 页面只使用一个一级标题；
- 主要章节使用二级标题；
- 命令注明操作系统和执行目录；
- 风险操作使用警告块；
- 示例数据必须是虚构数据；
- 不复制真实身份证号、患者姓名、Token、Cookie、签名 URL 或密钥；
- Mermaid 主要用于内部架构和流程图；
- 图片放在 `public/` 下并使用与部署基础路径兼容的地址。

示例：

```markdown
# 页面标题

> 适用版本、读者或风险说明。

## 操作步骤

1. 第一步；
2. 第二步。

::: warning 风险
此操作会修改生产数据，必须先备份。
:::
```

## 构建前检查

```bash
npm ci
npm run test
npm run docs:build
```

人工检查：

- 用户和内部导航是否完整；
- 新增页面能否被搜索；
- 相对链接和静态资源是否正确；
- 用户站点是否泄漏内部内容；
- 版本号是否与根 `VERSION` 一致；
- 更新说明是否只描述已进入当前分支的功能。

## 正式部署

文档通常由 MRR 发布流程构建后交给 Nginx 托管，并通过后端签发的短期 HttpOnly Cookie 控制访问：

- 用户手册：登录用户；
- 内部文档：管理员或 `system:read`；
- 实时 API：管理员或 `system:read`。

不要把内部站点单独部署到无认证的公开静态托管平台。正式部署步骤见 [Windows Server 部署](./internal/windows-deployment.md) 和 [发布流程](./internal/release.md)。