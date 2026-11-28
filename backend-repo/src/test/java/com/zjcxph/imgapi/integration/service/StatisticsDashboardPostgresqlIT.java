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

    @Autowired org.apache.ibatis.session.SqlSessionFactory sessions;

    @Test void dailySummaryMatchesListAcrossLegacyDateSeparators() {
        String type = "synthetic-date-" + java.util.UUID.randomUUID();
        jdbc.update("""
                insert into app.mr_statistics(bah,pages,date,type) values
                ('97000001',3,'2098-01-15',?), ('97000002',7,'2098/01/15',?),
                ('97000003',11,'2098/01/16',?), ('97000004',17,'2098-01-17',?)
                """,type,type,type,type);
        var service = new StatisticsServiceImpl(mapper);
        assertThat(service.getTotalCountByCondition(null,null,null,type,"2098-01-15","2098-01-16")).isEqualTo(3L);
        var daily = service.getDateStatisticsByCondition("2098-01-15","2098-01-16",type);
        assertThat(daily).containsExactly(new com.zjcxph.imgapi.dto.resp.DateStatisticsDTO("2098-01-15",2L,10L),
                new com.zjcxph.imgapi.dto.resp.DateStatisticsDTO("2098-01-16",1L,11L));
    }

    @Test void paginationSortHasUniqueTieBreakerAndPreservesRows() {
        String type = "synthetic-order-" + java.util.UUID.randomUUID();
        jdbc.update("""
                insert into app.mr_statistics(bah,pages,date,type)
                select '97000005',10,'2098-01-15',? from generate_series(1,12)
                """,type);
        var args = new java.util.HashMap<String,Object>();
        args.put("offset",0);args.put("limit",4);args.put("type",type);args.put("sortBy","pages");args.put("sortOrder","desc");
        var sql = sessions.getConfiguration().getMappedStatement(StatisticsMapper.class.getName()+".findWithConditionAndPagination")
                .getBoundSql(args).getSql().replaceAll("\\s+"," ");
        assertThat(sql).matches("(?is).*ORDER BY pages DESC , bah ASC, id ASC LIMIT.*");
        var ids = jdbc.queryForList("select id from app.mr_statistics where type=? order by id",Long.class,type);
        for(int offset=0;offset<12;offset+=4) {
            var page = mapper.findWithConditionAndPagination(offset,4,null,null,null,type,null,null,"pages","desc");
            assertThat(page.stream().map(com.zjcxph.imgapi.entity.Statistics::getId).toList())
                    .containsExactlyElementsOf(ids.subList(offset,offset+4));
        }
    }

}
