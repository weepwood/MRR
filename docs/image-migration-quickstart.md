# 图片迁移到 OSS - 快速开始指南

## 一、前置准备

### 1.1 OSS 账号配置

1. **登录阿里云控制台**
   - 访问 https://oss.console.aliyun.com
   - 创建 Bucket（存储桶）
   - 记录以下信息：
     - Endpoint: `oss-cn-hangzhou.aliyuncs.com`
     - Bucket Name: `mrr-medical-records`
     - AccessKey ID
     - AccessKey Secret

2. **配置 Bucket 权限**
   ```
   推荐配置：
   - 读写权限：私有（private）
   - 防盗链：启用
   - CORS：允许前端域名访问
   ```

3. **创建 RAM 用户（推荐）**
   ```
   不要使用主账号 AccessKey！
   创建专门的 RAM 用户，只授予 OSS 读写权限
   ```

### 1.2 环境配置

编辑 `backend-repo/src/main/resources/application.properties`：

```properties
# ============================================
# OSS 配置
# ============================================
oss.endpoint=oss-cn-hangzhou.aliyuncs.com
oss.bucket=mrr-medical-records
oss.access-key-id=${OSS_ACCESS_KEY_ID:your-access-key-id}
oss.access-key-secret=${OSS_ACCESS_KEY_SECRET:your-access-key-secret}
oss.region=cn-hangzhou
oss.base-url=https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com
oss.enable-cdn=false
oss.cdn-domain=

# 迁移配置
migration.batch-size=100
migration.concurrent-threads=5
migration.retry-times=3
```

**或使用环境变量：**
```bash
export OSS_ACCESS_KEY_ID="your-access-key-id"
export OSS_ACCESS_KEY_SECRET="your-access-key-secret"
```

---

## 二、数据库初始化

### 2.1 执行迁移脚本

```bash
# PostgreSQL
psql -U postgres -d imgapi -f mrr-db/migration_to_oss.sql

# 或使用 Docker
docker exec -i postgres-container psql -U postgres -d imgapi < mrr-db/migration_to_oss.sql
```

### 2.2 验证表结构

```sql
-- 检查表是否创建成功
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'app' 
  AND table_name IN ('image_migration_log', 'mr_scan');

-- 检查视图
SELECT table_name 
FROM information_schema.views 
WHERE table_schema = 'app';

-- 初始化迁移日志
SELECT app.init_migration_logs();

-- 查看待迁移数量
SELECT COUNT(*) FROM app.v_pending_migrations;
```

---

## 三、后端开发

### 3.1 添加 Maven 依赖

在 `backend-repo/pom.xml` 中添加：

```xml
<!-- 阿里云 OSS SDK -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
```

### 3.2 创建配置类

创建文件：`backend-repo/src/main/java/com/zjcxph/imgapi/config/OssProperties.java`

```java
package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String baseUrl;
    private Boolean enableCdn = false;
    private String cdnDomain;
    
    public String getCdnBaseUrl() {
        if (enableCdn && cdnDomain != null && !cdnDomain.isEmpty()) {
            return "https://" + cdnDomain;
        }
        return baseUrl;
    }
}
```

### 3.3 创建 OSS 服务类

创建文件：`backend-repo/src/main/java/com/zjcxph/imgapi/service/OssService.java`

