-- 为现有业务、认证与运维表补充 PostgreSQL 表级注释。

COMMENT ON TABLE app.mr_scan IS '病案扫描影像记录，保存影像文件元数据及 OSS 迁移信息';
COMMENT ON TABLE app.mr_patient IS '患者基本信息及病案关联信息';
COMMENT ON TABLE app.mr_statistics IS '病案影像统计与检索数据';
COMMENT ON TABLE app.mr_count IS '病案及影像页数汇总数据';
COMMENT ON TABLE app.mr_archive_box_record IS '实体病案装箱位置与盘点状态记录';

COMMENT ON TABLE app.mr_auth_role IS '系统角色及权限集合';
COMMENT ON TABLE app.mr_auth_user IS '系统认证用户及登录状态信息';
COMMENT ON TABLE app.mr_user IS '历史用户基础信息表';

COMMENT ON TABLE app.access_log IS '接口访问与影像访问审计日志';
COMMENT ON TABLE app.image_migration_log IS '影像文件迁移至 OSS 的逐文件执行日志';
COMMENT ON TABLE app.migration_job IS '批量 OSS 迁移任务执行状态';
COMMENT ON TABLE app.mr_system_settings IS '系统级键值配置';
COMMENT ON TABLE app.frontend_response_metric IS '前端上报的接口响应性能指标';

COMMENT ON TABLE app.mrr_data_quality_run IS '数据质量检查执行批次';
COMMENT ON TABLE app.mrr_data_quality_check_result IS '数据质量检查项汇总结果';
COMMENT ON TABLE app.mrr_data_quality_issue IS '数据质量检查发现的具体问题';
COMMENT ON TABLE app.system_availability_period IS 'MRR 服务可用性状态时间区间';
