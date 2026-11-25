package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveManifestService;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/archive-manifests")
@RequirePermissions({"system:read", "record:read"})
public class ArchiveManifestController {
    private final ArchiveManifestService service;

    public ArchiveManifestController(ArchiveManifestService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String,Object>> latest(@RequestParam long archiveId, HttpServletResponse response) {
        noStore(response);
        if (archiveId <= 0) throw new BusinessException(400, "无效病案");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("enabled", service.enabled());
        result.put("latest", service.latest(archiveId));
        return Result.success(result);
    }

    @PostMapping
    @RequirePermissions({"system:manage", "record:read"})
    public Result<ArchiveManifestService.Observation> start(@RequestParam long archiveId, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.start(archiveId, actor()));
    }

    @GetMapping("/history")
    public Result<List<ArchiveManifestService.Observation>> history(@RequestParam long archiveId,
            @RequestParam(required=false) Long before, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.history(archiveId,before));
    }

    @GetMapping("/compare")
    public Result<com.zjcxph.imgapi.repository.ArchiveManifestRepository.Comparison> compare(
            @RequestParam long archiveId, @RequestParam long baseVersion, @RequestParam long targetVersion,
            HttpServletResponse response) {
        noStore(response);
        return Result.success(service.compare(archiveId,baseVersion,targetVersion));
    }

    @GetMapping("/{version}")
    public Result<ArchiveManifestService.Observation> get(@PathVariable long version, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.get(version));
    }

    @GetMapping("/{version}/anomalies")
    public Result<List<ArchiveManifestService.Issue>> issues(@PathVariable long version,
            @RequestParam(defaultValue = "0") int after, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.issues(version, after));
    }

    @PostMapping("/{version}/cancel")
    @RequirePermissions({"system:manage", "record:read"})
    public Result<ArchiveManifestService.Observation> cancel(@PathVariable long version, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.cancel(version, actor()));
    }

    private static void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "private, no-store"); }

    private static long actor() {
        var session = AuthContext.getCurrentUser();
        if (session == null || session.getId() == null) throw new BusinessException(401, "未登录");
        return session.getId();
    }
}
