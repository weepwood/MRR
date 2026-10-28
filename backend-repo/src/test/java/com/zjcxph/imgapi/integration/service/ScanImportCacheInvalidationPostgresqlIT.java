package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.config.CacheConfig;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;
import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.ImageTypeService;
import com.zjcxph.imgapi.service.ScanDataExchangeService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扫描记录数据交换导入与查询缓存的一致性集成测试。
 *
 * <p>背景：数据交换导入通过 JdbcTemplate 直接写入 {@code mr_scan}，绕过了
 * {@code ScanServiceImpl} 写路径上的 {@code @CacheEvict} 切面。若导入后不清理
 * {@code scanByBah} / {@code scanLookupByBah} 等缓存，已查询过的病案号将在缓存
 * TTL 内继续返回旧数据。</p>
 */
@MybatisTest
@Import({
        TabularImportFileReader.class,
        ScanDataExchangeService.class,
        ScanServiceImpl.class,
        CacheConfig.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@DisplayName("扫描记录导入后清理查询缓存 PostgreSQL 集成测试")
class ScanImportCacheInvalidationPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScanDataExchangeService scanDataExchangeService;

    @Autowired
    private ScanService scanService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ImageTypeService imageTypeService;

    @BeforeEach
    void resetTablesAndCaches() {
        resetTables();
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * 集成测试共享同一个数据库实例，部分既有用例（如 {@code ScanMapperPostgresqlIT}）
     * 假定 {@code mr_scan} 为空。本类不清理就会让它们的行数断言失败，因此用完即清。
     */
    @AfterEach
    void removeImportedRows() {
        resetTables();
    }

    private void resetTables() {
        jdbcTemplate.execute("""
                truncate table app.mr_archive_box_record, app.mr_scan,
                               app.mr_statistics, app.mr_archive
                restart identity cascade
                """);
    }

    @Test
    @DisplayName("导入更新扫描记录后，按病案号查询立即返回新数据而非缓存旧数据")
    void evictsScanLookupCacheAfterImport() throws Exception {
        importScans(false, """
                sjh,bah,brxh,folder,filename,btype,filesize
                100,1,B1,F1,a.jpg,0,10
                """);

        ArchiveLookupResult first = scanService.getImageLookupByBAH("1", "1");
        assertThat(first.resultCount()).isEqualTo(1);
        assertThat(lookupCache().get("1:1"))
                .as("预热断言：首次查询结果应已写入 scanLookupByBah 缓存")
                .isNotNull();

        DataExchangeImportResult result = importScans(false, """
                sjh,bah,brxh,folder,filename,btype,filesize
                100,1,B1,F1,a.jpg,5,99
                100,1,B1,F1,b.jpg,0,20
                """);
        assertThat(result.canImport()).isTrue();
        assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.updatedRows()).isEqualTo(1);

        ArchiveLookupResult second = scanService.getImageLookupByBAH("1", "1");
        assertThat(second.resultCount())
                .as("导入提交后应立即看到新增的扫描记录，而不是缓存中的旧结果")
                .isEqualTo(2);
        assertThat(second.scans())
                .filteredOn(scan -> "a.jpg".equals(scan.getFilename()))
                .first()
                .satisfies(scan -> {
                    assertThat(scan.getBtype()).isEqualTo(5);
                    assertThat(scan.getFileSize()).isEqualTo(99L);
                });
    }

    private Cache lookupCache() {
        Cache cache = cacheManager.getCache("scanLookupByBah");
        assertThat(cache).as("CacheConfig 应注册 scanLookupByBah 缓存").isNotNull();
        return cache;
    }

    private DataExchangeImportResult importScans(boolean dryRun, String content) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scans.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
        try {
            return scanDataExchangeService.importScans(file, dryRun);
        } catch (IOException e) {
            throw new AssertionError("读取导入文件失败", e);
        }
    }
}
