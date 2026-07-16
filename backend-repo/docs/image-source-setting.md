# 图片来源设置

系统设置支持在本地图片服务器与 OSS 之间切换影像档案的默认读取来源。

## 设置入口

进入：

```text
系统设置 → 系统与档案 → 影像档案袋 → 图片来源
```

可选值：

| 设置值 | 界面名称 | 行为 |
| --- | --- | --- |
| `local` | 本地图片 | 从现有本地/Nginx 图片地址读取 |
| `oss` | OSS 图片 | 优先生成 OSS 临时签名地址 |

默认值为：

```text
local
```

数据库中尚不存在 `imageSource`、设置值为空、设置值非法或设置读取失败时，后端均按 `local` 处理。

## 生效范围

设置会影响：

- 影像档案袋图片列表；
- 影像档案袋缩略图和大图预览；
- 旧版影像查看页面；
- `GET /api/v1/img/url/{id}` 单条图片 URL；
- 前端基于当前图片 URL 创建的档案 ZIP。

以下显式接口不受默认来源设置影响：

```text
GET /api/v1/img/oss-image/{id}
```

该接口始终用于直接验证或访问 OSS 图片。

服务端流式 ZIP 接口暂时继续从本地存储读取：

```text
GET  /api/v1/img/download/{BAH}
POST /api/v1/scan/batch-download
```

这样可以避免一次批量下载占用大量 OSS 出网连接。后续如需让服务端 ZIP 也从 OSS 回源，应增加独立并发上限、超时、失败重试和流量监控后再启用。

## OSS 回退规则

选择 OSS 后，系统只对已经存在 `mr_scan.oss_url` 的记录生成签名地址。

以下情况自动回退本地图片：

- 当前扫描记录尚未迁移到 OSS；
- `oss_url` 为空；
- OSS 客户端未配置；
- 签名地址生成失败；
- 签名服务返回空地址；
- 系统设置读取失败。

因此可以在图片尚未全部迁移完成时提前启用 OSS，不会导致未迁移档案全部无法查看。

## API 响应约定

影像查询接口中的：

```text
img_url
```

始终表示按照当前系统设置解析后的有效首选地址。

- 本地模式：`img_url` 为本地图片 URL；
- OSS 模式：`img_url` 为 OSS 签名 URL；
- OSS 回退：`img_url` 为本地图片 URL。

`ossUrl` 仅在本次响应实际成功生成 OSS 签名地址时返回，用于兼容旧前端。新代码应优先使用 `img_url`。

## 验证

后端定向验证：

```powershell
./backend-repo/scripts/verify-architecture-foundation.ps1
```

前端设置解析与图片 URL 优先级测试：

```powershell
cd frontend-fantastic-admin
pnpm vitest run src/utils/__tests__/system-settings.test.ts src/views/statistics/archive/__tests__/constants.test.ts
```

手工验收：

1. 设置保持默认“本地图片”，查询一个已迁移到 OSS 的档案，确认返回和显示的仍是本地地址。
2. 切换为“OSS 图片”并保存，重新查询同一档案，确认已迁移图片使用签名地址。
3. 查询一个未迁移档案，确认自动使用本地地址。
4. 恢复默认设置并保存，确认重新回到本地地址。
