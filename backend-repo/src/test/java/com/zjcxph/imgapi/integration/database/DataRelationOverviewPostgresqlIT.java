package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.DataRelationRepository;
import com.zjcxph.imgapi.service.impl.DataRelationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({DataRelationRepository.class,DataRelationServiceImpl.class})
@Testcontainers(disabledWithoutDocker=true)
class DataRelationOverviewPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataRelationRepository repository;
    @Autowired DataRelationServiceImpl service;
    @Test void overviewUsesPostgresStatisticsAndMarksEveryRelationEstimate() {
        jdbc.update("INSERT INTO app.mr_scan(bah,filename,folder,uploadflag) VALUES ('321','synthetic-overview.jpg','synthetic',1)");
        jdbc.execute("ANALYZE app.mr_scan");
        jdbc.execute("ANALYZE app.mr_archive");
        jdbc.execute("ANALYZE app.mr_statistics");
        jdbc.execute("ANALYZE app.mr_archive_box_record");
        var relations=(List<Map<String,Object>>)service.getOverview().get("relations");
        assertThat(relations).allSatisfy(row->assertThat(row.get("estimated")).isEqualTo(true));
        var scans=relations.stream().filter(row->"mr_scan".equals(row.get("tableName"))).findFirst().orElseThrow();
        assertThat(scans.get("source")).isEqualTo("POSTGRES_STATISTICS");
        assertThat(repository.archiveCoverage("mr_statistics")).containsKeys("statistics_available","analyzed_at");
        assertThatThrownBy(()->repository.archiveCoverage("mr_scan; DELETE FROM app.mr_scan")).isInstanceOf(IllegalArgumentException.class);
    }
}
