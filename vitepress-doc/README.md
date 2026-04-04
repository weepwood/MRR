# MRR 文档系统

基于 VitePress 构建的现代化文档系统，用于统一管理 MRR 医疗影像记录管理系统的项目文档。

## 📁 文档结构

```
vitepress-doc/
├── .vitepress/              # VitePress 配置目录
│   ├── config.mts          # 主配置文件
│   └── theme/              # 主题定制
│       ├── index.ts        # 主题入口
│       └── custom.css      # 自定义样式
├── public/                  # 静态资源
│   └── logo.svg            # 站点 Logo
├── getting-started/         # 快速开始
│   ├── installation.md     # 安装指南
│   └── configuration.md    # 配置说明
├── ai-generation/           # 核心文档目录
│   ├── 项目概览/           # 项目介绍
│   ├── 系统架构/           # 架构设计
│   ├── 前端组件/           # 前端组件文档
│   ├── 后端API文档/        # API 文档
│   ├── 数据库设计/         # 数据库设计
│   ├── 开发指南/           # 开发规范
│   ├── 认证授权/           # 认证授权
│   ├── 日志审计与监控/     # 日志监控
│   ├── 部署运维/           # 部署运维
│   ├── guide/              # 使用指南
│   └── imgs/               # 文档图片
├── index.md                 # 首页
├── package.json            # 项目配置
└── DEPLOY.md               # 部署指南
```

## 🚀 快速开始

### 方式一：使用启动脚本（推荐）

**Windows:**
```bash
start-docs.bat
```

**Linux/macOS:**
```bash
chmod +x start-docs.sh
./start-docs.sh
```

### 方式二：手动启动

1. **进入文档目录**
```bash
cd vitepress-doc
```

2. **安装依赖**
```bash
npm install
```

3. **启动开发服务器**
```bash
npm run docs:dev
```

4. **访问文档**
```
http://localhost:5173
```

## 📖 文档内容

### 核心文档模块

| 模块 | 说明 | 路径 |
|------|------|------|
| **项目概览** | 项目介绍、技术架构、核心功能 | `/ai-generation/项目概览/` |
| **系统架构** | 前后端架构、数据架构、部署架构 | `/ai-generation/系统架构/` |
| **前端组件** | Vue 组件库、UI 设计规范 | `/ai-generation/前端组件/` |
| **后端 API** | RESTful API 文档、接口说明 | `/ai-generation/后端API文档/` |
| **数据库设计** | 表结构设计、索引优化 | `/ai-generation/数据库设计/` |
| **开发指南** | 编码规范、开发流程、测试策略 | `/ai-generation/开发指南/` |
| **认证授权** | JWT 认证、权限控制 | `/ai-generation/认证授权/` |
| **日志监控** | 日志管理、审计追踪、监控告警 | `/ai-generation/日志审计与监控/` |
| **部署运维** | 容器化部署、CI/CD、备份恢复 | `/ai-generation/部署运维/` |

### 快速开始指南

- [安装指南](vitepress-doc/getting-started/installation.md) - 系统安装和部署
- [配置说明](vitepress-doc/getting-started/configuration.md) - 详细配置参数

## 🛠️ 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器（热重载）
npm run docs:dev

# 构建生产版本
npm run docs:build

# 预览构建结果
npm run docs:preview
```

## 📝 文档编写指南

### 添加新文档

1. 在对应目录创建 Markdown 文件
2. 在 `.vitepress/config.mts` 中添加侧边栏配置
3. 文档会自动出现在侧边栏中

### 文档格式规范

```markdown
# 文档标题

> 文档简介

## 一级标题

### 二级标题

#### 三级标题

**粗体文本**
*斜体文本*

- 无序列表项 1
- 无序列表项 2

1. 有序列表项 1
2. 有序列表项 2

`行内代码`

```语言
代码块
```

[链接文本](链接地址)

![图片描述](图片路径)

::: tip 提示
提示内容
:::

::: warning 警告
警告内容
:::

::: danger 危险
危险内容
:::
```

### 图片资源

- 放置在 `vitepress-doc/public/` 目录下
- 使用绝对路径引用：`/图片路径/图片名.png`
- 示例：`![系统截图](/ai-generation/imgs/v0.0.9_imgs/登录界面.png)`

## 🎨 主题定制

### 自定义样式

编辑 `.vitepress/theme/custom.css` 文件：

```css
:root {
  --vp-c-brand-1: #3eaf7c;      /* 主题色 */
  --vp-c-brand-2: #42b883;      /* 主题色变体 */
  --vp-c-brand-3: #35a06c;      /* 主题色深色 */
  --vp-c-brand-soft: rgba(62, 175, 124, 0.14); /* 主题色透明 */
}
```

### 自定义配置

编辑 `.vitepress/config.mts` 文件：

```typescript
export default defineConfig({
  title: "你的标题",
  description: "你的描述",
  themeConfig: {
    nav: [...],      // 顶部导航
    sidebar: {...},  // 侧边栏配置
    footer: {...}    // 页脚配置
  }
})
```

## 📦 构建部署

### 构建静态网站

```bash
npm run docs:build
```

构建产物位于 `.vitepress/dist` 目录。

### 部署选项

详细部署步骤请参考 [DEPLOY.md](vitepress-doc/DEPLOY.md)

- **GitHub Pages** - 免费，适合开源项目
- **Netlify** - 自动部署，支持自定义域名
- **Vercel** - 全球 CDN，性能优秀
- **Docker** - 容器化部署，适合企业环境

## 🔍 搜索功能

VitePress 内置本地搜索功能，支持：

- 全文搜索
- 中文分词
- 实时搜索建议
- 快捷键支持（按 `/` 或 `Ctrl+K`）

## 📱 响应式设计

文档系统完全响应式，支持：

- 桌面端优化布局
- 平板端自适应
- 移动端友好界面
- 深色模式切换

## 🌐 国际化

当前支持中文（简体），如需添加其他语言：

1. 在 `config.mts` 中配置 `locales`
2. 创建对应语言的文档目录
3. 添加语言切换器

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目基于 MIT 许可证开源。

## 📞 联系方式

- **问题反馈**: [GitHub Issues](https://github.com/your-repo/mrr/issues)
- **功能建议**: [GitHub Discussions](https://github.com/your-repo/mrr/discussions)

---

**Made with ❤️ by MRR Team**