```java
package com.zjcxph.imgapi.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.zjcxph.imgapi.config.OssProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

@Slf4j
@Service
public class OssService {
    
    private final OssProperties ossProperties;
    private OSS ossClient;
    
    public OssService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }
    
    @PostConstruct
    public void init() {
        ossClient = new OSSClientBuilder().build(
            ossProperties.getEndpoint(),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret()
        );
        log.info("OSS client initialized: {}", ossProperties.getBucket());
    }
    
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS client shutdown");
        }
    }
    
    /**
     * 上传文件到 OSS
     */
    public String uploadFile(File localFile, String ossKey) {
        try (InputStream inputStream = new FileInputStream(localFile)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(localFile.length());
            metadata.setContentType("image/jpeg");
            
            PutObjectResult result = ossClient.putObject(
                ossProperties.getBucket(),
                ossKey,
                inputStream,
                metadata
            );
            
            log.info("File uploaded to OSS: {} (ETag: {})", ossKey, result.getETag());
            return generatePublicUrl(ossKey);
            
        } catch (Exception e) {
            log.error("Failed to upload file to OSS: {}", ossKey, e);
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成公共访问 URL
     */
    public String generatePublicUrl(String ossKey) {
        return ossProperties.getCdnBaseUrl() + "/" + ossKey;
    }
    
    /**
     * 生成签名 URL（私有 Bucket 使用）
     */
    public String generatePresignedUrl(String ossKey, int expireSeconds) {
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
        URL url = ossClient.generatePresignedUrl(
            ossProperties.getBucket(),
            ossKey,
            expiration
        );
        return url.toString();
    }
    
    /**
     * 计算文件 MD5
     */
    public String calculateMd5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return DigestUtils.md5Hex(fis);
        } catch (Exception e) {
            log.error("Failed to calculate MD5 for file: {}", file.getPath(), e);
            return null;
        }
    }
    
    /**
     * 删除 OSS 文件
     */
    public void deleteFile(String ossKey) {
        try {
            ossClient.deleteObject(ossProperties.getBucket(), ossKey);
            log.info("File deleted from OSS: {}", ossKey);
        } catch (Exception e) {
            log.error("Failed to delete file from OSS: {}", ossKey, e);
            throw new RuntimeException("OSS delete failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查文件是否存在
     */
    public boolean doesObjectExist(String ossKey) {
        return ossClient.doesObjectExist(ossProperties.getBucket(), ossKey);
    }
}
```

### 3.4 创建 Mapper

创建文件：`backend-repo/src/main/java/com/zjcxph/imgapi/mapper/MigrationLogMapper.java`

```java
package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.MigrationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MigrationLogMapper {
    
    @Insert("INSERT INTO app.image_migration_log (scan_id, local_path, migration_status, file_size, checksum_md5) " +
            "VALUES (#{scanId}, #{localPath}, #{migrationStatus}, #{fileSize}, #{checksumMd5})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MigrationLog log);
    
    @Update("UPDATE app.image_migration_log SET " +
            "migration_status = #{migrationStatus}, " +
            "oss_url = #{ossUrl}, " +
            "error_message = #{errorMessage}, " +
            "migrated_at = #{migratedAt}, " +
            "verified_at = #{verifiedAt}, " +
            "updated_at = NOW() " +
            "WHERE scan_id = #{scanId}")
    int updateByScanId(MigrationLog log);
    
    @Select("SELECT * FROM app.image_migration_log WHERE migration_status = #{status} LIMIT #{limit}")
    List<MigrationLog> findByStatus(@Param("status") String status, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM app.image_migration_log WHERE migration_status = #{status}")
    int countByStatus(@Param("status") String status);
}
```

### 3.5 创建实体类

创建文件：`backend-repo/src/main/java/com/zjcxph/imgapi/entity/MigrationLog.java`

