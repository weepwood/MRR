package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import com.zjcxph.imgapi.service.OcrJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequirePermissions({"record:read","ocr:read"})
public class OcrController {
    private final OcrJobService service;
    public OcrController(OcrJobService service) { this.service=service; }
    @PostMapping("/api/v1/archive-records/{id}/ocr/jobs")
    @RequirePermissions({"record:read","ocr:run"})
    public Result<JobView> create(@PathVariable Long id,@RequestBody Create body,HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(service.create(session(request),id,body));
    }
    @GetMapping("/api/v1/archive-records/{id}/ocr/jobs")
    public Result<List<JobView>> list(@PathVariable Long id,HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(service.jobs(session(request),id));
    }
    @GetMapping("/api/v1/ocr/jobs/{id}")
    public Result<JobView> get(@PathVariable String id,HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(JobView.from(service.owned(session(request),id,"ocr:read")));
    }
    @GetMapping("/api/v1/ocr/jobs/{id}/items")
    public Result<List<Item>> items(@PathVariable String id,@RequestParam(defaultValue="0") int after,
                                   @RequestParam(defaultValue="50") int limit,HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(service.items(session(request),id,after,limit));
    }
    @PostMapping("/api/v1/ocr/jobs/{id}/cancel")
    @RequirePermissions({"record:read","ocr:run"})
    public Result<JobView> cancel(@PathVariable String id,HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(service.cancel(session(request),id));
    }
    @GetMapping("/api/v1/scan/{id}/ocr")
    public Result<FactView> fact(@PathVariable Integer id,@RequestParam(defaultValue="false") boolean full,
                               HttpServletRequest request,HttpServletResponse response) {
        privateResponse(response); return Result.success(service.fact(session(request),id,full));
    }
    private AuthSession session(HttpServletRequest request) { return (AuthSession)request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE); }
    private void privateResponse(HttpServletResponse response) { response.setHeader("Cache-Control","private, no-store"); response.setHeader("Pragma","no-cache"); }
}
