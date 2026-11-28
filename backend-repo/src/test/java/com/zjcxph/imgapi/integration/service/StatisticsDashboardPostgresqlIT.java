package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.StatisticsMapper;
import com.zjcxph.imgapi.service.impl.StatisticsServiceImpl;
import com.zjcxph.imgapi.controller.StatisticsController;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@org.springframework.test.context.TestPropertySource(properties = "mybatis.mapper-locations=classpath*:mapper/*.xml")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatisticsDashboardPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired StatisticsMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void boundedDashboardMatchesExistingCanonicalGroupingAndOrder() {
        // The isolated test transaction rolls back all synthetic fixtures.
        // Reset only the disposable integration database, inside that same transaction.
        jdbc.execute("TRUNCATE TABLE app.mr_statistics RESTART IDENTITY CASCADE");
        jdbc.update("""
                insert into app.mr_statistics(bah,pages,date,type)
                select i::text, i % 50 + 1, '2026-01-01', 'synthetic'
                from generate_series(1,20000) i
                """);
        jdbc.update("insert into app.mr_statistics(bah,pages,date,type) values ('00000001',99,'2026-01-01','synthetic')");
        long start=System.nanoTime();
        var full=mapper.getBAHStatistics();
        long fullMs=(System.nanoTime()-start)/1000000;
        start=System.nanoTime();
        var bounded=mapper.getDashboardBAHStatistics();
        long boundedMs=(System.nanoTime()-start)/1000000;
        assertThat(full).hasSize(20000);
        assertThat(bounded).hasSize(10).containsExactlyElementsOf(full.subList(0,10));
        assertThat(bounded.getFirst().getRecordCount()).isEqualTo(2L);
        assertThat(bounded.getFirst().getTotalPages()).isEqualTo(101L);
        var dashboard=new StatisticsController(new StatisticsServiceImpl(mapper)).getDashboardData();
        assertThat(dashboard.getData().get("topBAH")).isEqualTo(bounded);
        System.out.printf("MRR_STATISTICS_DASHBOARD rows=%d/%d elapsed_ms=%d/%d%n",full.size(),bounded.size(),fullMs,boundedMs);
    }
}
