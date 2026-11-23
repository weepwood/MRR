package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * access_log 按月分区迁移配置（ADR-0010）。
 *
 * <p>分区维护（预建分区、迁出默认分区、按保留期删除整月分区）始终运行；
 * 历史数据复制默认关闭，需要运维确认磁盘余量后显式开启。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.access-log-partition")
public class AccessLogPartitionProperties {

    /** 是否由后台任务把旧 access_log 的历史数据分批复制到分区表。 */
    private boolean copyEnabled = false;

    /** 单批复制条数；一次批次一个事务。 */
    private int copyBatchSize = 5000;

    /** 每轮最多批次数；轮与轮之间间隔 {@code copy-interval-ms}。 */
    private int copyMaxBatchesPerRun = 20;

    /** 预建当前月之后的分区月数。 */
    private int monthsAhead = 2;
}
