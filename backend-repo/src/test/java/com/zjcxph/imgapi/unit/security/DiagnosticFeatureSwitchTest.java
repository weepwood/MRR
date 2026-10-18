package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.service.RuntimeLogJournalService;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiagnosticFeatureSwitchTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner()
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class))
            .withBean(SystemErrorEventMapper.class,()->mock(SystemErrorEventMapper.class))
            .withUserConfiguration(RuntimeLogJournalService.class);
    @Test void collectionRequiresExplicitEnablementAndCanBeIndependentlyDisabled() {
        context.run(app->assertThat(app).doesNotHaveBean(RuntimeLogJournalService.class));
        context.withPropertyValues("app.runtime-logs.enabled=true").run(app->assertThat(app).hasSingleBean(RuntimeLogJournalService.class));
        context.withPropertyValues("app.runtime-logs.enabled=false").run(app->assertThat(app).doesNotHaveBean(RuntimeLogJournalService.class));
    }
}