```java
package com.zjcxph.imgapi.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MigrationLog {
    private Long id;
    private Integer scanId;
    private String localPath;
    private String ossUrl;
    private String migrationStatus;
    private String errorMessage;
    private Long fileSize;
    private String checksumMd5;
    private LocalDateTime migratedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.6 创建迁移服务

创建文件：`backend-repo/src/main/java/com/zjcxph/imgapi/service/MigrationService.java`

```java
package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.entity.MigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.MigrationLogMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MigrationService {
    
    private final ScanMapper scanMapper;
    private final MigrationLogMapper migrationLogMapper;
    private final OssService ossService;
    private final ImageProperties imageProperties;
    private final OssProperties ossProperties;
    
    @Value("${migration.batch-size:100}")
    private int batchSize;
    
    @Value("${migration.concurrent-threads:5}")
    private int concurrentThreads;
    
    public MigrationService(
            ScanMapper scanMapper,
            MigrationLogMapper migrationLogMapper,
            OssService ossService,
            ImageProperties imageProperties,
            OssProperties ossProperties) {
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.ossService = ossService;
        this.imageProperties = imageProperties;
        this.ossProperties = ossProperties;
    }
    
    /**
     * 批量迁移
     */
    @Transactional
    public MigrationResult batchMigrate(int batchSize) {
        MigrationResult result = new MigrationResult();
        result.setStartTime(LocalDateTime.now());
        
        // 查询待迁移的记录
        List<MigrationLog> pendingLogs = migrationLogMapper.findByStatus("pending", batchSize);
        result.setTotalCount(pendingLogs.size());
        
        log.info("Starting batch migration: {} files", pendingLogs.size());
        
        for (MigrationLog logEntry : pendingLogs) {
            try {
                boolean success = migrateSingleImage(logEntry);
                if (success) {
                    result.incrementSuccess();
                } else {
                    result.incrementFailed();
                }
            } catch (Exception e) {
                log.error("Migration failed for scan_id: {}", logEntry.getScanId(), e);
                result.incrementFailed();
                updateMigrationStatus(logEntry.getScanId(), "failed", null, e.getMessage(), null);
            }
        }
        
        result.setEndTime(LocalDateTime.now());
        log.info("Batch migration completed: {}", result);
        
        return result;
    }
    
    /**
     * 迁移单个图片
     */
    private boolean migrateSingleImage(MigrationLog logEntry) {
        Integer scanId = logEntry.getScanId();
        
        // 1. 查询扫描记录
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            log.warn("Scan record not found: {}", scanId);
            updateMigrationStatus(scanId, "failed", null, "Scan record not found", null);
            return false;
        }
        
        // 2. 构建本地文件路径
        String localPath = buildLocalPath(scan);
        File localFile = new File(localPath);
        
        if (!localFile.exists()) {
            log.warn("Local file not found: {}", localPath);
            updateMigrationStatus(scanId, "failed", null, "Local file not found", null);
            return false;
        }
        
        // 3. 计算 MD5
        String md5 = ossService.calculateMd5(localFile);
        
        // 4. 构建 OSS Key
        String ossKey = buildOssKey(scan);
        
        // 5. 检查是否已存在
        if (ossService.doesObjectExist(ossKey)) {
            log.info("File already exists in OSS: {}", ossKey);
            String ossUrl = ossService.generatePublicUrl(ossKey);
            updateMigrationStatus(scanId, "success", ossUrl, null, md5);
            return true;
        }
        
        // 6. 上传到 OSS
        log.info("Uploading to OSS: {} -> {}", localPath, ossKey);
        updateMigrationStatus(scanId, "migrating", null, null, md5);
        
        String ossUrl = ossService.uploadFile(localFile, ossKey);
        
        // 7. 更新状态
        updateMigrationStatus(scanId, "success", ossUrl, null, md5);
        
        log.info("Migration successful: scan_id={}, oss_url={}", scanId, ossUrl);
        return true;
    }
    
    /**
     * 构建本地文件路径
     */
    private String buildLocalPath(Scan scan) {
        String parentFolder = scan.getFolder().substring(0, 5);
        String folderName = scan.getBrxh() + "-" + scan.getBah();
        
        Path path = Paths.get(
            imageProperties.getBasePath(),
            parentFolder,
            scan.getFolder(),
            folderName,
            scan.getFilename()
        );
        
        return path.toString();
    }
    
    /**
     * 构建 OSS Key
     */
    private String buildOssKey(Scan scan) {
        String parentFolder = scan.getFolder().substring(0, 5);
        String folderName = scan.getBrxh() + "-" + scan.getBah();
        
        return String.format("images/%s/%s/%s/%s",
            parentFolder,
            scan.getFolder(),
            folderName,
            scan.getFilename()
        );
    }
    
    /**
     * 更新迁移状态
     */
    private void updateMigrationStatus(Integer scanId, String status, 
                                       String ossUrl, String errorMessage, String md5) {
        MigrationLog logEntry = new MigrationLog();
        logEntry.setScanId(scanId);
        logEntry.setMigrationStatus(status);
        logEntry.setOssUrl(ossUrl);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setChecksumMd5(md5);
        
        if ("success".equals(status)) {
            logEntry.setMigratedAt(LocalDateTime.now());
        } else if ("verified".equals(status)) {
            logEntry.setVerifiedAt(LocalDateTime.now());
        }
        
        migrationLogMapper.updateByScanId(logEntry);
    }
    
    /**
     * 迁移结果
     */
    @Data
    public static class MigrationResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalCount;
        private int successCount;
        private int failedCount;
        
        public void incrementSuccess() {
            this.successCount++;
        }
        
        public void incrementFailed() {
            this.failedCount++;
        }
        
        public double getSuccessRate() {
            if (totalCount == 0) return 0;
            return (double) successCount / totalCount * 100;
        }
    }
}
```

---

## 四、执行迁移

### 4.1 测试模式（小批量）

```bash
# 启动后端服务
cd backend-repo
mvn spring-boot:run

