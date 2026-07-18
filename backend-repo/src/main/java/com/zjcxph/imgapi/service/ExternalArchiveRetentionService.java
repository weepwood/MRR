package com.zjcxph.imgapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalArchiveRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalArchiveRetentionService.class);
    private final JdbcTemplate jdbcTemplate;

    public ExternalArchiveRetentionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 临时凭证保留 7 天用于问题排查，审计日志不在此任务中删除。
     */
    @Scheduled(cron = "0 20 3 * * ?")
    @Transactional
    public void cleanExpiredCredentials() {
        int nonces = jdbcTemplate.update("DELETE FROM mr_external_archive_nonce WHERE expires_at < NOW()");
        int tickets = jdbcTemplate.update("""
                DELETE FROM mr_external_archive_ticket
                WHERE expires_at < NOW() - INTERVAL '7 days'
                """);
        int sessions = jdbcTemplate.update("""
                DELETE FROM mr_external_archive_session
                WHERE expires_at < NOW() - INTERVAL '7 days'
                """);
        if (nonces + tickets + sessions > 0) {
            logger.info(
                    "External archive credential cleanup completed: nonces={}, tickets={}, sessions={}",
                    nonces, tickets, sessions
            );
        }
    }
}
