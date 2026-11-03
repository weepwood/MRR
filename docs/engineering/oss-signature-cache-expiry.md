# OSS 签名缓存有效期

关联 #390，风险 P2。负责人：Codex；分支 `codex/390-oss-signature-cache`；worktree `D:/Project/CodexWorkTress/oss-signature-cache/MRR`；状态：待合并。基线 `2b03f9c8`，2026-10-29。

## 调查与最小修改

现有链路是受管前缀装饰器 → Spring签名缓存代理 → OssServiceImpl → SDK签名器。固定600秒缓存会在合法的60秒签名配置下返回过期地址。保留同一缓存、签名方法、受管前缀校验、统计及容量上限。

CacheConfig按启动OssProperties计算 `max(0,min(600,urlExpireSeconds-30))` 秒TTL，预留30秒余量。60秒配置缓存30秒，默认3600秒配置仍缓存600秒。没有OSS配置Bean的已有切片测试回退默认Properties。Caffeine单调Ticker生产使用systemTicker，定向测试提供可控Ticker。

当前 `oss.urlExpireSeconds` 仅通过@ConfigurationProperties在启动加载，不在运行时设置注册表中；改变配置必须重启应用，新Spring上下文建立空缓存，旧签名不复用。不新增动态配置入口。若未来开放热更新，必须同时增加旧缓存主动失效，不能只修改Properties字段。

无接口、权限、凭据、数据库、Flyway或部署组件变更；不改既有ManagedPrefixOssService、不清理其他缓存、不扩大存储访问范围。

## RED/GREEN证据

新增OssSignedUrlCacheTest使用真实Spring缓存代理与可控单调时间，SDK签名器mock，不访问真实存储。

- RED：旧实现4项中3项失败，短签名、超时重签及浏览器缓存共享场景返回旧地址。
- GREEN：`mvnw.cmd -o -B -ntp -Dtest=OssSignedUrlCacheTest,ManagedPrefixOssServiceTest,OssServiceImplTest,ScanServiceCacheTest -Dlocal.config.exclude=true test`：35项，失败/错误/跳过均0，BUILD SUCCESS。
- 覆盖60秒有效期、29/31/92秒边界；默认配置599/601秒边界；同key普通与浏览器签名共享；预先缓存非受管对象仍拒绝浏览器访问；配置重启后空缓存和新签名持续时间。

本地只运行对应测试。没有读取实际部署配置值或输出真实签名URL。未执行PG16集成或真实S3兼容存储验收，远端CI和现场存储结果另行确认。

## 独立审查

只读代理 `privacy_review` 独立读取#390、diff、启动入口、运行时设置注册表及受管前缀装饰器，确认当前配置仅重启生效、可选Properties/Ticker装配及最短/最长TTL边界。独立执行新增4项、ScanServiceCacheTest/OssPropertiesValidationTest共10项，均通过、无跳过；`git diff --check`通过。无阻塞发现。旧性能说明中的固定10分钟描述已同步修正。

## 回滚与剩余验收

独立撤销本PR并重启会重新建立缓存，无数据恢复。回滚会恢复短有效期的旧缓存风险，应先将签名有效期保持在既有600秒缓存以上并预留余量，或停用遗留签名入口；不回退已修复的前缀校验和受控影像网关。配置在安全服务器侧处理，不写入PR或Artifact。