# 调用迁移接口（测试 10 条）
curl -X POST http://localhost:18045/v1/migration-api/start \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "batchSize": 10,
    "testMode": true
  }'
```

### 4.2 全量迁移

```bash
# 分批执行，每批 1000 条
curl -X POST http://localhost:18045/v1/migration-api/start \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "batchSize": 1000,
    "testMode": false
  }'
```

### 4.3 监控进度

```bash
# 查询迁移状态
curl http://localhost:18045/v1/migration-api/status \
  -H "Authorization: Bearer YOUR_TOKEN"

# 查询迁移日志
curl "http://localhost:18045/v1/migration-api/logs?page=1&size=50" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 五、验证迁移结果

### 5.1 数据库验证

```sql
-- 查看迁移统计
SELECT * FROM app.v_migration_statistics;

-- 查看失败的记录
SELECT * FROM app.image_migration_log 
WHERE migration_status = 'failed'
LIMIT 10;

-- 随机抽样验证
SELECT s.id, s.bah, s.filename, s.oss_url, l.checksum_md5
FROM app.mr_scan s
JOIN app.image_migration_log l ON s.id = l.scan_id
WHERE l.migration_status = 'success'
ORDER BY RANDOM()
LIMIT 10;
```

### 5.2 前端验证

1. 打开浏览器开发者工具
2. 访问任意病案页面
3. 检查 Network 标签
4. 确认图片从 OSS URL 加载
5. 检查是否有 404 错误

### 5.3 完整性验证

```bash
# 调用验证接口
curl -X POST http://localhost:18045/v1/migration-api/verify \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sampleSize": 100,
    "verifyChecksum": true
  }'
```

---

## 六、常见问题

### Q1: 上传失败怎么办？
```
检查：
1. OSS AccessKey 是否正确
2. Bucket 权限配置
3. 网络连接
4. 文件大小限制

解决：
- 查看错误日志
- 使用重试机制
- 手动重新迁移失败的记录
```

### Q2: 迁移速度慢？
```
优化：
1. 增加并发线程数：migration.concurrent-threads=10
2. 增大批次大小：migration.batch-size=500
3. 使用 CDN 加速
4. 选择离服务器最近的 OSS Region
```

### Q3: 如何回滚？
```sql
-- 清空 OSS URL
UPDATE app.mr_scan 
SET oss_url = NULL,
    migration_status = 'not_migrated'
WHERE migration_status IN ('migrated', 'verified');

-- 或删除迁移日志
DELETE FROM app.image_migration_log;
```

---

## 七、下一步

✅ 完成上述步骤后：
1. 持续监控 OSS 访问情况
2. 观察前端加载速度
3. 收集用户反馈
4. 30 天后清理本地文件

📚 详细文档：
- [完整迁移方案](./image-migration-to-oss-plan.md)
- [数据库迁移脚本](../../mrr-db/migration_to_oss.sql)
