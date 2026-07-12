package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@TestPropertySource(properties = {
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "spring.flyway.enabled=false"
})
@Sql("classpath:schema-itest.sql")
@DisplayName("ScanMapper 集成测试 (H2)")
class ScanMapperIntegrationTest {

    @Autowired
    private ScanMapper scanMapper;

    private Scan sampleScan() {
        Scan s = new Scan();
        s.setBrxh("605746");
        s.setBah("00789508");
        s.setSjh("SJH001");
        s.setFilename("test.jpg");
        s.setBtype(1);
        s.setPages(2);
        s.setOpenerNo("OP001");
        s.setUploadDate(new Date());
        s.setUploadFlag(1);
        s.setFolder("25.03.15");
        return s;
    }

    @BeforeEach
    void setUp() {
        scanMapper.insert(sampleScan());
    }

    @Test
    @DisplayName("CRUD — 插入、查询、更新、软删除")
    void crud() {
        Scan inserted = scanMapper.findById(1);
        assertThat(inserted).isNotNull();
        assertThat(inserted.getBah()).isEqualTo("00789508");
        assertThat(inserted.getBrxh()).isEqualTo("605746");

        inserted.setPages(5);
        scanMapper.update(inserted);
        Scan updated = scanMapper.findById(1);
        assertThat(updated.getPages()).isEqualTo(5);

        scanMapper.softDeleteById(1);
        Scan deleted = scanMapper.findById(1);
        assertThat(deleted.getUploadFlag()).isZero();
    }

    @Test
    @DisplayName("findByBah — 按病案号查询")
    void findByBah() {
        List<Scan> results = scanMapper.findByBah("00789508");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFilename()).isEqualTo("test.jpg");
    }

    @Test
    @DisplayName("findByBrxh — 按住院号查询")
    void findByBrxh() {
        List<Scan> results = scanMapper.findByBrxh("605746");
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("countByCondition — 按条件计数")
    void countByCondition() {
        ScanRequest param = new ScanRequest();
        param.setBah("00789508");
        int count = scanMapper.countByCondition(param);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("countMigrationStats — 迁移统计汇总")
    void countMigrationStats() {
        Map<String, Object> stats = scanMapper.countMigrationStats();
        assertThat(stats).containsKey("TOTAL");
        assertThat(((Number) stats.get("TOTAL")).longValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateOssInfo — 更新 OSS 信息")
    void updateOssInfo() {
        int affected = scanMapper.updateOssInfo(1, "https://oss/test.jpg", 1024L, "md5hash", "migrated");
        assertThat(affected).isEqualTo(1);
        Scan updated = scanMapper.findById(1);
        assertThat(updated).isNotNull();
        assertThat(updated.getOssUrl()).isEqualTo("https://oss/test.jpg");
        assertThat(updated.getFileSize()).isEqualTo(1024L);
        assertThat(updated.getChecksumMd5()).isEqualTo("md5hash");
        assertThat(updated.getMigrationStatus()).isEqualTo("migrated");
    }

    @Test
    @DisplayName("findPendingMigration — 待迁移列表（排除 uploadFlag=0）")
    void findPendingMigration() {
        Scan s2 = sampleScan();
        s2.setBrxh("999999");
        s2.setBah("99999999");
        s2.setUploadFlag(0);
        scanMapper.insert(s2);

        List<Scan> pending = scanMapper.findPendingMigration(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getBrxh()).isEqualTo("605746");
    }
}
