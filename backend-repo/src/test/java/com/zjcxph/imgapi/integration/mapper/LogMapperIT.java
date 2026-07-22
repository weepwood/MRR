package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditCountDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditTrendDTO;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "mybatis.mapper-locations=classpath*:mapper/*.xml"
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("LogMapper 图片审计聚合集成测试 (PostgreSQL 16)")
class LogMapperIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imgapi")
                    .withUsername("imgapi")
                    .withPassword("imgapi");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> appendJdbcParameter(
                POSTGRES.getJdbcUrl(), "currentSchema=app"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    private static String appendJdbcParameter(String jdbcUrl, String parameter) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + parameter;
    }

    @Autowired
    private LogMapper logMapper;

    @BeforeEach
    void setUp() {
        insert("alice", "VIEW_IMAGE", "00789508", "200", 11L, LocalDateTime.of(2026, 7, 10, 9, 0));
        insert("alice", "DOWNLOAD", "00789508", "404", 30L, LocalDateTime.of(2026, 7, 10, 10, 0));
        insert("bob", "VIEW_OSS_IMAGE", "9", "302", 20L, LocalDateTime.of(2026, 7, 10, 11, 0));
        insert("bob", "LIST", "00112233", "500", 40L, LocalDateTime.of(2026, 7, 11, 9, 0));
        insert(" ", "VIEW_IMAGE", null, "200", 50L, LocalDateTime.of(2026, 7, 11, 10, 0));
        insert("admin", "UPDATE_USER", "42", "500", 100L, LocalDateTime.of(2026, 7, 11, 11, 0));
        insert("legacy", null, "00999999", "500", 100L, LocalDateTime.of(2026, 7, 11, 12, 0));
    }

    @Test
    @DisplayName("严格限定四种图片动作并正确聚合")
    void aggregatesOnlyImageAuditActions() {
        ImageAuditAnalyticsDTO overview = logMapper.getImageAuditOverview(
                null, null, null, null, null, null, null);
        List<ImageAuditTrendDTO> trend = logMapper.getImageAuditTrend(
                null, null, null, null, null, null, null);
        List<ImageAuditCountDTO> actions = logMapper.getImageAuditActionDistribution(
                null, null, null, null, null, null, null);
        List<ImageAuditCountDTO> users = logMapper.getTopImageAuditUsers(
                null, null, null, null, null, null, null);

        assertThat(overview.getTotalAccesses()).isEqualTo(5);
        assertThat(overview.getUniqueUsers()).isEqualTo(2);
        assertThat(overview.getUniqueTargets()).isEqualTo(3);
        assertThat(overview.getAbnormalAccesses()).isEqualTo(2);
        assertThat(overview.getAverageDurationMs()).isEqualTo(30.2D);
        assertThat(trend).extracting(ImageAuditTrendDTO::getDate)
                .containsExactly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11));
        assertThat(trend).extracting(ImageAuditTrendDTO::getCount).containsExactly(3L, 2L);
        assertThat(actions).extracting(ImageAuditCountDTO::getLabel)
                .containsExactly("VIEW_IMAGE", "DOWNLOAD", "LIST", "VIEW_OSS_IMAGE");
        assertThat(actions).extracting(ImageAuditCountDTO::getCount).containsExactly(2L, 1L, 1L, 1L);
        assertThat(users).extracting(ImageAuditCountDTO::getLabel).containsExactly("alice", "bob");
        assertThat(users).extracting(ImageAuditCountDTO::getCount).containsExactly(2L, 2L);
    }

    @Test
    @DisplayName("列表、计数和聚合复用相同的动作及筛选口径")
    void listCountAndAnalyticsShareFilters() {
        List<Log> list = logMapper.searchImageAudit(
                null, "bob", null, null, null, null, null, 20, 0);
        int count = logMapper.countImageAudit(
                null, "bob", null, null, null, null, null);
        ImageAuditAnalyticsDTO overview = logMapper.getImageAuditOverview(
                null, "bob", null, null, null, null, null);
        List<ImageAuditCountDTO> actions = logMapper.getImageAuditActionDistribution(
                null, "bob", null, null, null, null, null);

        assertThat(list).hasSize(2);
        assertThat(count).isEqualTo(2);
        assertThat(overview.getTotalAccesses()).isEqualTo(2);
        assertThat(actions).extracting(ImageAuditCountDTO::getLabel)
                .containsExactly("LIST", "VIEW_OSS_IMAGE", "DOWNLOAD", "VIEW_IMAGE");
        assertThat(actions).extracting(ImageAuditCountDTO::getCount).containsExactly(1L, 1L, 0L, 0L);
    }

    @Test
    @DisplayName("趋势只保留最近十四个有记录日期并按日期升序")
    void trendKeepsLatestFourteenDatesAscending() {
        for (int day = 1; day <= 15; day++) {
            insert("trend", "VIEW_IMAGE", "trend-" + day, "200", 1L,
                    LocalDateTime.of(2026, 6, day, 8, 0));
        }

        List<ImageAuditTrendDTO> trend = logMapper.getImageAuditTrend(
                null, null, null, null, null, null, null);

        assertThat(trend).hasSize(14);
        assertThat(trend.getFirst().getDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(trend.getLast().getDate()).isEqualTo(LocalDate.of(2026, 7, 11));
    }

    @Test
    @DisplayName("高频用户固定返回前五名")
    void topUsersIsLimitedToFive() {
        for (int i = 0; i < 6; i++) {
            insert("user-" + i, "VIEW_IMAGE", "target-" + i, "200", 1L,
                    LocalDateTime.of(2026, 7, 12, 8 + i, 0));
        }

        List<ImageAuditCountDTO> users = logMapper.getTopImageAuditUsers(
                null, null, null, null, null, null, null);

        assertThat(users).hasSize(5);
        assertThat(users).extracting(ImageAuditCountDTO::getLabel)
                .containsExactly("alice", "bob", "user-0", "user-1", "user-2");
    }

    private void insert(String username, String action, String target, String status, long duration,
                        LocalDateTime accessTime) {
        Log log = new Log();
        log.setUsername(username);
        log.setClientIp("127.0.0.1");
        log.setRequestUri(action == null ? "/api/v1/img/00999999" : "/api/test/" + action);
        log.setMethod("GET");
        log.setAccessTime(Date.from(accessTime.atZone(ZoneId.systemDefault()).toInstant()));
        log.setResponseStatus(status);
        log.setExecuteTime(duration);
        log.setAuditAction(action);
        log.setAuditTarget(target);
        log.setAuditDescription("test");
        logMapper.insert(log);
    }
}
