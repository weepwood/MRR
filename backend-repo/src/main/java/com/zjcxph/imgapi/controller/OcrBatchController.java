package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.OcrBatchService;
import com.zjcxph.imgapi.service.OcrBatchService.*;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/ocr/batches")
@RequirePermissions({"record:read","ocr:run","ocr:batch-run"})
public class OcrBatchController {
    private final OcrBatchService service;
    public OcrBatchController(OcrBatchService service) {this.service=service;}
    @ModelAttribute public void privateResponse(HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @PostMapping("/preview") public Result<Preview> preview(@RequestBody Request request) {return Result.success(service.preview(AuthContext.getCurrentUser(),request));}
    @PostMapping public Result<View> create(@RequestBody Request request) {return Result.success(service.create(AuthContext.getCurrentUser(),request));}
    @GetMapping public Result<List<View>> list(@RequestParam(defaultValue="") String after) {return Result.success(service.list(AuthContext.getCurrentUser(),after));}
    @GetMapping("/{id}") public Result<View> get(@PathVariable String id) {return Result.success(service.get(AuthContext.getCurrentUser(),id));}
    @GetMapping("/{id}/jobs") public Result<List<String>> children(@PathVariable String id,@RequestParam(defaultValue="") String after) {return Result.success(service.children(AuthContext.getCurrentUser(),id,after));}
    @PostMapping("/{id}/{action:pause|resume|cancel}") public Result<View> action(@PathVariable String id,@PathVariable String action) {return Result.success(service.action(AuthContext.getCurrentUser(),id,action));}
}
