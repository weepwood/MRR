# 历史 Flyway 迁移归档

此目录保存日期时间版本基线重整前的增量迁移脚本，仅用于审计和历史追溯。

应用的活动迁移位置是 `classpath:db/migration`，兼容回调位置是 `classpath:db/callback`，因此本目录不会被 Flyway 执行。新生产数据库必须从 `db/migration/V20260715113552__baseline_schema.sql` 初始化；不得把这里的脚本重新加入同一条迁移链。

若需要迁移已部署的旧数据库，应遵循 `backend-repo/docs/flyway-versioning.md` 中的历史版本映射方案，不能直接把归档脚本重新放回活动迁移目录。
