package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@Testcontainers(disabledWithoutDocker = true)
class ArchiveLinkRefreshPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.zjcxph.imgapi.mapper.ScanMapper mapper;

    @Test
    void changingShelfNumberRefreshesAnExistingLinkWithoutRewritingCodes() {
        Long first = jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES ('123','456') RETURNING id", Long.class);
        Long second = jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES ('789','987') RETURNING id", Long.class);
        Integer scan = jdbc.queryForObject("INSERT INTO app.mr_scan(bah,sjh,uploadflag,archive_id) VALUES ('123','456',1,?) RETURNING id", Integer.class, first);
        jdbc.update("UPDATE app.mr_scan SET bah='789',sjh='987' WHERE id=?", scan);
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, scan)).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT bah FROM app.mr_scan WHERE id=?", String.class, scan)).isEqualTo("789");
    }

    @Test
    void ambiguousBahClearsOldLinkInsteadOfKeepingAnUnrelatedArchive() {
        Long first = jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES ('123','456') RETURNING id", Long.class);
        jdbc.update("INSERT INTO app.mr_archive(bah,sjh) VALUES ('789','987'),('789','988')");
        Integer scan = jdbc.queryForObject("INSERT INTO app.mr_scan(bah,sjh,uploadflag,archive_id) VALUES ('123','456',1,?) RETURNING id", Integer.class, first);
        jdbc.update("UPDATE app.mr_scan SET bah='789',sjh=NULL WHERE id=?", scan);
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, scan)).isNull();
    }

    @Test
    void changingOnlyBahCannotKeepAConflictingShelfArchive() {
        Long archive = jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES ('123','456') RETURNING id", Long.class);
        Integer id = jdbc.queryForObject("INSERT INTO app.mr_scan(bah,sjh,uploadflag,archive_id) VALUES ('123','456',1,?) RETURNING id", Integer.class, archive);
        jdbc.update("UPDATE app.mr_scan SET bah='789' WHERE id=?", id);
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, id)).isNull();
    }

    @Test
    void mapperCanExplicitlyClearShelfNumberAndKeepBahUnchanged() {
        Long archive = jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES ('10000001','456') RETURNING id", Long.class);
        Integer id = jdbc.queryForObject("INSERT INTO app.mr_scan(bah,sjh,uploadflag,archive_id) VALUES ('10000001','456',1,?) RETURNING id", Integer.class, archive);
        com.zjcxph.imgapi.entity.Scan patch = new com.zjcxph.imgapi.entity.Scan();
        patch.setId(id);
        patch.setSjh("  ");
        new com.zjcxph.imgapi.service.impl.ScanServiceImpl(mapper, org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ImageTypeService.class)).update(patch);
        assertThat(jdbc.queryForObject("SELECT sjh FROM app.mr_scan WHERE id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT bah FROM app.mr_scan WHERE id=?", String.class, id)).isEqualTo("10000001");
    }
}
