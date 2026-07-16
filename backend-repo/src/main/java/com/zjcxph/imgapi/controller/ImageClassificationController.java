package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.entity.ClassificationJob;
import com.zjcxph.imgapi.entity.ImageClassification;
import com.zjcxph.imgapi.entity.RecordTypeDefinition;
import com.zjcxph.imgapi.service.ArchiveImageClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/image-classification")
@Tag(name = "Image Classification", description = "病案影像 OCR 分类建议、任务与人工审核")
@RequirePermissions({"record:read"})
public class ImageClassificationController {

    private final ArchiveImageClassificationService classificationService;

    public ImageClassificationController(ArchiveImageClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @GetMapping("/types")
    @Operation(summary = "获取启用的影像类型及 OCR 规则")
    public Result<List<RecordTypeDefinition>> findTypes() {
        return Result.success(classificationService.findEnabledTypes());
    }

    @PostMapping("/archives/{archiveId}/jobs")
    @Operation(summary = "为一份病案创建异步 OCR 分类任务")
    public Result<ClassificationJob> startJob(
            @PathVariable Long archiveId,
            @RequestBody(required = false) CreateJobRequest request
    ) {
        String scope = request == null ? null : request.scope();
        String createdBy = request == null ? null : request.createdBy();
        return Result.success("识别任务已创建", classificationService.startJob(archiveId, scope, createdBy));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询 OCR 分类任务进度")
    public Result<ClassificationJob> findJob(@PathVariable Long jobId) {
        ClassificationJob job = classificationService.findJob(jobId);
        return job == null ? Result.notFound("识别任务不存在") : Result.success(job);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "取消 OCR 分类任务")
    public Result<Void> cancelJob(@PathVariable Long jobId) {
        return classificationService.cancelJob(jobId)
                ? Result.success("识别任务已取消")
                : Result.fail("任务不存在或已经结束");
    }

    @GetMapping("/archives/{archiveId}/results")
    @Operation(summary = "查询一份病案的图片分类建议")
    public Result<List<ImageClassification>> findResults(@PathVariable Long archiveId) {
        return Result.success(classificationService.findResults(archiveId));
    }

    @PutMapping("/scans/{scanId}/confirm")
    @Operation(summary = "确认或修改单张图片的智能分类建议")
    public Result<ImageClassification> confirm(
            @PathVariable Integer scanId,
            @RequestBody(required = false) ConfirmRequest request
    ) {
        Integer btype = request == null ? null : request.btype();
        String reviewedBy = request == null ? null : request.reviewedBy();
        return Result.success(
                "分类建议已确认",
                classificationService.confirmSuggestion(scanId, btype, reviewedBy)
        );
    }

    @PostMapping("/archives/{archiveId}/confirm-high-confidence")
    @Operation(summary = "批量采用一份病案中的高置信度分类建议")
    public Result<Map<String, Integer>> confirmHighConfidence(
            @PathVariable Long archiveId,
            @RequestBody(required = false) BatchConfirmRequest request
    ) {
        Double threshold = request == null ? null : request.minConfidence();
        String reviewedBy = request == null ? null : request.reviewedBy();
        int confirmed = classificationService.confirmHighConfidence(archiveId, threshold, reviewedBy);
        return Result.success("高置信度建议已确认", Map.of("confirmedCount", confirmed));
    }

    public record CreateJobRequest(String scope, String createdBy) {
    }

    public record ConfirmRequest(Integer btype, String reviewedBy) {
    }

    public record BatchConfirmRequest(Double minConfidence, String reviewedBy) {
    }
}
