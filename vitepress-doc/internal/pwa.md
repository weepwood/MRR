# PWA 安装与 HTTPS 部署

MRR 前端支持安装为渐进式 Web 应用（PWA），可从 Edge、Chrome 等浏览器安装到 Windows 桌面，并以独立窗口运行。

## 安全边界

MRR 的 Service Worker 只缓存应用壳静态资源，例如构建后的 JavaScript、CSS、字体、图标和离线说明页。

以下内容不会进入 PWA 缓存：

- `/api/**` 与 `/proxy/**` 业务接口；
- 登录、权限、审计和系统监控响应；
- 患者信息、病案查询结果和影像内容；
- Actuator、Swagger 与 OpenAPI 文档。

断网时只显示通用离线说明，不展示历史业务数据。

## 运行前提

Service Worker 只在安全上下文中运行：

- `https://` 地址；
- 或当前机器上的 `http://localhost`。

因此：

- 在服务器本机访问 `http://localhost:8002` 可以进行开发验证；
- 从其他电脑访问 `http://服务器IP:8002` 时，浏览器不会启用 PWA；
- 内网正式使用应配置受终端信任的 HTTPS 证书。

推荐为 MRR 分配稳定的内网 DNS 名称，例如 `mrr.hospital.example`，并由医院内部 CA 或受信任 CA 签发证书。不要依赖浏览器仍显示证书警告的自签名证书。

## Nginx HTTPS 反向代理示例

以下示例把 HTTPS 请求转发到单体 JAR 的 `127.0.0.1:8002`：

```nginx
upstream mrr_standalone {
    server 127.0.0.1:8002;
    keepalive 32;
}

server {
    listen 80;
    server_name mrr.hospital.example;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name mrr.hospital.example;

    ssl_certificate     C:/MRR/certs/mrr-fullchain.pem;
    ssl_certificate_key C:/MRR/certs/mrr-private-key.pem;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options SAMEORIGIN always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;

    location / {
        proxy_pass http://mrr_standalone;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Connection "";
    }
}
```

证书私钥必须限制文件访问权限，不应提交到 Git 仓库或打入发布包。

## 安装方法

1. 使用 Edge 或 Chrome 打开 MRR 的 HTTPS 地址；
2. 完成登录并确认系统可以正常访问；
3. 点击地址栏右侧的“安装应用”图标，或打开浏览器菜单并选择“安装 MRR”；
4. 安装后可从桌面、开始菜单或任务栏启动。

安装仅改变启动方式，不绕过登录、权限、IP 限制、外部系统票据或审计策略。

## 验证清单

在浏览器开发者工具的 **Application** 面板检查：

- Manifest 能正常读取 `/manifest.json`；
- Service Worker 脚本为 `/sw.js?v=...`；
- Scope 为 `/`；
- 应用图标和主题色正确；
- `/api/**` 请求未出现在 Cache Storage；
- 断网刷新普通页面时显示 MRR 离线说明；
- 恢复网络后可以重新加载并正常登录。

## 更新与故障处理

新版本部署后，MRR 会检测等待中的 Service Worker，并提示重新加载。选择“稍后”不会中断当前业务操作。

若测试环境曾部署错误的 Service Worker，可在开发者工具中执行以下操作后重新加载：

1. Application → Service Workers → Unregister；
2. Application → Storage → Clear site data；
3. 关闭所有 MRR 页面并重新打开。

不要在生产终端频繁清理站点数据，因为这也可能清除浏览器保存的非敏感界面偏好。
