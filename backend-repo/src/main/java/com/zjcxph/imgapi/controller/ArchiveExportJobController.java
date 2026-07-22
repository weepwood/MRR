package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportJobResponse;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.service.ArchiveExportJobService;
import com.zjcxph.imgapi.utils.HttpByteRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/archive-exports/jobs")
@Tag(name = "Archive Export Jobs", description = "超大病案 ZIP/PDF 异步导出任务")
@RequirePermissions({"record:read"})
public class ArchiveExportJobController {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String PRIVATE_NO_STORE = "private, no-store, max-age=0";

    private final ArchiveExportJobService jobService;

    public ArchiveExportJobController(ArchiveExportJobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "创建异步导出任务")
    @PostMapping
    @AuthenticatedOnly
    public Result<ArchiveExportJobResponse> create(
            @RequestBody ArchiveExportJobRequest request,
            HttpServletRequest servletRequest) {
        AuthSession session = session(servletRequest);
        String format = request == null || request.getFormat() == null
                ? ""
                : request.getFormat().trim().toUpperCase(Locale.ROOT);
        if ("ZIP".equals(format)) {
            requirePermission(session, "record:download");
        } else if ("PDF".equals(format)) {
            requirePermission(session, "record:pdf:export");
        }
        return Result.success(jobService.create(session, request));
    }

    @Operation(summary = "查询异步导出任务")
    @GetMapping("/{id}")
    public Result<ArchiveExportJobResponse> get(
            @PathVariable String id,
            HttpServletRequest request) {
        AuthSession session = session(request);
        ArchiveExportJob job = jobService.requireOwned(session, id);
        requireJobPermission(session, job);
        return Result.success(ArchiveExportJobResponse.from(job));
    }

    @Operation(summary = "取消异步导出任务")
    @PostMapping("/{id}/cancel")
    @AuthenticatedOnly
    public Result<ArchiveExportJobResponse> cancel(
            @PathVariable String id,
            HttpServletRequest request) {
        AuthSession session = session(request);
        ArchiveExportJob job = jobService.requireOwned(session, id);
        requireJobPermission(session, job);
        return Result.success(jobService.cancel(session, id));
    }

    @Operation(summary = "下载异步导出文件，支持单区间 Range")
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) throws Exception {
        AuthSession session = session(request);
        ArchiveExportJob job = jobService.requireOwned(session, id);
        requireJobPermission(session, job);
        Path file = jobService.requireDownloadFile(session, id);
        long total = Files.size(file);
        MediaType contentType = "PDF".equals(job.getFormat())
                ? MediaType.APPLICATION_PDF
                : MediaType.APPLICATION_OCTET_STREAM;
        String disposition = ContentDisposition.attachment()
                .filename(job.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        String etag = job.getSha256() == null || job.getSha256().isBlank()
                ? null
                : "\"" + job.getSha256() + "\"";

        if (rangeHeader == null || rangeHeader.isBlank()) {
            StreamingResponseBody body = output -> {
                try (InputStream input = Files.newInputStream(file)) {
                    input.transferTo(output);
                }
            };
            ResponseEntity.BodyBuilder builder = baseHeaders(
                    ResponseEntity.ok(), disposition, job, etag);
            return builder.contentType(contentType)
                    .contentLength(total)
                    .body(body);
        }

        final HttpByteRange range;
        try {
            range = HttpByteRange.parse(rangeHeader, total);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .<StreamingResponseBody>build();
        }

        StreamingResponseBody body = output -> {
            try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
                input.seek(range.start());
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = range.length();
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };
        ResponseEntity.BodyBuilder builder = baseHeaders(
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT), disposition, job, etag)
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + range.start() + "-" + range.end() + "/" + total);
        return builder.contentType(contentType)
                .contentLength(range.length())
                .body(body);
    }

    private ResponseEntity.BodyBuilder baseHeaders(
            ResponseEntity.BodyBuilder builder,
            String disposition,
            ArchiveExportJob job,
            String etag) {
        builder.header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Archive-SHA256", job.getSha256() == null ? "" : job.getSha256());
        if (etag != null) {
            builder.header(HttpHeaders.ETAG, etag);
        }
        return builder;
    }

    private AuthSession session(HttpServletRequest request) {
        AuthSession session = (AuthSession) request.getAttribute(
                AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        if (session == null) {
            throw new BusinessException(401, "请先登录");
        }
        return session;
    }

    private void requireJobPermission(AuthSession session, ArchiveExportJob job) {
        requirePermission(session, "PDF".equals(job.getFormat())
                ? "record:pdf:export"
                : "record:download");
    }

    private void requirePermission(AuthSession session, String permission) {
        if (!session.hasPermission(permission)) {
            throw new BusinessException(403, "没有病案导出权限");
        }
    }
}
