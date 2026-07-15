# 历史 Flyway 迁移归档

此目录保存 V0 基线重整前的增量迁移脚本，仅用于审计和历史追溯。

应用的 `spring.flyway.locations` 只指向 `classpath:db/migration`，因此本目录不会被 Flyway 执行。新生产数据库必须从 `db/migration/V0__baseline_schema.sql` 初始化；不得把这里的脚本重新加入同一条迁移链。

若需要迁移已部署的旧数据库，应单独制定数据迁移方案，不能直接切换到 V0 基线。
