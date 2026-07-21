# API 与权限

## 事实来源

接口路径、参数和响应模型会随代码变化。静态文档只维护分组、权限和约定，完整字段查看运行中的 Springdoc：

```text
管理端同域入口：/api-docs/
OpenAPI JSON：/v3/api-docs
OpenAPI YAML：/v3/api-docs.yaml
后端 Swagger UI：/swagger-ui.html
```

生产环境通过文档访问会话保护这些路径，不能把 Swagger 当作公开接口目录。

## 接口前缀

业务接口通常使用：

```text
/api/v1/**
```

新增接口应保持资源化路径和明确 HTTP 方法，避免一个万能接口根据参数执行多个不相关操作。

## 主要接口域

| 域 | 典型职责 | 常见权限 |
|----|----------|----------|
| 认证 | 登录、当前用户、退出 | 登录公开，其余需会话 |
| 用户与角色 | 用户、状态、角色与权限 | `user:manage`、`role:read` 或管理员 |
| 扫描记录 | 列表、查询、维护、下载 | `record:read`、`record:manage` |
| 患者与搜索 | 病案号、上架号、身份证查询 | `record:read` 或 `search:read` |
| 统计 | 汇总、趋势、明细 | `statistics:read` |
| 档案装箱 | 箱号、异常状态和位置维护 | 记录权限 |
| OSS 迁移 | 任务、进度、校验和日志 | 记录管理权限 |
| 日志审计 | 访问日志、图片访问审计 | `log:read` |
| 系统监控 | 数据库、连接池、运行指标 | `system:read` |
| 数据质量 | 手动执行、批次和问题明细 | `system:read` 或管理员 |
| 系统设置 | 读取和保存运行时设置 | `system:read` |
| 文档会话 | 签发用户或内部文档 Cookie | 按目标文档校验 |
| 公开状态 | 当前状态、每日可用率、异常区间 | 公开且脱敏 |

## 认证与权限

前端登录成功后在业务请求中携带 JWT。后端解析用户 ID、用户名、角色和权限集合。令牌签名密钥通过 `JWT_SECRET_KEY` 注入。

权限原则：

1. 管理员可以具有全局能力。
2. 普通用户必须具备接口声明的具体权限。
3. 前端路由与按钮权限只是交互提示。
4. 后端权限校验是最终安全边界。
5. 新权限需要同步更新角色种子、权限页面和文档。

常用权限：

```text
record:read
record:edit
record:manage
statistics:read
log:read
system:read
user:manage
role:manage
role:read
search:read
```

## 响应与错误

接口应使用统一响应和异常处理，调用方至少区分：

- 参数错误。
- 未认证。
- 无权限。
- 资源不存在。
- 业务规则冲突。
- 数据库或外部文件服务异常。
- 服务端未知错误。

错误信息不能泄露 SQL、服务器绝对路径、密钥或完整个人信息。

## 分页与筛选

列表接口应明确页码起始、每页上限、总记录数、排序字段白名单和日期范围语义。不要把前端排序字段直接拼接 SQL。

## 病案查询规则

病案号和上架号统一为八位数字字符串：

```text
1234 -> 00001234
```

- `bah < 10000000`：允许只提供病案号。
- `bah >= 10000000`：必须同时提供上架号。
- 只提供上架号：允许。
- 同时提供：使用 `bah AND sjh` 精确匹配。

### 身份证查询

前端 POST 提交明文身份证号，后端返回脱敏信息和不透明令牌。浏览器刷新恢复使用令牌 GET 查询。具体 DTO 以 Springdoc 为准。

## 公开状态接口

```text
GET /api/v1/public/status/summary
GET /api/v1/public/status/daily
GET /api/v1/public/status/incidents
GET /api/v1/public/status/ping
```

只返回当前状态、持续时间、可用率、每日状态和脱敏异常区间，不能返回数据库地址、堆栈、路径、用户名或监控凭证。

## 数据质量接口

手动触发：

```http
POST /api/v1/system/data-quality/run
```

接口必须受保护并防止重复并发执行。读取批次、结果和问题样本也需要 `system:read`。

## 文档访问接口

管理端打开文档时先请求后端创建访问会话。Cookie 应：

- HttpOnly。
- 使用适当 SameSite。
- HTTPS 环境启用 Secure。
- 有明确过期时间。
- 登出时清除。

Nginx `auth_request` 与后端直连校验应保持相同权限判断。

## API 变更规则

- 向后兼容字段优先新增，不随意改名或改变类型。
- 删除接口前先标记废弃并迁移前端调用。
- 修改权限时同步检查路由、按钮、测试和文档。
- 修改数据库字段时通过 V0 之后的新 Flyway 增量迁移。
- OpenAPI 说明以 Controller 与 DTO 注解为主。
- PR 写明影响范围和兼容性。