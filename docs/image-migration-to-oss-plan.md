# 本地图片迁移到 OSS 完整方案

## 一、当前存储结构分析

### 1.1 本地文件系统结构
```
./data/img/                          # image.basePath
├── YY.MM/                           # 年份.月份 (parentFolder)
│   └── YY.MM.DD/                    # 年份.月份.日期 (folder)
│       └── BRXH-BAH/                # 病人序号-病案号 (folderName)
│           ├── 0001.jpg
│           ├── 0002.jpg
│           └── ...
```

**路径示例：**
- basePath: `./data/img`
- folder: `24.04.30`
- parentFolder: `24.04` (folder 前5位)
- folderName: `605746-00789508` (BRXH-BAH)
- filename: `0001.jpg`

**完整路径：** `./data/img/24.04/24.04.30/605746-00789508/0001.jpg`

### 1.2 数据库字段
```sql
mr_scan 表：
- id: 记录ID
- brxh: 病人序号
- bah: 病案号
- filename: 文件名
- folder: 文件夹 (YY.MM.DD)
- oss_url: OSS URL (新增，待填充)
```

---

## 二、OSS 存储结构设计

### 2.1 OSS Bucket 配置
```yaml
oss:
  endpoint: oss-cn-hangzhou.aliyuncs.com
  bucket: mrr-medical-records
  accessKeyId: ${OSS_ACCESS_KEY_ID}
  accessKeySecret: ${OSS_ACCESS_KEY_SECRET}
  region: cn-hangzhou
```

### 2.2 OSS 目录结构（推荐方案）

#### 方案 A：保持原有结构（推荐）
```
oss://mrr-medical-records/
├── images/
│   ├── YY.MM/
│   │   └── YY.MM.DD/
│   │       └── BRXH-BAH/
│   │           ├── 0001.jpg
│   │           └── 0002.jpg
└── metadata/
    └── migration-log/
```

**优点：**
- 与现有结构一致，迁移逻辑简单
- 便于理解和维护
- URL 生成规则清晰

**OSS URL 格式：**
```
https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com/images/24.04/24.04.30/605746-00789508/0001.jpg
```

#### 方案 B：扁平化结构（备选）
```
oss://mrr-medical-records/
└── images/
    ├── BAH/
    │   └── BRXH-BAH/
    │       ├── 0001.jpg
    │       └── 0002.jpg
```

**优点：**
- 按病案号组织，查询更直观
- 减少目录层级

**缺点：**
- 需要重新设计路径映射逻辑

### 2.3 文件命名规范
- **保持原文件名**：`0001.jpg`, `0002.jpg`
- **不建议重命名**：避免破坏现有引用关系

### 2.4 OSS 访问控制
```yaml
# 推荐配置
oss:
  # 私有读写（推荐，通过签名 URL 访问）
  acl: private
  
  # 或公共读（简化访问，但需注意安全）
  # acl: public-read
  
  # CDN 加速（可选）
  cdn:
    enabled: true
    domain: cdn.mrr-medical.com
```

---

## 三、数据库迁移规划

### 3.1 新增迁移记录表

```sql
-- 迁移状态追踪表
CREATE TABLE IF NOT EXISTS app.image_migration_log (
    id BIGSERIAL PRIMARY KEY,
    scan_id INTEGER NOT NULL REFERENCES app.mr_scan(id),
    local_path TEXT NOT NULL,
    oss_url TEXT,
    migration_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    -- pending: 待迁移
    -- migrating: 迁移中
    -- success: 迁移成功
    -- failed: 迁移失败
    -- verified: 已验证
    -- rollback: 已回滚
    error_message TEXT,
    file_size BIGINT,
    checksum_md5 VARCHAR(32),
    migrated_at TIMESTAMP WITHOUT TIME ZONE,
    verified_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 索引优化
CREATE INDEX idx_migration_status ON app.image_migration_log(migration_status);
CREATE INDEX idx_migration_scan_id ON app.image_migration_log(scan_id);
CREATE INDEX idx_migration_created_at ON app.image_migration_log(created_at);

-- 注释
COMMENT ON TABLE app.image_migration_log IS '图片迁移日志表';
COMMENT ON COLUMN app.image_migration_log.migration_status IS '迁移状态: pending/migrating/success/failed/verified/rollback';
```

### 3.2 mr_scan 表增强

