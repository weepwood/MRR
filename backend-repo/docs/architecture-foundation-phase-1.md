# 架构落地第一阶段

本阶段基于 `dev-no-login` 已合并的病案主数据模型实施，不改变数据库结构，也不引入微服务。

## 已落地边界

### 1. 大表查询保护

旧接口继续兼容，但所有限制都在 PostgreSQL 查询阶段执行：

```text
GET  /api/v1/scan             最多返回 1000 条
POST /api/v1/scan/condition   最多返回 1000 条
```

不再执行“全表加载到 JVM 后再截断”。

新增主键游标接口：

```http
GET /api/v1/scan/cursor?afterId=0&size=100
```

响应示例：

```json
{
  "code": 200,
  "data": {
    "list": [],
    "nextCursorId": 100,
    "hasMore": true,
    "size": 100
  }
}
```

游标查询执行：

```sql
SELECT *
FROM app.mr_scan
WHERE id > :afterId
ORDER BY id
LIMIT :sizePlusOne;
```

通过多读取一条判断下一页，避免深度 `OFFSET`。原页码接口暂时保留，现有前端不需要同步迁移。

### 2. 图片存储访问端口

新增：

```text
storage/ImageStorage
storage/LocalImageStorage
```

Controller 和 ZIP 导出不再读取 `image.basePath` 或拼接 Windows 路径。`LocalImageStorage` 负责：

- 读取外置 `image.basePath`；
- 按历史目录规则定位文件；
- 拒绝绝对路径、目录分隔符和 `..`；
- 验证结果始终位于配置根目录内；
- 检查文件存在且可读。

未来接入 NAS 或对象存储时，应增加新的 `ImageStorage` 实现，不在 Controller 中增加存储判断。

### 3. 导出应用服务

新增：

```text
ArchiveExportService
ArchiveExportServiceImpl
```

`ScanController` 只负责：

- 校验请求数量不超过 200；
- 创建导出计划；
- 设置 HTTP 下载响应。

导出服务负责：

- 读取扫描文件元数据；
- 通过 `ImageStorage` 打开文件；
- 流式写入 ZIP；
- 清理 ZIP 条目名称；
- 对重复文件名增加序号；
- 跳过无法打开的源文件；
- ZIP 响应写入失败时立即终止，避免返回损坏文件。

### 4. 数据类型与测试

扫描记录计数 Mapper 返回类型从 `int` 调整为 `long`。

新增或更新测试覆盖：

- 旧查询 SQL 限流；
- 游标分页的下一页判断；
- PostgreSQL 游标 SQL；
- 本地影像目录穿越防护；
- ZIP 重名处理与不可变导出计划；
- Controller 新依赖和兼容接口。

## 本地验证

```powershell
./backend-repo/scripts/verify-architecture-foundation.ps1
```

脚本执行 Maven 编译、定向单元测试，并在 Docker 可用时执行 PostgreSQL 16 Mapper 集成测试。

## 兼容性

- 不修改 Flyway 和数据表。
- 不修改现有前端调用的页码分页接口。
- 不修改病案主数据 `mr_archive/archive_id` 规则。
- 不修改 OSS 迁移队列、CSV 数据交换中心和 Windows 部署脚本。
- 旧无分页接口保留，但明确标记为废弃并限制为 1000 条。

## 后续阶段

建议按以下顺序继续：

1. 前端扫描记录列表切换为游标分页或分页结果缓存，减少重复 `COUNT(*)`。
2. API 数据库实体与响应 DTO 分离。
3. `access_log`、`frontend_response_metric` 按月分区。
4. 在线请求、OSS 迁移、数据导入和质量检查使用独立有界线程池。
5. 显式路由成为唯一正式路由来源，移除文件系统路由双轨兼容。
