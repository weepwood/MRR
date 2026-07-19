# MRR 可选高级监控

> 本目录不是单服务器生产部署的必需部分。

MRR 默认采用内置状态页、结构化本地日志、健康检查和 Windows 计划任务完成日常运维。只有在未来增加第二台服务器、专职运维人员或需要长期指标看板时，才需要部署本目录中的 Prometheus、Grafana、Alertmanager 和 exporter。

## 默认单服务器模式

默认运行组件只有：

- PostgreSQL 16
- MRR Spring Boot 后端
- Nginx 网关

默认管理端口仅监听本机：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/health/liveness
http://127.0.0.1:18046/actuator/health/readiness
http://127.0.0.1:18046/actuator/metrics
```

Prometheus 导出默认关闭，Windows 安装脚本不会复制或安装本目录中的任何组件。

## 何时再启用

满足下列任一情况时，可以考虑启用高级监控：

- MRR 部署到多台服务器。
- 增加独立图片、缩略图、PDF 或迁移服务。
- 需要跨服务器告警和长期趋势看板。
- 有独立运维人员负责维护监控平台。

启用时在生产配置中增加：

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.prometheus.metrics.export.enabled=true
```

随后再按需部署：

- `postgres_exporter`
- `windows_exporter`
- Prometheus
- Grafana
- Alertmanager

## PostgreSQL 统计扩展

无论是否部署 Prometheus，仍建议在 PostgreSQL 中启用 `pg_stat_statements`，因为 MRR 自带数据库监控与慢 SQL 页面会使用它：

```conf
shared_preload_libraries = 'pg_stat_statements'
track_io_timing = on
compute_query_id = on
pg_stat_statements.max = 10000
pg_stat_statements.track = all
```

修改后重启 PostgreSQL。应用启动时的 Flyway 基线会负责扩展和对象的正常初始化；生产数据库账号权限不足时，应由数据库管理员手工完成扩展创建。

## 原有配置用途

本目录保留以下内容，供扩容后使用：

- Prometheus 抓取配置和告警规则。
- Grafana Dashboard。
- PostgreSQL exporter 查询配置。
- Alertmanager 示例配置。

这些文件不会影响默认单服务器部署，也不应直接暴露到公网。
