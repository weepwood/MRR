package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import com.zjcxph.imgapi.service.DatabaseBrowseService;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 固化功能总开关的默认姿态：未显式配置时必须开启，显式配置 false 时仍可即时关闭。
 *
 * <p>只验证构造器上的 {@code @Value} 默认值，不启动 Web 容器或数据库。</p>
 */
@DisplayName("受控只读数据库浏览默认开关")
class DatabaseBrowseDefaultSwitchTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(DbBrowseRegistry.class, () -> mock(DbBrowseRegistry.class))
            .withBean(DatabaseBrowseRepository.class, () -> mock(DatabaseBrowseRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(DatabaseBrowseService.class);

    @Test
    @DisplayName("未显式配置时默认开启")
    void enabledByDefault() {
        context.run(app -> assertThat(app.getBean(DatabaseBrowseService.class).isEnabled()).isTrue());
    }

    @Test
    @DisplayName("显式配置 false 时可即时关闭")
    void canBeDisabledExplicitly() {
        context.withPropertyValues("app.operations.db-browse-enabled=false")
                .run(app -> assertThat(app.getBean(DatabaseBrowseService.class).isEnabled()).isFalse());
    }
}