```sql
-- 已有字段（之前已添加）
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS oss_url TEXT;

-- 添加迁移相关字段（可选，用于快速查询）
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS migration_status VARCHAR(20) DEFAULT 'not_migrated';
-- not_migrated: 未迁移
-- migrated: 已迁移
-- verified: 已验证

ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS migrated_at TIMESTAMP WITHOUT TIME ZONE;

-- 索引
CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_status ON app.mr_scan(migration_status);
CREATE INDEX IF NOT EXISTS idx_mr_scan_oss_url ON app.mr_scan(oss_url) WHERE oss_url IS NOT NULL;

COMMENT ON COLUMN app.mr_scan.oss_url IS 'OSS 图片地址';
COMMENT ON COLUMN app.mr_scan.migration_status IS '迁移状态';
COMMENT ON COLUMN app.mr_scan.migrated_at IS '迁移完成时间';
```

### 3.3 迁移状态流转

```
not_migrated → pending → migrating → success → verified
                         ↓
                       failed → retry → migrating
                         ↓
                       rollback → not_migrated
```

---

## 四、迁移工具设计方案

### 4.1 技术选型

#### 后端服务扩展
```java
// 新增组件
- OssService: OSS 上传/下载服务
- MigrationService: 迁移业务逻辑
- MigrationController: 迁移管理接口
- MigrationScheduler: 定时任务（可选）
```

#### 依赖添加（pom.xml）
```xml
<!-- 阿里云 OSS SDK -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>

<!-- MD5 校验 -->
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.21.0</version>
</dependency>
```

### 4.2 核心功能模块

#### 4.2.1 OSS 配置类
```java
@ConfigurationProperties(prefix = "oss")
@Data
public class OssProperties {
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String baseUrl;  // OSS 访问域名
    private Boolean enableCdn;
    private String cdnDomain;
}
```

#### 4.2.2 OSS 服务类
```java
@Service
public class OssService {
    private OSS ossClient;
    private OssProperties ossProperties;
    
    /**
     * 上传文件到 OSS
     * @param localFile 本地文件
     * @param ossKey OSS 对象键
     * @return OSS URL
     */
    public String uploadFile(File localFile, String ossKey) {
        // 实现上传逻辑
    }
    
    /**
     * 生成 OSS 访问 URL
     * @param ossKey OSS 对象键
     * @param expireSeconds 过期时间（秒）
     * @return 签名 URL
     */
    public String generatePresignedUrl(String ossKey, int expireSeconds) {
        // 生成签名 URL
    }
    
    /**
     * 验证文件完整性
     * @param ossKey OSS 对象键
     * @param expectedMd5 期望的 MD5
     * @return 是否匹配
     */
    public boolean verifyFileIntegrity(String ossKey, String expectedMd5) {
        // 验证逻辑
    }
}
```

#### 4.2.3 迁移服务类
```java
@Service
public class MigrationService {
    @Autowired
    private ScanMapper scanMapper;
    
    @Autowired
    private OssService ossService;
    
    @Autowired
    private MigrationLogMapper migrationLogMapper;
    
    /**
     * 批量迁移图片
     * @param batchSize 批次大小
     * @return 迁移结果统计
     */
    public MigrationResult batchMigrate(int batchSize) {
        // 1. 查询待迁移的记录
        // 2. 逐个上传到 OSS
        // 3. 更新数据库
        // 4. 记录日志
    }
    
    /**
     * 迁移单个图片
     */
    public boolean migrateSingleImage(Integer scanId) {
        // 详细迁移逻辑
    }
    
    /**
     * 验证迁移结果
     */
    public boolean verifyMigration(Integer scanId) {
        // 对比本地文件和 OSS 文件的 MD5
    }
    
    /**
     * 回滚迁移
     */
    public boolean rollbackMigration(Integer scanId) {
        // 清空 oss_url，恢复状态
    }
}
```

### 4.3 REST API 接口设计

