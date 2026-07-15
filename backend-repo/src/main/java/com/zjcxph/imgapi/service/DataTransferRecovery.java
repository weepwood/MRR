package com.zjcxph.imgapi.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务进程退出时，数据库中的异步状态不会自动结束。
 * 启动后把遗留执行状态标记为失败，使管理员可以从未完成文件重试。
 */
@Component
public class DataTransferRecovery {

    private final JdbcTemplate jdbcTemplate;

    public DataTransferRecovery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedJobs() {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_file
                SET status = 'FAILED',
                    error_message = COALESCE(error_message, '服务重启导致文件处理被中断'),
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status IN ('VALIDATING', 'IMPORTING', 'EXPORTING')
                """
        );
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job
                SET status = 'FAILED',
                    current_stage = '服务重启后等待重试',
                    error_message = COALESCE(error_message, '服务重启导致任务被中断，请重试失败文件'),
                    completed_at = CURRENT_TIMESTAMP,
                    heartbeat_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status IN ('VALIDATING', 'IMPORTING', 'EXPORTING', 'MERGING')
                """
        );
    }
}
