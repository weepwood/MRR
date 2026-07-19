package com.zjcxph.imgapi.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Fixed-cardinality capacity gauges used for growth forecasting in Prometheus/Grafana. */
@Service
public class CapacityMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(CapacityMetricsService.class);
    private static final String[] MONITORED_TABLES = {
            "mr_scan", "mr_statistics", "mr_patient", "access_log"
    };

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong databaseSize = new AtomicLong();
    private final Map<String, AtomicLong> tableSizes = new LinkedHashMap<>();
    private final Map<String, AtomicLong> tableRows = new LinkedHashMap<>();

    public CapacityMetricsService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("mrr.database.size.bytes", databaseSize, AtomicLong::get)
                .description("Current PostgreSQL database size")
                .register(meterRegistry);
        for (String table : MONITORED_TABLES) {
            AtomicLong size = new AtomicLong();
            AtomicLong rows = new AtomicLong();
            tableSizes.put(table, size);
            tableRows.put(table, rows);
            Gauge.builder("mrr.table.size.bytes", size, AtomicLong::get)
                    .tag("table", table)
                    .description("PostgreSQL table and index size")
                    .register(meterRegistry);
            Gauge.builder("mrr.table.estimated.rows", rows, AtomicLong::get)
                    .tag("table", table)
                    .description("PostgreSQL estimated live row count")
                    .register(meterRegistry);
        }
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.capacity.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            Long size = jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class);
            databaseSize.set(size == null ? 0 : size);
            for (String table : MONITORED_TABLES) {
                Long tableSize = jdbcTemplate.queryForObject(
                        "SELECT pg_total_relation_size(?::regclass)", Long.class, "app." + table);
                Long rows = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(c.reltuples, 0)::bigint
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'app' AND c.relname = ?
                        """, Long.class, table);
                tableSizes.get(table).set(tableSize == null ? 0 : tableSize);
                tableRows.get(table).set(rows == null ? 0 : rows);
            }
        } catch (Exception exception) {
            logger.warn("Unable to refresh database capacity metrics: {}", exception.getMessage());
        }
    }
}
