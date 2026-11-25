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
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({DataRelationRepository.class,DataRelationServiceImpl.class})
class DataRelationOverviewPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataRelationRepository repository;
    @Autowired DataRelationServiceImpl service;
    @Test void activeScanCursorTraversesDuplicateAndNullPagesWithoutOtherArchives() {
        long archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES ('654321') RETURNING id",Long.class);
        long other=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES ('654322') RETURNING id",Long.class);
        jdbc.update("""
                INSERT INTO app.mr_scan(archive_id,bah,pages,uploadflag,filename)
                SELECT ?,'654321',CASE WHEN n>53 THEN NULL ELSE n/2 END,1,'synthetic.jpg'
                FROM generate_series(1,56) n
                """,archive);
        jdbc.update("INSERT INTO app.mr_scan(archive_id,bah,pages,uploadflag,filename) VALUES (?,'654321',0,0,'inactive.jpg'),(?,'654322',0,1,'other.jpg')",archive,other);
        var ids=new java.util.ArrayList<Integer>();
        DataRelationRepository.ScanCursor cursor=null;
        do {
            var page=service.activeScanPage(archive,cursor==null?null:cursor.pages(),cursor==null?null:cursor.id(),7);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(7).allSatisfy(row-> {
                assertThat(((Number)row.get("archiveId")).longValue()).isEqualTo(archive);
                assertThat(row).doesNotContainKeys("filename","folder","oss_url","bah","sjh");
                ids.add(((Number)row.get("id")).intValue());
            });
            cursor=page.nextCursor();
        } while(cursor!=null && ids.size()<=56);
        assertThat(ids).containsExactlyElementsOf(jdbc.queryForList("SELECT id FROM app.mr_scan WHERE archive_id=? AND uploadflag<>0 ORDER BY pages,id",Integer.class,archive)).hasSize(56).doesNotHaveDuplicates();
        assertThatThrownBy(()->service.activeScanPage(archive,1,null,7)).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        assertThatThrownBy(()->service.activeScanPage(archive,null,null,101)).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
    }

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
