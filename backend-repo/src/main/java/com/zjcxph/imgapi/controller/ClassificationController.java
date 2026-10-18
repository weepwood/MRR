package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.*;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/classification")
@RequirePermissions({"record:read","classification:read"})
public class ClassificationController {
    public record Review(String action,Integer type) {}
    public record TestRule(Integer scanId) {}
    private final ClassificationMapper mapper;private final OcrMapper ocr;private final OcrJobService jobs;
    private final ImageTypeService types;private final ClassificationCatalogService catalog;
    public ClassificationController(ClassificationMapper mapper,OcrMapper ocr,OcrJobService jobs,ImageTypeService types,ClassificationCatalogService catalog) {
        this.mapper=mapper;this.ocr=ocr;this.jobs=jobs;this.types=types;this.catalog=catalog;
    }
    @ModelAttribute public void privateResponse(HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @GetMapping("/jobs/{id}/suggestions")
    public Result<List<ClassificationMapper.Suggestion>> suggestions(@PathVariable String id,@RequestParam(defaultValue="0") long after) {
        jobs.owned(AuthContext.getCurrentUser(),id,"classification:read");return Result.success(mapper.suggestions(id,Math.max(0,after)));
    }
    @PostMapping("/suggestions/{id}/review")
    @RequirePermissions({"record:read","classification:review"})
    public Result<Void> review(@PathVariable Long id,@RequestBody Review body) {types.review(AuthContext.getCurrentUser(),id,body.action(),body.type());return Result.success();}
    @GetMapping("/rule-sets") @RequirePermissions({"ocr:config"})
    public Result<List<ClassificationCatalogMapper.Version>> versions() {return Result.success(catalog.versions(AuthContext.getCurrentUser()));}
    @GetMapping("/rule-sets/{version}") @RequirePermissions({"ocr:config"})
    public Result<ClassificationCatalogService.Bundle> bundle(@PathVariable String version) {return Result.success(catalog.get(AuthContext.getCurrentUser(),version));}
    @PostMapping("/rule-sets") @RequirePermissions({"ocr:config"})
    public Result<String> create(@RequestBody ClassificationCatalogService.Bundle body) {return Result.successWithData(catalog.create(AuthContext.getCurrentUser(),body));}
    @PutMapping("/rule-sets/{version}") @RequirePermissions({"ocr:config"})
    public Result<Void> save(@PathVariable String version,@RequestBody ClassificationCatalogService.Bundle body) {catalog.update(AuthContext.getCurrentUser(),version,body);return Result.success();}
    @PostMapping("/rule-sets/{version}/publish") @RequirePermissions({"ocr:config"})
    public Result<Void> publish(@PathVariable String version) {catalog.publish(AuthContext.getCurrentUser(),version);return Result.success();}
    @PostMapping("/rule-sets/{version}/restore") @RequirePermissions({"ocr:config"})
    public Result<String> restore(@PathVariable String version) {return Result.successWithData(catalog.create(AuthContext.getCurrentUser(),catalog.get(AuthContext.getCurrentUser(),version)));}
    @PostMapping("/rule-sets/{version}/test") @RequirePermissions({"ocr:config","ocr:text-read","record:read"})
    public Result<com.zjcxph.imgapi.ocr.ClassificationEngine.Outcome> test(@PathVariable String version,@RequestBody TestRule body) {
        return Result.success(catalog.test(AuthContext.getCurrentUser(),version,body.scanId(),ocr));
    }
}
