# VitePress 文档部署指南

## 本地开发

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run docs:dev
```

访问 `http://localhost:5173`

### 构建生产版本

```bash
npm run docs:build
```

构建产物位于 `.vitepress/dist` 目录。

### 预览构建结果

```bash
npm run docs:preview
```

## 部署选项

### GitHub Pages

1. 修改 `.vitepress/config.mts` 中的 `base` 配置:

```typescript
export default defineConfig({
  base: '/your-repo-name/',
  // ... 其他配置
})
```

2. 创建 GitHub Actions 工作流 `.github/workflows/deploy.yml`:

```yaml
name: Deploy VitePress site to Pages

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
      - name: Setup Pages
        uses: actions/configure-pages@v4
      - name: Install dependencies
        run: npm ci
      - name: Build with VitePress
        run: npm run docs:build
      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: .vitepress/dist

  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    needs: build
    runs-on: ubuntu-latest
    name: Deploy
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

### Netlify

1. 连接 GitHub 仓库
2. 配置构建设置:
   - Build command: `npm run docs:build`
   - Publish directory: `.vitepress/dist`
3. 部署

### Vercel

1. 导入 GitHub 仓库
2. 配置构建设置:
   - Framework Preset: VitePress
   - Build Command: `npm run docs:build`
   - Output Directory: `.vitepress/dist`
3. 部署

### Docker 部署

创建 `Dockerfile`:

```dockerfile
FROM node:20-alpine as builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run docs:build

FROM nginx:alpine
COPY --from=builder /app/.vitepress/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

创建 `nginx.conf`:

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

构建和运行:

```bash
docker build -t mrr-docs .
docker run -d -p 80:80 mrr-docs
```

## 环境变量

可以通过环境变量自定义配置:

```bash
# 设置基础 URL
VITEPRESS_BASE=/your-path/

# 设置站点标题
VITEPRESS_TITLE=MRR 文档
```

## 自定义域名

### GitHub Pages

1. 在仓库设置中添加自定义域名
2. 创建 `public/CNAME` 文件:

```
docs.your-domain.com
```

### Netlify/Vercel

在项目设置中添加自定义域名，平台会自动配置 DNS。

## 更新文档

1. 修改 Markdown 文件
2. 本地预览: `npm run docs:dev`
3. 提交更改: `git add . && git commit -m "Update docs"`
4. 推送到远程: `git push`
5. 自动部署 (如果配置了 CI/CD)

## 故障排查

### 构建失败

1. 检查 Node.js 版本 (需要 18+)
2. 清理依赖: `rm -rf node_modules package-lock.json && npm install`
3. 检查 Markdown 语法

### 路由问题

1. 确保所有链接使用正确的路径
2. 检查 `base` 配置是否正确
3. 使用相对路径引用静态资源

### 样式问题

1. 检查自定义 CSS 是否正确
2. 清理浏览器缓存
3. 检查主题配置

## 相关链接

- [VitePress 官方文档](https://vitepress.dev/)
- [VitePress 部署指南](https://vitepress.dev/guide/deploy)
- [GitHub Pages 文档](https://docs.github.com/zh/pages)
