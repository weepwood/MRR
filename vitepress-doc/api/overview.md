# API 概览

> 本文档介绍 MRR 系统 API 接口的整体设计

## API 基础信息

**Base URL**
```
开发环境: http://localhost:18045
生产环境: https://api.your-domain.com
```

**API 文档**
- Swagger UI: `/v1/swagger-ui/index.html`
- OpenAPI JSON: `/v1/api-docs`

## 认证方式

系统使用 JWT (JSON Web Token) 进行认证。

### 获取 Token

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "br_admin",
  "password": "br_password"
}
```

**响应示例**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400000
  }
}
```

### 使用 Token

在后续请求中添加 Authorization 头:

```http
GET /api/scans
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 刷新 Token

```http
POST /api/auth/refresh
Authorization: Bearer {refreshToken}
```

## 通用响应格式

所有 API 接口使用统一的响应格式:

### 成功响应

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    // 响应数据
  }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "参数错误",
  "data": null
}
```

### 分页响应

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "list": [
      // 数据列表
    ],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

## HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 204 | 删除成功 (无返回内容) |
| 400 | 请求参数错误 |
| 401 | 未认证或 Token 过期 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## API 模块划分

### 认证授权 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/logout` | POST | 用户登出 |
| `/api/auth/refresh` | POST | 刷新 Token |
| `/api/auth/profile` | GET | 获取用户信息 |
| `/api/auth/password` | PUT | 修改密码 |

### 扫描记录 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/scans` | GET | 查询扫描记录列表 |
| `/api/scans/{id}` | GET | 获取扫描记录详情 |
| `/api/scans` | POST | 创建扫描记录 |
| `/api/scans/{id}` | PUT | 更新扫描记录 |
| `/api/scans/{id}` | DELETE | 删除扫描记录 |
| `/api/scans/search` | POST | 高级搜索 |

### 患者信息 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/patients` | GET | 查询患者列表 |
| `/api/patients/{id}` | GET | 获取患者详情 |
| `/api/patients` | POST | 创建患者信息 |
| `/api/patients/{id}` | PUT | 更新患者信息 |
| `/api/patients/{id}` | DELETE | 删除患者信息 |

### 影像管理 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/images` | GET | 查询影像列表 |
| `/api/images/{id}` | GET | 获取影像详情 |
| `/api/images/upload` | POST | 上传影像 |
| `/api/images/{id}` | DELETE | 删除影像 |
| `/api/images/download` | POST | 批量下载影像 |

### 统计分析 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/statistics/overview` | GET | 统计概览 |
| `/api/statistics/daily` | GET | 每日统计 |
| `/api/statistics/modality` | GET | 按设备类型统计 |
| `/api/statistics/trend` | GET | 趋势分析 |

### 日志管理 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/logs` | GET | 查询日志列表 |
| `/api/logs/{id}` | GET | 获取日志详情 |
| `/api/logs/export` | GET | 导出日志 |

### 用户管理 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/users` | GET | 查询用户列表 |
| `/api/users/{id}` | GET | 获取用户详情 |
| `/api/users` | POST | 创建用户 |
| `/api/users/{id}` | PUT | 更新用户信息 |
| `/api/users/{id}` | DELETE | 删除用户 |
| `/api/users/{id}/reset-password` | POST | 重置密码 |

### 系统信息 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/system/info` | GET | 系统信息 |
| `/api/system/health` | GET | 健康检查 |
| `/api/system/config` | GET | 系统配置 |

## 请求参数

### 查询参数

```http
GET /api/scans?pageNum=1&pageSize=20&keyword=CT&startDate=2024-01-01
```

### 路径参数

```http
GET /api/scans/123
```

### 请求体

```http
POST /api/scans
Content-Type: application/json

{
  "patientId": "P001",
  "scanDate": "2024-01-01",
  "modality": "CT",
  "description": "胸部CT扫描"
}
```

## 权限控制

API 接口使用权限注解进行访问控制:

```java
@RequirePermissions("scan:create")
@PostMapping("/scans")
public Result createScan(@RequestBody ScanRequest request) {
    // ...
}
```

**权限列表**

| 权限代码 | 说明 |
|---------|------|
| `scan:view` | 查看扫描记录 |
| `scan:create` | 创建扫描记录 |
| `scan:update` | 更新扫描记录 |
| `scan:delete` | 删除扫描记录 |
| `user:view` | 查看用户信息 |
| `user:create` | 创建用户 |
| `user:update` | 更新用户信息 |
| `user:delete` | 删除用户 |
| `admin:all` | 所有权限 |

## 错误码

| 错误码 | 说明 |
|--------|------|
| 1001 | 用户名或密码错误 |
| 1002 | Token 无效或已过期 |
| 1003 | 权限不足 |
| 2001 | 参数验证失败 |
| 2002 | 资源不存在 |
| 3001 | 数据库操作失败 |
| 3002 | 文件上传失败 |
| 4001 | 系统内部错误 |

## API 调用示例

### JavaScript (Axios)

```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:18045',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.get('/api/scans', {
  params: {
    pageNum: 1,
    pageSize: 20
  }
}).then(response => {
  console.log(response.data)
})
```

### Python (requests)

```python
import requests

BASE_URL = 'http://localhost:18045'
TOKEN = 'your-jwt-token'

headers = {
    'Authorization': f'Bearer {TOKEN}',
    'Content-Type': 'application/json'
}

response = requests.get(
    f'{BASE_URL}/api/scans',
    headers=headers,
    params={'pageNum': 1, 'pageSize': 20}
)

print(response.json())
```

### cURL

```bash
curl -X GET "http://localhost:18045/api/scans?pageNum=1&pageSize=20" \
  -H "Authorization: Bearer your-jwt-token" \
  -H "Content-Type: application/json"
```

## API 测试

### 使用 Swagger UI

1. 访问 http://localhost:18045/v1/swagger-ui/index.html
2. 点击 "Authorize" 按钮
3. 输入 Token: `Bearer your-jwt-token`
4. 选择接口进行测试

### 使用 Postman

1. 导入 OpenAPI 文档
2. 配置环境变量
3. 设置认证 Token
4. 发送请求测试

## 相关链接

- [认证授权 API](/api/authentication)
- [扫描记录 API](/api/records)
- [影像管理 API](/api/images)
- [统计分析 API](/api/statistics)