```java
@RestController
@RequestMapping("/v1/migration-api")
@Tag(name = "Image Migration", description = "图片迁移管理接口")
public class MigrationController {
    
    @PostMapping("/start")
    @RequirePermissions("system:migrate")
    public Result<MigrationResult> startMigration(@RequestBody MigrationRequest request) {
        // 启动批量迁移
    }
    
    @GetMapping("/status")
    @RequirePermissions("system:migrate")
    public Result<MigrationStatus> getMigrationStatus() {
        // 查询迁移进度
    }
    
    @PostMapping("/retry-failed")
    @RequirePermissions("system:migrate")
    public Result<Integer> retryFailedMigrations() {
        // 重试失败的迁移
    }
    
    @PostMapping("/verify")
    @RequirePermissions("system:migrate")
    public Result<VerificationResult> verifyMigrations(@RequestBody VerifyRequest request) {
        // 验证迁移结果
    }
    
    @PostMapping("/rollback")
    @RequirePermissions("system:migrate")
    public Result<Boolean> rollbackMigration(@PathVariable Integer scanId) {
        // 回滚单个迁移
    }
    
    @GetMapping("/logs")
    @RequirePermissions("system:migrate")
    public Result<List<MigrationLog>> getMigrationLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        // 查询迁移日志
    }
}
```

---

## 五、迁移执行流程

### 5.1 迁移前准备

#### 步骤 1：环境配置
```properties
# application.properties 添加 OSS 配置
oss.endpoint=oss-cn-hangzhou.aliyuncs.com
oss.bucket=mrr-medical-records
oss.access-key-id=${OSS_ACCESS_KEY_ID}
oss.access-key-secret=${OSS_ACCESS_KEY_SECRET}
oss.region=cn-hangzhou
oss.base-url=https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com
oss.enable-cdn=false
```

#### 步骤 2：数据库初始化
```bash
# 执行迁移脚本
psql -U postgres -d imgapi -f migration_create_tables.sql
```

#### 步骤 3：备份数据
```bash
# 备份 mr_scan 表
pg_dump -U postgres -d imgapi -t app.mr_scan > backup_mr_scan_$(date +%Y%m%d).sql
```

### 5.2 迁移执行

#### 阶段 1：小批量测试（10-50 条）
```bash
curl -X POST http://localhost:18045/v1/migration-api/start \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "batchSize": 10,
    "testMode": true
  }'
```

**验证要点：**
- ✅ 文件上传成功
- ✅ OSS URL 正确生成
- ✅ 数据库更新正确
- ✅ 前端能正常访问
- ✅ MD5 校验通过

#### 阶段 2：分批全量迁移
```bash
# 每批 1000 条，避免长时间占用资源
curl -X POST http://localhost:18045/v1/migration-api/start \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "batchSize": 1000,
    "testMode": false
  }'
```

**监控指标：**
- 迁移速度（条/分钟）
- 成功率
- 失败原因分布
- OSS 存储用量
- 网络带宽使用

#### 阶段 3：验证迁移结果
```bash
# 随机抽样验证
curl -X POST http://localhost:18045/v1/migration-api/verify \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sampleSize": 100,
    "verifyChecksum": true
  }'
```

### 5.3 迁移后操作

#### 步骤 1：切换前端访问
- 确认所有图片迁移成功
- 前端已通过 `ossUrl` 访问（已完成）
- 监控访问错误率

#### 步骤 2：清理本地文件（谨慎！）
```bash
# 建议保留至少 30 天后再清理
# 先标记可删除的文件
UPDATE app.mr_scan 
SET migration_status = 'ready_for_cleanup'
WHERE migration_status = 'verified'
  AND migrated_at < NOW() - INTERVAL '30 days';
```

#### 步骤 3：归档旧数据
```bash
# 备份本地图片目录
tar -czf img_backup_$(date +%Y%m%d).tar.gz ./data/img/

# 移动到归档位置
mv ./data/img ./archive/img_$(date +%Y%m%d)
```

---

## 六、回滚方案

### 6.1 自动回滚触发条件
- 迁移失败率 > 5%
- OSS 访问错误率 > 2%
- 文件完整性验证失败

### 6.2 手动回滚步骤

#### 步骤 1：停止迁移任务
```bash
curl -X POST http://localhost:18045/v1/migration-api/stop
```

#### 步骤 2：回滚数据库
```sql
-- 清空已迁移记录的 oss_url
UPDATE app.mr_scan 
SET oss_url = NULL,
    migration_status = 'not_migrated',
    migrated_at = NULL
WHERE migration_status IN ('migrated', 'verified');

-- 或删除迁移日志
DELETE FROM app.image_migration_log;
```

#### 步骤 3：恢复前端配置
- 前端会自动降级到 `img_url`
- 无需额外操作

#### 步骤 4：清理 OSS 文件（可选）
```java
// 批量删除 OSS 文件
ossService.batchDeleteObjects("images/");
```

### 6.3 回滚验证
```bash
# 验证前端是否能正常访问
curl http://frontend/api/img-api/00789508

# 检查返回的 img_url 是否正常
```

