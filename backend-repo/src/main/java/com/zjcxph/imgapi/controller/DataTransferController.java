package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.DataTransferExportRequest;
import com.zjcxph.imgapi.dto.req.DataTransferInboxRequest;
import com.zjcxph.imgapi.dto.resp.DataTransferJobDetailDTO;
import com.zjcxph.imgapi.entity.DataTransferFile;
import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import com.zjcxph.imgapi.service.DataTransferService;
import com.zjcxph.imgapi.service.DataTransferStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/data-transfer")
@Tag(name = "Data Transfer", description = "CSV 数据导入导出任务")
@RequirePermissions({"record:manage"})
public class DataTransferController {

    private static final String STATISTICS_TEMPLATE =
            "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate\r\n";
    private static final String SCAN_TEMPLATE =
            "brxh,bah,sjh,filename,btype,pages,openerno,uploaddate,uploadflag,folder,file_size\r\n";

    private final DataTransferService dataTransferService;
    private final DataTransferRepository repository;
    private final DataTransferStorageService storageService;

    public DataTransferController(
            DataTransferService dataTransferService,
            DataTransferRepository repository,
            DataTransferStorageService storageService
    ) {
        this.dataTransferService = dataTransferService;
        this.repository = repository;
        this.storageService = storageService;
    }

    @GetMapping("/jobs")
    @Operation(summary = "查询数据交换任务")
    public Result<List<DataTransferJob>> findJobs(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(dataTransferService.findJobs(limit));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询任务详情、文件和错误样本")
    public Result<DataTransferJobDetailDTO> findJob(@PathVariable long jobId) {
        return Result.success(dataTransferService.findDetail(jobId));
    }

    @PostMapping(value = "/imports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传一个或多个 CSV 并创建导入任务")
    public Result<DataTransferJob> createUploadImport(
            @RequestPart("entityType") String entityType,
            @RequestPart(value = "importMode", required = false) String importMode,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return Result.success(dataTransferService.createUploadImportJob(entityType, importMode, files));
    }

    @GetMapping("/inbox")
    @Operation(summary = "列出服务器受控 inbox 中的 CSV 文件")
    public Result<List<String>> listInboxFiles() {
        return Result.success(dataTransferService.listInboxFiles());
    }

    @PostMapping("/imports/inbox")
    @Operation(summary = "使用服务器 inbox 文件创建导入任务")
    public Result<DataTransferJob> createInboxImport(@Valid @RequestBody DataTransferInboxRequest request) {
        return Result.success(dataTransferService.createInboxImportJob(request));
    }

    @PostMapping("/exports")
    @Operation(summary = "创建异步 CSV 分卷导出任务")
    public Result<DataTransferJob> createExport(@Valid @RequestBody DataTransferExportRequest request) {
        return Result.success(dataTransferService.createExportJob(request));
    }

    @PostMapping("/jobs/{jobId}/execute")
    @Operation(summary = "开始执行已上传任务")
    public Result<Void> execute(@PathVariable long jobId) {
        dataTransferService.execute(jobId);
        return Result.success();
    }

    @PostMapping("/jobs/{jobId}/pause")
    @Operation(summary = "在当前文件完成后暂停任务")
    public Result<Void> pause(@PathVariable long jobId) {
        dataTransferService.pause(jobId);
        return Result.success();
    }

    @PostMapping("/jobs/{jobId}/resume")
    @Operation(summary = "继续已暂停任务")
    public Result<Void> resume(@PathVariable long jobId) {
        dataTransferService.resume(jobId);
        return Result.success();
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "取消任务")
    public Result<Void> cancel(@PathVariable long jobId) {
        dataTransferService.cancel(jobId);
        return Result.success();
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "重试任务中的失败文件")
    public Result<Void> retry(@PathVariable long jobId) {
        dataTransferService.retry(jobId);
        return Result.success();
    }

    @GetMapping("/templates/{entityType}.csv")
    @Operation(summary = "下载标准 CSV 模板")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String entityType) {
        String normalized = entityType.toUpperCase(Locale.ROOT);
        String content;
        String filename;
        if ("MR_STATISTICS".equals(normalized)) {
            content = STATISTICS_TEMPLATE;
            filename = "mr-statistics-template.csv";
        }
        else if ("MR_SCAN".equals(normalized)) {
            content = SCAN_TEMPLATE;
            filename = "mr-scan-template.csv";
        }
        else {
            throw new BusinessException(400, "不支持的数据类型");
        }
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(filename))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/files/{fileId}/download")
    @Operation(summary = "下载导出分卷或任务文件")
    public ResponseEntity<Resource> downloadFile(@PathVariable long fileId) {
        DataTransferFile file = requireFile(fileId);
        Path path = storageService.resolveStoredPath(file.getStoredPath());
        String filename = file.getDownloadName() == null || file.getDownloadName().isBlank()
                ? file.getOriginalFilename()
                : file.getDownloadName();
        return resourceResponse(path, filename, MediaType.APPLICATION_OCTET_STREAM);
    }

    @GetMapping("/jobs/{jobId}/files/{fileId}/errors.csv.gz")
    @Operation(summary = "下载完整错误行报告")
    public ResponseEntity<Resource> downloadErrorReport(
            @PathVariable long jobId,
            @PathVariable long fileId
    ) {
        DataTransferFile file = requireFile(fileId);
        if (!Long.valueOf(jobId).equals(file.getJobId())) {
            throw new BusinessException(404, "任务文件不存在");
        }
        Path path = storageService.createErrorReportPath(jobId, fileId);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "该文件没有错误报告");
        }
        return resourceResponse(path, "errors-file-" + fileId + ".csv.gz", MediaType.APPLICATION_OCTET_STREAM);
    }

    private DataTransferFile requireFile(long fileId) {
        DataTransferFile file = repository.findFile(fileId);
        if (file == null) {
            throw new BusinessException(404, "任务文件不存在");
        }
        return file;
    }

    private ResponseEntity<Resource> resourceResponse(Path path, String filename, MediaType mediaType) {
        try {
            FileSystemResource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Files.size(path))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition(filename))
                    .body(resource);
        }
        catch (Exception exception) {
            throw new BusinessException(500, "读取下载文件失败");
        }
    }

    private String disposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
