package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.ArchiveExportJobResponse;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/archive-exports/jobs")
@RequirePermissions({"record:read"})
public class ArchiveExportJobListController {

    private final ArchiveExportJobRepository repository;

    public ArchiveExportJobListController(ArchiveExportJobRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "查询当前用户仍在处理的导出任务")
    @GetMapping
    public Result<List<ArchiveExportJobResponse>> listActive(
            @RequestParam String format,
            @RequestParam(defaultValue = "5") int limit,
            HttpServletRequest request) {
        AuthSession session = session(request);
        String normalizedFormat = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
        if (!normalizedFormat.equals("ZIP") && !normalizedFormat.equals("PDF")) {
            throw new BusinessException(400, "导出格式仅支持 ZIP 或 PDF");
        }
        String permission = normalizedFormat.equals("PDF")
                ? "record:pdf:export"
                : "record:download";
        if (!session.hasPermission(permission)) {
            throw new BusinessException(403, "没有病案导出权限");
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<ArchiveExportJobResponse> jobs = repository
                .findActiveByOwner(
                        session.getId(), session.getUsername(), normalizedFormat, safeLimit)
                .stream()
                .map(ArchiveExportJobResponse::from)
                .toList();
        return Result.success(jobs);
    }

    private AuthSession session(HttpServletRequest request) {
        AuthSession session = (AuthSession) request.getAttribute(
                AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        if (session == null || session.getId() == null
                || session.getUsername() == null || session.getUsername().isBlank()) {
            throw new BusinessException(401, "请先登录");
        }
        return session;
    }
}