---

## 七、监控和告警

### 7.1 关键监控指标

```yaml
metrics:
  - name: migration_total_count
    type: counter
    description: 总迁移数量
    
  - name: migration_success_count
    type: counter
    description: 成功迁移数量
    
  - name: migration_failed_count
    type: counter
    description: 失败迁移数量
    
  - name: migration_progress_percent
    type: gauge
    description: 迁移进度百分比
    
  - name: oss_upload_duration_seconds
    type: histogram
    description: OSS 上传耗时
    
  - name: oss_access_error_rate
    type: gauge
    description: OSS 访问错误率
```

### 7.2 告警规则

```yaml
alerts:
  - name: HighMigrationFailureRate
    condition: migration_failed_count / migration_total_count > 0.05
    severity: critical
    message: "迁移失败率超过 5%"
    
  - name: SlowMigrationSpeed
    condition: migration_progress_percent < 10 after 1 hour
    severity: warning
    message: "迁移速度过慢"
    
  - name: OssAccessError
    condition: oss_access_error_rate > 0.02
    severity: critical
    message: "OSS 访问错误率超过 2%"
```

---

## 八、性能优化建议

### 8.1 并发控制
```java
// 使用线程池控制并发
ExecutorService executor = Executors.newFixedThreadPool(10);

// 限流
RateLimiter rateLimiter = RateLimiter.create(50.0); // 每秒 50 个请求
```

### 8.2 批量上传优化
```java
// 使用 OSS 批量上传接口
ossClient.uploadPart()  // 分片上传大文件
ossClient.completeMultipartUpload()
```

### 8.3 数据库批量更新
```java
// 使用 MyBatis 批量更新
@Update("<script>" +
    "UPDATE app.mr_scan SET oss_url = CASE id " +
    "<foreach collection='items' item='item'>" +
    "WHEN #{item.id} THEN #{item.ossUrl}" +
    "</foreach>" +
    "END WHERE id IN " +
    "<foreach collection='items' item='item' open='(' separator=',' close=')'>" +
    "#{item.id}" +
    "</foreach>" +
    "</script>")
int batchUpdateOssUrl(@Param("items") List<OssUrlUpdate> items);
```

---

## 九、成本估算

### 9.1 OSS 存储成本
```
假设：
- 图片总数：100,000 张
- 平均每张图片：500 KB
- 总存储量：50 GB

阿里云 OSS 标准存储（杭州）：
- 存储费用：0.12 元/GB/月 × 50 GB = 6 元/月
- 流量费用：根据实际访问量计算
- 请求费用：0.01 元/万次
```

### 9.2 CDN 加速成本（可选）
```
CDN 下行流量：
- 0-10 TB：0.24 元/GB
- 假设月流量 100 GB：24 元/月
```

---

## 十、实施时间表

| 阶段 | 任务 | 预计时间 | 负责人 |
|------|------|----------|--------|
| 准备期 | OSS 账号申请、配置 | 1 天 | 运维 |
| 开发期 | 迁移工具开发 | 3-5 天 | 后端开发 |
| 测试期 | 小批量测试、验证 | 2 天 | 测试+开发 |
| 执行期 | 全量迁移 | 1-2 天 | 运维+开发 |
| 观察期 | 监控、问题修复 | 7 天 | 全员 |
| 清理期 | 清理本地文件 | 30 天后 | 运维 |

---

## 十一、风险清单

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| OSS 上传失败 | 高 | 低 | 重试机制、错误日志 |
| 网络中断 | 中 | 中 | 断点续传、暂停恢复 |
| 数据不一致 | 高 | 低 | MD5 校验、人工复核 |
| 前端访问异常 | 高 | 低 | 降级方案、快速回滚 |
| 成本超支 | 中 | 低 | 预算监控、用量告警 |

---

## 十二、总结

本方案提供了从本地文件系统迁移到 OSS 的完整解决方案，包括：

✅ **数据库设计**：迁移日志表、状态追踪  
✅ **存储规划**：OSS 目录结构、命名规范  
✅ **迁移工具**：自动化批量迁移、验证、回滚  
✅ **监控告警**：实时监控、异常告警  
✅ **回滚方案**：快速恢复、数据安全  
✅ **成本控制**：成本估算、优化建议  

**下一步行动：**
1. 确认 OSS 账号和配置
2. 执行数据库迁移脚本
3. 开发迁移工具
4. 小批量测试验证
5. 全量迁移执行
