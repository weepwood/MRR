package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.QualityCaseReviewService;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/data-quality/case-reviews")
@RequirePermissions({"system:manage","record:manage"})
public class QualityCaseReviewController {
    private final QualityCaseReviewService service;
    public QualityCaseReviewController(QualityCaseReviewService service) { this.service=service; }
    @GetMapping
    public Result<QualityCaseReviewService.Page> list(@RequestParam(required=false) Long before,HttpServletResponse response) {
        noStore(response);return Result.success(service.list(before));
    }
    @GetMapping("/issue/{issueId}")
    public Result<QualityCaseReviewService.Context> context(@PathVariable long issueId,HttpServletResponse response) {
        noStore(response);return Result.success(service.context(issueId));
    }
    @GetMapping("/{id}/events")
    public Result<java.util.List<QualityCaseReviewService.Event>> events(@PathVariable long id,@RequestParam(defaultValue="0") int before,HttpServletResponse response) {
        noStore(response);return Result.success(service.events(id,before));
    }
    @PostMapping("/issue/{issueId}")
    @RequirePermissions({"system:manage","record:manage"})
    public Result<QualityCaseReviewService.Review> decide(@PathVariable long issueId,@RequestParam int expectedRevision,
            @RequestParam String action,HttpServletResponse response) {
        noStore(response);
        var session=AuthContext.getCurrentUser();
        if(session==null || session.getId()==null) throw new BusinessException(401,"未登录");
        return Result.success(service.decide(issueId,expectedRevision,action,session.getId()));
    }
    private static void noStore(HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
}
