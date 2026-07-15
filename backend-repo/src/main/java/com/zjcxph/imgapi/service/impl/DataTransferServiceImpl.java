package com.zjcxph.imgapi.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.dto.req.DataTransferExportRequest;
import com.zjcxph.imgapi.dto.req.DataTransferInboxRequest;
import com.zjcxph.imgapi.dto.resp.DataTransferJobDetailDTO;
import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import com.zjcxph.imgapi.service.DataTransferService;
import com.zjcxph.imgapi.service.DataTransferStorageService;
import com.zjcxph.imgapi.service.DataTransferWorker;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DataTransferServiceImpl implements DataTransferService {

    private static final List<String> ENTITY_TYPES = List.of("MR_STATISTICS", "MR_SCAN");
    private static final List<String> IMPORT_MODES = List.of("SKIP_DUPLICATES", "UPSERT");

    private final DataTransferRepository repository;
    private final DataTransferStorageService storageService;
    private final DataTransferWorker worker;
    private final DataTransferProperties properties;
    private final ObjectMapper objectMapper;

    public DataTransferServiceImpl(
            DataTransferRepository repository,
            DataTransferStorageService storageService,
            DataTransferWorker worker,
            DataTransferProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.worker = worker;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public DataTransferJob createUploadImportJob(
            String entityType,
            String importMode,
            List<MultipartFile> files
    ) {
        String normalizedEntity = normalizeEntityType(entityType);
        String normalizedMode = normalizeImportMode(importMode);
        validateFileCount(files == null ? 0 : files.size());

        long jobId = repository.createJob(
                "IMPORT",
                normalizedEntity,
                normalizedMode,
                "UPLOAD",
                "{}",
                "web"
        );

        try {
            int sequence = 1;
            for (MultipartFile multipartFile : files) {
                DataTransferStorageService.StoredFile stored = storageService.storeUpload(jobId, sequence, multipartFile);
                repository.createFile(
                        jobId,
                        sequence,
                        stored.originalFilename(),
                        stored.path().toString(),
                        null,
                        stored.size(),
                        stored.sha256(),
                        "UPLOADED"
                );
                sequence++;
            }
            repository.setJobFileCount(jobId, files.size());
            return requireJob(jobId);
        }
        catch (RuntimeException exception) {
            repository.updateJobStatus(jobId, "FAILED", "保存上传文件", exception.getMessage());
            throw exception;
        }
    }

    @Override
    public DataTransferJob createInboxImportJob(DataTransferInboxRequest request) {
        String normalizedEntity = normalizeEntityType(request.getEntityType());
        String normalizedMode = normalizeImportMode(request.getImportMode());
        List<String> filenames = request.getFilenames();
        validateFileCount(filenames == null ? 0 : filenames.size());

        long jobId = repository.createJob(
                "IMPORT",
                normalizedEntity,
                normalizedMode,
                "INBOX",
                "{}",
                "web"
        );

        try {
            int sequence = 1;
            for (String filename : filenames) {
                DataTransferStorageService.StoredFile stored = storageService.registerInboxFile(jobId, sequence, filename);
                repository.createFile(
                        jobId,
                        sequence,
                        stored.originalFilename(),
                        stored.path().toString(),
                        null,
                        stored.size(),
                        stored.sha256(),
                        "UPLOADED"
                );
                sequence++;
            }
            repository.setJobFileCount(jobId, filenames.size());
            return requireJob(jobId);
        }
        catch (RuntimeException exception) {
            repository.updateJobStatus(jobId, "FAILED", "登记服务器文件", exception.getMessage());
            throw exception;
        }
    }

    @Override
    public DataTransferJob createExportJob(DataTransferExportRequest request) {
        String entityType = normalizeEntityType(request.getEntityType());
        int rowsPerPart = request.getRowsPerPart() == null
                ? properties.getExportRowsPerPart()
                : request.getRowsPerPart();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("startId", request.getStartId());
        options.put("endId", request.getEndId());
        options.put("rowsPerPart", rowsPerPart);

        long jobId = repository.createJob(
                "EXPORT",
                entityType,
                "SKIP_DUPLICATES",
                null,
                toJson(options),
                "web"
        );
        execute(jobId);
        return requireJob(jobId);
    }

    @Override
    public void execute(long jobId) {
        DataTransferJob job = requireJob(jobId);
        if (List.of("IMPORTING", "EXPORTING", "VALIDATING", "MERGING").contains(job.getStatus())) {
            throw new BusinessException(409, "任务正在执行中");
        }
        if (List.of("COMPLETED", "COMPLETED_WITH_ERRORS", "CANCELLED").contains(job.getStatus())) {
            throw new BusinessException(409, "当前任务状态不允许再次执行");
        }
        if ("IMPORT".equals(job.getDirection()) && repository.findFiles(jobId).isEmpty()) {
            throw new BusinessException(400, "导入任务没有文件");
        }

        String runningStatus = "IMPORT".equals(job.getDirection()) ? "IMPORTING" : "EXPORTING";
        int claimed = repository.jdbcTemplate().update(
                """
                UPDATE app.data_transfer_job
                SET status = ?, current_stage = '准备执行',
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    heartbeat_at = CURRENT_TIMESTAMP, completed_at = NULL,
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status NOT IN ('IMPORTING', 'EXPORTING', 'VALIDATING', 'MERGING',
                                     'COMPLETED', 'COMPLETED_WITH_ERRORS', 'CANCELLED')
                """,
                runningStatus,
                jobId
        );
        if (claimed == 0) {
            throw new BusinessException(409, "任务状态已发生变化，请刷新后重试");
        }

        try {
            worker.executeAsync(jobId);
        }
        catch (TaskRejectedException exception) {
            repository.updateJobStatus(jobId, "FAILED", "任务排队失败", "数据交换队列已满，请稍后重试");
            throw new BusinessException(503, "数据交换队列已满，请稍后重试");
        }
    }

    @Override
    public void pause(long jobId) {
        int changed = repository.jdbcTemplate().update(
                """
                UPDATE app.data_transfer_job
                SET status = 'PAUSED', current_stage = '等待当前文件完成后暂停',
                    heartbeat_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('IMPORTING', 'EXPORTING', 'VALIDATING', 'MERGING')
                """,
                jobId
        );
        if (changed == 0) {
            throw new BusinessException(409, "只有执行中的任务可以暂停");
        }
    }

    @Override
    public void resume(long jobId) {
        DataTransferJob job = requireJob(jobId);
        if (!"PAUSED".equals(job.getStatus())) {
            throw new BusinessException(409, "只有已暂停任务可以继续");
        }
        repository.jdbcTemplate().update(
                "UPDATE app.data_transfer_job SET status = 'CREATED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                jobId
        );
        execute(jobId);
    }

    @Override
    public void cancel(long jobId) {
        int changed = repository.jdbcTemplate().update(
                """
                UPDATE app.data_transfer_job
                SET status = 'CANCELLED', current_stage = '已取消',
                    completed_at = CURRENT_TIMESTAMP, heartbeat_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status NOT IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'CANCELLED')
                """,
                jobId
        );
        if (changed == 0) {
            throw new BusinessException(409, "任务已经结束");
        }
    }

    @Override
    public void retry(long jobId) {
        DataTransferJob job = requireJob(jobId);
        if (!List.of("FAILED", "COMPLETED_WITH_ERRORS").contains(job.getStatus())) {
            throw new BusinessException(409, "当前任务没有可重试的失败文件");
        }

        Long failedFiles = repository.jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                Long.class,
                jobId
        );
        if (failedFiles == null || failedFiles == 0) {
            throw new BusinessException(409, "任务只有数据校验错误，没有可自动重试的失败文件");
        }

        if ("EXPORT".equals(job.getDirection())) {
            // 导出工作器会从最后一个已完成分卷继续；失败的临时分卷记录先删除，避免序号冲突。
            repository.jdbcTemplate().update(
                    "DELETE FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                    jobId
            );
        }
        else {
            repository.jdbcTemplate().update(
                    """
                    UPDATE app.data_transfer_file
                    SET status = 'UPLOADED', error_message = NULL, completed_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE job_id = ? AND status = 'FAILED'
                    """,
                    jobId
            );
        }

        repository.jdbcTemplate().update(
                """
                UPDATE app.data_transfer_job
                SET status = 'CREATED', error_message = NULL, completed_at = NULL,
                    current_stage = '等待重试', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                jobId
        );
        execute(jobId);
    }

    @Override
    public DataTransferJobDetailDTO findDetail(long jobId) {
        return new DataTransferJobDetailDTO(
                requireJob(jobId),
                repository.findFiles(jobId),
                repository.findErrors(jobId, 200)
        );
    }

    @Override
    public List<DataTransferJob> findJobs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findJobs(safeLimit);
    }

    @Override
    public List<String> listInboxFiles() {
        return storageService.listInboxFiles();
    }

    private DataTransferJob requireJob(long jobId) {
        DataTransferJob job = repository.findJob(jobId);
        if (job == null) {
            throw new BusinessException(404, "数据交换任务不存在");
        }
        return job;
    }

    private String normalizeEntityType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(normalized)) {
            throw new BusinessException(400, "不支持的数据类型：" + value);
        }
        return normalized;
    }

    private String normalizeImportMode(String value) {
        String normalized = value == null || value.isBlank()
                ? "SKIP_DUPLICATES"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!IMPORT_MODES.contains(normalized)) {
            throw new BusinessException(400, "不支持的导入模式：" + value);
        }
        return normalized;
    }

    private void validateFileCount(int count) {
        if (count < 1) {
            throw new BusinessException(400, "至少选择一个 CSV 文件");
        }
        if (count > properties.getMaxFilesPerJob()) {
            throw new BusinessException(400, "单个任务最多允许 " + properties.getMaxFilesPerJob() + " 个文件");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new BusinessException(500, "序列化任务参数失败");
        }
    }
}
