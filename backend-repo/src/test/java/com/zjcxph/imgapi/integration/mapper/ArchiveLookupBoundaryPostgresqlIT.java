package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("病案主档组合查询与 Mapper 边界 PostgreSQL 16 集成测试")
class ArchiveLookupBoundaryPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScanMapper scanMapper;

    @Test
    @DisplayName("重复病案号缺少上架号时拒绝猜测，提供上架号后精确解析")
    void resolvesBySjhWhenBahIsAmbiguous() {
        Long firstArchiveId = insertArchive("10000000", "00001111");
        Long secondArchiveId = insertArchive("10000000", "00002222");

        assertThat(scanMapper.resolveArchiveId("10000000", "")).isNull();
        assertThat(scanMapper.resolveArchiveId("10000000", "00001111"))
                .isEqualTo(firstArchiveId);
        assertThat(scanMapper.resolveArchiveId("10000000", "00002222"))
                .isEqualTo(secondArchiveId);
    }

    @Test
    @DisplayName("非空上架号在数据库层保持唯一")
    void rejectsDuplicateSjhEvenWhenBahDiffers() {
        insertArchive("10000001", "00003333");

        assertThatThrownBy(() -> insertArchive("10000002", "00003333"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_mr_archive_sjh");
    }

    @Test
    @DisplayName("同时提供病案号和上架号时兼容查询必须满足组合条件")
    void legacyLookupRequiresBothCodesWhenBothAreProvided() {
        insertScan("10000010", "00001001", "match.jpg", 1, "combination-boundary");
        insertScan("10000010", "00001002", "same-bah.jpg", 2, "combination-boundary");
        insertScan("10000011", "00001001", "same-sjh.jpg", 3, "combination-boundary");
        insertScan("10000010", "00001001", "deleted.jpg", 4, "combination-boundary", 0);

        List<Scan> results = scanMapper.findByCode(
                "10000010",
                "10000010",
                "00001001",
                "1001"
        );

        assertThat(results).extracting(Scan::getFilename)
                .containsExactly("match.jpg");
    }

    @Test
    @DisplayName("历史补零形成多个等价上架号时兼容解析返回空")
    void compatibleSjhLookupRejectsEquivalentDuplicates() {
        Long shortCodeArchiveId = insertArchive("10000020", "1234");
        Long paddedCodeArchiveId = insertArchive("10000021", "00001234");

        assertThat(scanMapper.resolveArchiveIdBySearchCode("", "1234")).isNull();
        assertThat(scanMapper.resolveArchiveId("", "1234"))
                .isEqualTo(shortCodeArchiveId);
        assertThat(scanMapper.resolveArchiveId("", "00001234"))
                .isEqualTo(paddedCodeArchiveId);
    }

    @Test
    @DisplayName("病案号和上架号均为空时兼容查询不允许返回全表")
    void blankLegacyLookupDoesNotReturnAllRows() {
        insertScan("10000030", "00003001", "must-not-leak.jpg", 1, "blank-boundary");

        assertThat(scanMapper.findByCode("", "", "", "")).isEmpty();
    }

    @Test
    @DisplayName("分页查询保持稳定顺序并在越界偏移时返回空")
    void paginationIsStableAndSafePastEnd() {
        insertScan("10000040", "00004001", "page-1.jpg", 1, "pagination-boundary");
        insertScan("10000041", "00004002", "page-2.jpg", 2, "pagination-boundary");
        insertScan("10000042", "00004003", "page-3.jpg", 3, "pagination-boundary");

        ScanRequest request = new ScanRequest();
        request.setFolder("pagination-boundary");

        assertThat(scanMapper.countByCondition(request)).isEqualTo(3);
        assertThat(scanMapper.findByConditionWithPagination(request, 1, 2))
                .extracting(Scan::getFilename)
                .containsExactly("page-2.jpg", "page-3.jpg");
        assertThat(scanMapper.findByConditionWithPagination(request, 3, 2)).isEmpty();
    }

    private Long insertArchive(String bah, String sjh) {
        return jdbcTemplate.queryForObject("""
                insert into app.mr_archive (bah, sjh)
                values (?, ?)
                returning id
                """, Long.class, bah, sjh);
    }

    private void insertScan(String bah, String sjh, String filename, int pages, String folder) {
        insertScan(bah, sjh, filename, pages, folder, 1);
    }

    private void insertScan(
            String bah,
            String sjh,
            String filename,
            int pages,
            String folder,
            int uploadFlag
    ) {
        jdbcTemplate.update("""
                insert into app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                "BRXH-" + filename,
                bah,
                sjh,
                filename,
                pages,
                uploadFlag,
                folder
        );
    }
}
