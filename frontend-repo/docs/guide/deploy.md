# 构建与部署

## 本地预览静态产物

```bash
npm run docs:build
npm run docs:preview
```

## 常见部署方式

1. 作为独立静态站点部署  
将 `docs/.vitepress/dist` 目录部署到 Nginx、对象存储静态托管或 CDN。

2. 作为主站子路径部署  
如果你希望挂载到 `/docs/`，建议在 VitePress 配置里增加 `base: '/docs/'`，然后再构建发布。

## 推荐发布检查

- 首页是否可访问
- 侧边栏链接是否正常
- 搜索是否可用
- 静态资源是否全部加载成功
