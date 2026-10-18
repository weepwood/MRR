package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateLogbackConfigurationTest {
    @TempDir Path directory;
    @Test void realConfigurationCapturesRootApplicationAndNonAdditiveMapperAtEveryEmittedLevel() throws Exception {
        Path file = directory.resolve("levels.log");
        var environment = new MockEnvironment().withProperty("logging.file.name", file.toString());
        environment.setActiveProfiles("test");
        var logging = new LogbackLoggingSystem(getClass().getClassLoader());
        try {
            logging.beforeInitialize();
            logging.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
            LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
            for (String name : List.of("synthetic.thirdparty", "com.zjcxph.imgapi.synthetic", "com.zjcxph.imgapi.mapper.synthetic")) {
                var logger = context.getLogger(name); logger.setLevel(Level.TRACE);
                logger.trace("SYNTHETIC-PRIVATE"); logger.debug("SYNTHETIC-PRIVATE"); logger.info("SYNTHETIC-PRIVATE");
                logger.warn("SYNTHETIC-PRIVATE"); logger.error("SYNTHETIC-PRIVATE", new IllegalStateException("SYNTHETIC-PRIVATE"));
            }
            // stop drains AsyncAppender; no timing-based sleep assertion.
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_FILE").stop();
            String text = Files.readString(file);
            assertThat(text).doesNotContain("SYNTHETIC-PRIVATE");
            var json = new ObjectMapper();
            Map<String,Set<String>> levels = new HashMap<>();
            for (String line : text.lines().toList()) {
                var row = json.readTree(line);
                levels.computeIfAbsent(row.path("logger_name").asText(), ignored -> new HashSet<>()).add(row.path("level").asText());
            }
            for (String name : List.of("synthetic.thirdparty", "com.zjcxph.imgapi.synthetic", "com.zjcxph.imgapi.mapper.synthetic"))
                assertThat(levels.get(name)).containsExactlyInAnyOrder("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        } finally { logging.cleanUp(); }
    }
}
