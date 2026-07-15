package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 数据交换异步入口。具体 COPY、校验和分卷逻辑分别由导入/导出处理器负责。
 */
@Service
public class DataTransferWorker {

    private static final Logger log = LoggerFactory.getLogger(DataTransferWorker.class);

    private final DataTransferRepository repository;
    private final DataTransferImportProcessor importProcessor;
    private final DataTransferExportProcessor exportProcessor;

    public DataTransferWorker(
            DataTransferRepository repository,
            DataTransferImportProcessor importProcessor,
            DataTransferExportProcessor exportProcessor
    ) {
        this.repository = repository;
        this.importProcessor = importProcessor;
        this.exportProcessor = exportProcessor;
    }

    @Async("dataTransferExecutor")
    public void executeAsync(long jobId) {
        try {
            DataTransferJob job = repository.findJob(jobId);
            if (job == null) {
                return;
            }
            if ("IMPORT".equals(job.getDirection())) {
                importProcessor.process(job);
            }
            else if ("EXPORT".equals(job.getDirection())) {
                exportProcessor.process(job);
            }
            else {
                throw new IllegalStateException("Unsupported transfer direction: " + job.getDirection());
            }
        }
        catch (Exception exception) {
            log.error("Data transfer job failed: jobId={}", jobId, exception);
            repository.updateJobStatus(jobId, "FAILED", "任务失败", safeMessage(exception));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
