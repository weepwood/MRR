# MRR 全接口访问审计指南

## 1. 审计目标

MRR 使用两层日志共同回答以下问题：

- 谁在什么时间访问了系统；
- 请求来自哪个客户端 IP 和页面来源；
- 实际访问了哪个 URI，对应哪个 Spring MVC 接口模板；
- 使用了哪些病案号、上架号、身份证查询条件等业务参数；
- 请求是否成功、耗时多久、是否发生异常；
- 后端未启动、维护模式或代理超时时，入口请求是否真实发生过。

## 2. 两层审计结构

### 2.1 Nginx 入口审计

文件：

```text
logs/nginx/access-audit.jsonl
```

记录所有到达 Nginx 的请求，包括未进入 Spring Boot 的请求。主要字段：

- `time`
- `remote_addr`
- `forwarded_for`
- `method`
- `uri`
- `status`
- `request_time`
- `upstream_status`
- `upstream_response_time`
- `request_id`
- `origin`
- `user_agent`

入口日志不记录 Query String，避免外部票据、Token 或签名明文落盘。

### 2.2 Spring Boot 业务审计

数据库表：

```text
app.access_log
```

记录已经进入应用的业务请求。主要字段：

- `request_id`：与响应头 `X-Request-Id` 对应；
- `username`：登录用户或外部档案访问用户；
- `client_ip`；
- `request_uri`：真实请求路径；
- `endpoint_template`：接口模板；
- `query_string`：业务查询参数；
- `request_body`：文本请求体；
- `referer`；
- `response_status`；
- `execute_time`；
- `error_message`；
- `audit_action`、`audit_target`、`audit_description`。

密码、Token、Secret、签名、票据和密钥等凭据不会明文保存，而是记录为：

```text
sha256:<32位摘要>
```

摘要可用于判断多条日志是否使用了同一凭据，但不能还原原值。

## 3. 常用追溯方式

### 3.1 根据 Request ID 定位单次请求

```sql
SELECT *
FROM app.access_log
WHERE request_id = '响应头中的 X-Request-Id';
```

然后在 `access-audit.jsonl` 中搜索同一 `request_id`，可关联入口层和应用层耗时。

### 3.2 查询某个用户的全部访问

```sql
SELECT access_time, username, client_ip, method,
       request_uri, endpoint_template, response_status,
       execute_time, audit_action, audit_target
FROM app.access_log
WHERE username = '用户名'
ORDER BY access_time DESC;
```

### 3.3 查询某个病案号或业务参数

日志管理页面的“关键字”支持搜索 Query String、Request Body、审计目标、真实 URI、接口模板和 Request ID。

也可以直接查询：

```sql
SELECT *
FROM app.access_log
WHERE query_string LIKE '%00123456%'
   OR request_body LIKE '%00123456%'
   OR audit_target = '00123456'
ORDER BY access_time DESC;
```

### 3.4 查询异常接口访问

```sql
SELECT access_time, request_id, username, client_ip,
       method, request_uri, endpoint_template,
       response_status, execute_time, error_message
FROM app.access_log
WHERE response_status LIKE '4%'
   OR response_status LIKE '5%'
ORDER BY access_time DESC;
```

### 3.5 查询某个接口模板的访问情况

```sql
SELECT endpoint_template,
       method,
       COUNT(*) AS request_count,
       AVG(execute_time) AS avg_ms,
       MAX(execute_time) AS max_ms,
       COUNT(*) FILTER (
           WHERE response_status LIKE '4%'
              OR response_status LIKE '5%'
       ) AS error_count
FROM app.access_log
WHERE endpoint_template = '/api/v1/img/{bah}'
GROUP BY endpoint_template, method;
```

## 4. 能覆盖与不能覆盖的范围

| 场景 | Nginx 入口日志 | 应用审计日志 |
| --- | --- | --- |
| 正常业务接口 | 是 | 是 |
| 权限拒绝 | 是 | 是（请求进入应用时） |
| 后端未启动 | 是 | 否 |
| Nginx 维护模式 | 是 | 否 |
| 代理连接超时 | 是 | 否 |
| 静态资源 | 是 | 默认不进入应用审计 |
| Actuator、Swagger、文档 | 是 | 默认排除 |

因此，判断“请求是否到达服务器”以 Nginx 为准；判断“谁访问了哪个业务对象、使用了什么业务参数”以应用审计为准。

## 5. 运维与安全建议

1. `app.access_log` 的查询权限继续限定为 `log:read`。
2. 数据库管理员权限和日志读取权限应分离，避免普通业务管理员修改审计记录。
3. 根据医院内部制度配置数据库日志保留期和 Nginx 日志归档周期。
4. 定期将历史审计日志导出到只读目录或独立介质，并记录文件 SHA-256。
5. 不要在 URL、Referer 或请求体中传递明文密码；外部系统票据应设置短有效期并避免重复使用。
6. 审计数据包含患者和用户信息，备份、导出和查看均应纳入权限与操作审计。

## 6. 后续增强方向

当前方案解决“完整记录和快速查询”。如果需要更强的防篡改能力，可以继续增加：

- 审计表只允许专用数据库账号 `INSERT`，禁止应用账号 `UPDATE/DELETE`；
- 日志哈希链或每日 Merkle Root；
- 每日签名归档；
- 独立审计员只读角色；
- Nginx 日志自动轮转、压缩和校验清单。
